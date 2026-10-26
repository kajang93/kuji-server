// 최근 30일 게시글에 전날 달린 타인 댓글 수를 중복 없이 집계합니다.
package com.kuji.backend.domain.threads.service;

import com.kuji.backend.domain.threads.client.ThreadsApiClient;
import com.kuji.backend.domain.threads.dto.ThreadsMediaPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadsCommentStatsService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGES = 20;
    private final ThreadsApiClient client;
    private final ThreadsRecentPosts recentPosts;

    @Value("${threads.access-token:mock}")
    private String accessToken;

    public Optional<CommentStats> fetchDailyStats(LocalDate day) {
        if (accessToken == null || accessToken.isBlank() || "mock".equalsIgnoreCase(accessToken.trim())) return Optional.empty();
        try {
            Instant start = day.atStartOfDay(ZONE).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
            var posts = recentPosts.since(day.minusDays(29).atStartOfDay(ZONE).toInstant()).stream()
                    .filter(post -> ThreadsRecentPosts.timestamp(post.timestamp()).isBefore(end)).toList();
            Set<String> replies = new HashSet<>();
            for (var post : posts) countConversation(post.id(), start, end, replies);
            return Optional.of(new CommentStats(replies.size(), posts.size()));
        } catch (RuntimeException e) {
            log.warn("[Threads 댓글 통계] 전체 조회를 확인하지 못해 미집계로 표시합니다. 오류 유형={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void countConversation(String postId, Instant start, Instant end, Set<String> replies) {
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            ThreadsMediaPage response = client.getConversationPage(postId, cursor);
            if (response == null || response.data() == null) throw new IllegalStateException("댓글 통계 응답이 없습니다.");
            for (var reply : response.data()) {
                Instant time = ThreadsRecentPosts.timestamp(reply.timestamp());
                if (time.isBefore(start) || !time.isBefore(end)) continue;
                if (reply.ownedByMe() == null) throw new IllegalStateException("댓글 작성자의 소유 여부를 확인할 수 없습니다.");
                if (Boolean.FALSE.equals(reply.ownedByMe())) {
                    if (reply.id() == null || !reply.id().matches("[0-9]{1,64}")) throw new IllegalStateException("댓글 식별자가 없습니다.");
                    replies.add(reply.id());
                }
            }
            String next = response.nextCursor();
            if (next == null || next.isBlank()) return;
            if (response.data().isEmpty() || !cursors.add(next)) throw new IllegalStateException("댓글 통계 페이지가 반복되거나 비어 있습니다.");
            cursor = next;
        }
        throw new IllegalStateException("댓글 통계가 조회 상한을 넘어 전체 집계를 확인하지 못했습니다.");
    }

    public record CommentStats(long comments, long posts) { }
}
