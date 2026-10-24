// 최근 쓰레드 게시글의 새 댓글을 주기적으로 확인합니다.
package com.kuji.backend.domain.threads.service;

import com.kuji.backend.domain.threads.client.ThreadsApiClient;
import com.kuji.backend.domain.threads.dto.ThreadsAccountInfo;
import com.kuji.backend.domain.threads.dto.ThreadsMediaPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadsReplyMonitorService {
    private static final int MAX_PAGES = 20;
    private final ThreadsApiClient threadsApiClient;
    private final ThreadsReplyNotificationService notificationService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${threads.reply-monitor.enabled:false}")
    private boolean enabled;

    @Value("${threads.access-token:mock}")
    private String accessToken;

    @Value("${slack.webhook-url:mock}")
    private String slackWebhookUrl;

    @Async("notificationExecutor")
    @Scheduled(fixedDelayString = "${threads.reply-monitor.interval-ms:300000}", initialDelayString = "${threads.reply-monitor.interval-ms:300000}")
    public void checkReplies() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (missing(accessToken) || missing(slackWebhookUrl)) {
                log.warn("[Threads 댓글 감시] Threads 토큰과 Slack 웹훅 설정이 필요합니다.");
                return;
            }
            ThreadsAccountInfo account = threadsApiClient.getMe();
            if (account == null || account.getId() == null || !account.getId().matches("[0-9]{1,64}")) {
                throw new IllegalStateException("연결된 Threads 계정 식별자가 올바르지 않습니다.");
            }
            Instant startedAt = notificationService.getMonitoringStart(account.getId());
            Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
            String cursor = null;
            for (int page = 0; page < MAX_PAGES; page++) {
                ThreadsMediaPage posts = threadsApiClient.getRecentPostPage(since, cursor);
                if (posts.data() == null || posts.data().isEmpty()) {
                    return;
                }
                for (ThreadsMediaPage.Media post : posts.data()) {
                    checkConversation(account.getId(), post.id(), startedAt);
                }
                String next = posts.nextCursor();
                if (next == null || next.isBlank()) {
                    return;
                }
                if (Objects.equals(cursor, next)) {
                    throw new IllegalStateException("Threads 게시글 페이지 커서가 반복되었습니다.");
                }
                cursor = next;
            }
            log.warn("[Threads 댓글 감시] 최근 게시글 조회가 페이지 상한에 도달했습니다. 다음 주기에 다시 확인합니다.");
        } catch (Exception e) {
            // 외부 예외 원문에는 토큰과 웹훅 주소가 포함될 수 있어 출력하지 않습니다.
            log.warn("[Threads 댓글 감시] 조회 또는 알림 전달 실패. 권한과 연결 상태를 확인하세요. ({})", e.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    private void checkConversation(String accountId, String postId, Instant startedAt) {
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            ThreadsMediaPage replies = threadsApiClient.getConversationPage(postId, cursor);
            if (replies.data() == null || replies.data().isEmpty()) {
                return;
            }
            boolean olderReplies = false;
            for (ThreadsMediaPage.Media reply : replies.data()) {
                Instant publishedAt = parseTimestamp(reply.timestamp());
                if (publishedAt.isBefore(startedAt)) {
                    olderReplies = true;
                    continue;
                }
                if (Boolean.FALSE.equals(reply.ownedByMe()) && reply.id() != null && reply.id().matches("[0-9]{1,64}")) {
                    notificationService.notifyReply(accountId, reply);
                }
            }
            // 최신순 조회에서 시작 시각 이전 댓글까지 확인했으면 과거 페이지를 더 읽지 않습니다.
            if (olderReplies) {
                return;
            }
            String next = replies.nextCursor();
            if (next == null || next.isBlank()) {
                return;
            }
            if (Objects.equals(cursor, next)) {
                throw new IllegalStateException("Threads 댓글 페이지 커서가 반복되었습니다.");
            }
            cursor = next;
        }
        log.warn("[Threads 댓글 감시] 게시글 {}의 댓글 조회가 페이지 상한에 도달했습니다. 다음 주기에 다시 확인합니다.", postId);
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Threads 댓글 작성 시각이 없습니다.");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        }
    }

    private boolean missing(String value) {
        return value == null || value.isBlank() || "mock".equalsIgnoreCase(value);
    }
}
