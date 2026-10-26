// 계정과 한국 날짜별 발행 상태를 기록하며 하루 한 본문과 첫 댓글을 게시합니다.
package com.kuji.backend.domain.threads.service;

import com.kuji.backend.domain.threads.client.ThreadsApiClient;
import com.kuji.backend.domain.threads.dto.ThreadsPostResult;
import com.kuji.backend.global.infra.ExternalApiHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadsSchedulerService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final ThreadsApiClient client;
    private final ThreadsContentService contentService;
    private final ThreadsRecentPosts recentPosts;
    private final ThreadsPublicationHistory history;

    @Value("${threads.enabled:true}")
    private boolean threadsEnabled;
    @Value("${threads.access-token:mock}")
    private String accessToken;
    @Value("${slack.webhook-url:mock}")
    private String slackWebhookUrl;
    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void scheduleDailyThreadsPost() {
        if (!threadsEnabled || !configured(accessToken)) return;
        try {
            executePostAndReply(null, null);
        } catch (ResponseStatusException e) {
            log.info("[Threads 정기 발행] 오늘의 기존 발행 기록을 확인해 추가 본문을 게시하지 않았습니다.");
        } catch (Exception e) {
            log.error("[Threads 정기 발행] 발행 또는 상태 확인 실패. 오류 유형={}", e.getClass().getSimpleName());
            notifySlack("🚨 Threads 정기 발행 확인 필요\n최근 글 조회 또는 발행 상태를 확인하지 못해 추가 게시를 중단했습니다.\n오류 유형. " + e.getClass().getSimpleName());
        }
    }

    public ThreadsPostResult executePostAndReply(String customPostText, String customReplyText) {
        if (!threadsEnabled || !configured(accessToken)) throw new IllegalStateException("Threads 발행 설정과 토큰을 확인해주세요.");
        String accountId = client.getMe().getId();
        if (accountId == null || !accountId.matches("[0-9]{1,64}")) throw new IllegalStateException("Threads 계정을 확인하지 못했습니다.");
        LocalDate day = LocalDate.now(ZONE);
        var existing = history.find(accountId, day);
        if (existing.isPresent()) {
            var publication = existing.get();
            if ("BODY_PUBLISHED".equals(publication.state())) return publishReply(publication);
            if ("BODY_READY".equals(publication.state()) && history.transition(accountId, day, "BODY_READY", "BODY_PREPARING")) {
                return publishBody(publication);
            }
            throw duplicate();
        }
        var remote = recentPosts.since(day.minusDays(30).atStartOfDay(ZONE).toInstant());
        if (remote.stream().anyMatch(post -> ThreadsRecentPosts.timestamp(post.timestamp()).atZone(ZONE).toLocalDate().equals(day))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "오늘 이미 Threads 본문이 발행되어 추가 게시를 중단했습니다.");
        }
        ThreadsContentService.Candidate candidate;
        if (customPostText != null && !customPostText.isBlank()) {
            contentService.validateCustomPost(accountId, day, customPostText, remote);
            candidate = new ThreadsContentService.Candidate("custom", customPostText.trim());
        } else {
            candidate = contentService.generate(accountId, day, remote);
        }
        String reply = customReplyText != null && !customReplyText.isBlank() ? customReplyText.trim() : contentService.generateDefaultReplyContent();
        if (reply.length() > 500) throw new IllegalArgumentException("첫 댓글은 500자 이내로 작성해주세요.");
        if (!history.claim(accountId, day, candidate.topicKey(), candidate.text(), reply)) throw duplicate();
        return publishBody(new ThreadsPublicationHistory.Publication(accountId, day, candidate.topicKey(), candidate.text(), reply, null, null, "BODY_PREPARING"));
    }

    private ThreadsPostResult publishBody(ThreadsPublicationHistory.Publication publication) {
        String account = publication.accountId();
        LocalDate day = publication.day();
        String postId;
        try {
            String container = client.createTextPostContainer(publication.postText());
            safeSleep();
            if (!history.transition(account, day, "BODY_PREPARING", "BODY_PUBLISHING")) throw duplicate();
            postId = client.publishContainer(container);
            history.bodyPublished(account, day, postId);
        } catch (Exception e) {
            // 컨테이너 생성 실패는 안전하게 재시도할 수 있지만 실제 게시 응답 유실은 재게시하지 않습니다.
            recordFailure(account, day, "BODY_PREPARING", "BODY_READY", "BODY_PUBLISHING");
            notifySlack("🚨 Threads 발행 상태 확인 필요\n날짜. " + day + "\n본문·댓글을 다시 발행하기 전에 저장된 상태와 Threads 실제 피드를 확인해주세요.\n오류 유형. " + e.getClass().getSimpleName());
            throw new IllegalStateException("Threads 발행을 완료하지 못했습니다. 저장된 상태를 확인한 뒤 재시도해주세요.");
        }
        return publishReply(new ThreadsPublicationHistory.Publication(account, day, publication.topicKey(), publication.postText(), publication.replyText(), postId, null, "BODY_PUBLISHED"));
    }

    private ThreadsPostResult publishReply(ThreadsPublicationHistory.Publication publication) {
        String account = publication.accountId();
        LocalDate day = publication.day();
        if (!history.transition(account, day, "BODY_PUBLISHED", "REPLY_PREPARING")) throw duplicate();
        try {
            String container = client.createReplyContainer(publication.postId(), publication.replyText());
            safeSleep();
            if (!history.transition(account, day, "REPLY_PREPARING", "REPLY_PUBLISHING")) throw duplicate();
            String replyId = client.publishContainer(container);
            history.replyPublished(account, day, replyId);
            ThreadsPostResult result = ThreadsPostResult.builder().success(true).postId(publication.postId()).replyId(replyId)
                    .postContent(publication.postText()).replyContent(publication.replyText()).publishedAt(LocalDateTime.now(ZONE)).build();
            notifySlack("🧵 Threads 일간 발행 완료\n본문. " + publication.postText() + "\n\n첫 댓글. " + publication.replyText() + "\n\n새 댓글 알림과 전날 타인 댓글 수는 별도로 전달합니다.");
            return result;
        } catch (Exception e) {
            recordFailure(account, day, "REPLY_PREPARING", "BODY_PUBLISHED", "REPLY_PUBLISHING");
            notifySlack("🚨 Threads 첫 댓글 확인 필요\n날짜. " + day + "\n본문은 발행되어 추가 본문을 만들지 않습니다. 첫 댓글 생성 실패는 댓글만 재시도하고, 게시 응답이 불확실하면 실제 피드를 먼저 확인해주세요.\n오류 유형. " + e.getClass().getSimpleName());
            throw new IllegalStateException("본문은 발행되었으나 첫 댓글을 완료하지 못했습니다. 저장된 댓글 상태를 확인해주세요.");
        }
    }

    private void recordFailure(String account, LocalDate day, String preparing, String retryable, String publishing) {
        try {
            history.transition(account, day, preparing, retryable);
            history.transition(account, day, publishing, "UNCERTAIN");
        } catch (Exception e) {
            // DB도 사용할 수 없는 경우에도 아래 Slack 진단 알림은 시도합니다.
            log.error("[Threads 발행 기록] 실패 상태를 저장하지 못했습니다. 오류 유형={}", e.getClass().getSimpleName());
        }
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "오늘의 Threads 발행이 완료되었거나 처리 중입니다. 발행 결과가 불확실한 경우 확인 전 재게시하지 않습니다.");
    }

    private void safeSleep() {
        try { Thread.sleep(2000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Threads 게시 대기가 중단되었습니다."); }
    }

    private void notifySlack(String message) {
        if (!configured(slackWebhookUrl)) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            var response = restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(Map.of("text", "Threads 운영 알림", "blocks", java.util.List.of(Map.of("type", "section", "text", Map.of("type", "plain_text", "text", message)))), headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || !"ok".equals(response.getBody())) throw new IllegalStateException("Slack 수신 확인을 받지 못했습니다.");
        } catch (Exception e) {
            log.warn("[Threads Slack] 알림 전달 실패. 오류 유형={}", e.getClass().getSimpleName());
        }
    }

    private boolean configured(String value) { return value != null && !value.isBlank() && !"mock".equalsIgnoreCase(value.trim()); }
}
