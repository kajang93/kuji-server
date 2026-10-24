package com.kuji.backend.domain.threads.service;

import com.kuji.backend.domain.threads.client.ThreadsApiClient;
import com.kuji.backend.domain.threads.dto.ThreadsPostResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.kuji.backend.global.infra.ExternalApiHttpClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadsSchedulerService {

    private final ThreadsApiClient threadsApiClient;
    private final ThreadsContentService threadsContentService;

    @Value("${threads.enabled:true}")
    private boolean threadsEnabled;

    @Value("${threads.access-token:mock}")
    private String accessToken;

    @Value("${slack.webhook-url:mock}")
    private String slackWebhookUrl;

    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    /**
     * 매일 오전 9시에 Threads에 글을 게시하고 첫 댓글도 자동으로 작성합니다.
     * Cron: 초(0) 분(0) 시(9) 일(*) 월(*) 요일(*)
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void scheduleDailyThreadsPost() {
        if (!threadsEnabled) {
            log.info("[Threads 스케줄러] threads.enabled=false 설정으로 포스팅을 건너뜁니다.");
            return;
        }
        if ("mock".equalsIgnoreCase(accessToken) || accessToken.isBlank()) {
            log.error("[Threads 스케줄러] 토큰이 없습니다. GitHub의 THREADS_ACCESS_TOKEN 또는 APPLICATION_SECRET_YML 내 threads.access-token을 설정하고 서버를 재배포하세요.");
            return;
        }

        log.info("[Threads 스케줄러] ⏰ 오전 9시 정기 Threads 포스팅 & 댓글 작성을 시작합니다.");
        try {
            ThreadsPostResult result = executePostAndReply(null, null);
            log.info("[Threads 스케줄러] 정기 포스팅 완료: postId={}, replyId={}", result.getPostId(), result.getReplyId());
        } catch (Exception e) {
            log.error("[Threads 스케줄러] 정기 포스팅 실행 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 글 게시 및 첫 댓글 작성 실행 (수동 또는 스케줄러 공통)
     * 
     * @param customPostText  직접 지정할 본문 (null이면 AI/템플릿 자동 생성)
     * @param customReplyText 직접 지정할 댓글 (null이면 기본 댓글 템플릿 사용)
     * @return 실행 결과 DTO
     */
    public ThreadsPostResult executePostAndReply(String customPostText, String customReplyText) {
        String postText = (customPostText != null && !customPostText.isBlank())
                ? customPostText
                : threadsContentService.generateDailyPostContent();

        String replyText = (customReplyText != null && !customReplyText.isBlank())
                ? customReplyText
                : threadsContentService.generateDefaultReplyContent();

        log.info("[Threads 실행] 본문 포스팅 준비:\n{}", postText);
        log.info("[Threads 실행] 첫 댓글 준비:\n{}", replyText);

        try {
            // 1. 본문 컨테이너 생성
            String postContainerId = threadsApiClient.createTextPostContainer(postText);
            safeSleep(2000); // 메타 서버 처리 대기

            // 2. 본문 게시
            String publishedPostId = threadsApiClient.publishContainer(postContainerId);
            safeSleep(2000); // 본문 등록 완료 대기

            // 3. 댓글 컨테이너 생성 (reply_to_id 설정)
            String replyContainerId = threadsApiClient.createReplyContainer(publishedPostId, replyText);
            safeSleep(2000); // 댓글 처리 대기

            // 4. 댓글 게시
            String publishedReplyId = threadsApiClient.publishContainer(replyContainerId);

            ThreadsPostResult result = ThreadsPostResult.builder()
                    .success(true)
                    .postId(publishedPostId)
                    .replyId(publishedReplyId)
                    .postContent(postText)
                    .replyContent(replyText)
                    .publishedAt(LocalDateTime.now())
                    .build();

            // 5. 성공 슬랙 알림 발송
            notifySlackSuccess(result);

            return result;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("[Threads 실행] 포스팅 또는 댓글 작성 실패: {}", errorMsg);

            ThreadsPostResult failResult = ThreadsPostResult.builder()
                    .success(false)
                    .postContent(postText)
                    .replyContent(replyText)
                    .errorMessage(errorMsg)
                    .publishedAt(LocalDateTime.now())
                    .build();

            // 실패 슬랙 알림 발송
            notifySlackFailure(failResult);

            throw new RuntimeException("Threads 포스팅 실패: " + errorMsg, e);
        }
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void notifySlackSuccess(ThreadsPostResult result) {
        if ("mock".equalsIgnoreCase(slackWebhookUrl) || slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String message = String.format("""
                    🧵 *[Threads 일간 포스팅 & 댓글 발행 완료]*
                    • 포스트 ID: `%s`
                    • 댓글 ID: `%s`
                    • 발행 일시: %s

                    📝 *본문 내용:*
                    >>> %s

                    💬 *첫 댓글 내용:*
                    >>> %s
                    """,
                    result.getPostId(),
                    result.getReplyId(),
                    result.getPublishedAt(),
                    result.getPostContent(),
                    result.getReplyContent());

            Map<String, Object> body = new HashMap<>();
            body.put("text", message);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(slackWebhookUrl, request, String.class);
        } catch (Exception e) {
            log.warn("[Threads 슬랙 알림] 발송 실패 ({})", e.getClass().getSimpleName());
        }
    }

    private void notifySlackFailure(ThreadsPostResult result) {
        if ("mock".equalsIgnoreCase(slackWebhookUrl) || slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String message = String.format("""
                    🚨 *[Threads 포스팅 실패 경고]*
                    • 오류 메시지: `%s`
                    • 시도 일시: %s

                    📝 *시도한 본문:*
                    >>> %s
                    """,
                    result.getErrorMessage(),
                    result.getPublishedAt(),
                    result.getPostContent());

            Map<String, Object> body = new HashMap<>();
            body.put("text", message);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(slackWebhookUrl, request, String.class);
        } catch (Exception e) {
            log.warn("[Threads 슬랙 알림] 발송 실패 ({})", e.getClass().getSimpleName());
        }
    }
}
