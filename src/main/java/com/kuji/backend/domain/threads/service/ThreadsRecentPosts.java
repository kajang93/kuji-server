// 최근 쓰레드 본문을 끝까지 읽어 중복 검사와 댓글 통계의 집계 범위를 확인합니다.
package com.kuji.backend.domain.threads.service;

import com.kuji.backend.domain.threads.client.ThreadsApiClient;
import com.kuji.backend.domain.threads.dto.ThreadsMediaPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ThreadsRecentPosts {
    private static final int MAX_PAGES = 20;
    private final ThreadsApiClient client;

    public List<ThreadsMediaPage.Media> since(Instant since) {
        List<ThreadsMediaPage.Media> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            ThreadsMediaPage response = client.getRecentPostPage(since, cursor);
            if (response == null || response.data() == null) throw new IllegalStateException("최근 쓰레드 목록 응답이 없습니다.");
            for (ThreadsMediaPage.Media post : response.data()) {
                if (post.id() == null || !post.id().matches("[0-9]{1,64}")) throw new IllegalStateException("쓰레드 게시글 식별자가 없습니다.");
                if (!timestamp(post.timestamp()).isBefore(since) && ids.add(post.id())) result.add(post);
            }
            String next = response.nextCursor();
            if (next == null || next.isBlank()) return result;
            if (response.data().isEmpty() || !cursors.add(next)) throw new IllegalStateException("쓰레드 목록 페이지가 반복되거나 비어 있습니다.");
            cursor = next;
        }
        throw new IllegalStateException("최근 쓰레드 목록이 조회 상한을 넘어 전체 집계를 확인하지 못했습니다.");
    }

    static Instant timestamp(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("쓰레드 작성 시각이 없습니다.");
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        }
    }
}
