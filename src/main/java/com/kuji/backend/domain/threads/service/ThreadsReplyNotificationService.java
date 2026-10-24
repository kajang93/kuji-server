// 새 쓰레드 댓글을 중복 없이 슬랙에 전달합니다.
package com.kuji.backend.domain.threads.service;

import com.kuji.backend.domain.threads.dto.ThreadsMediaPage;
import com.kuji.backend.domain.threads.entity.ThreadsReplyMonitorState;
import com.kuji.backend.domain.threads.entity.ThreadsReplyNotification;
import com.kuji.backend.domain.threads.repository.ThreadsReplyMonitorStateRepository;
import com.kuji.backend.domain.threads.repository.ThreadsReplyNotificationRepository;
import com.kuji.backend.global.infra.ExternalApiHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThreadsReplyNotificationService {
    private final ThreadsReplyMonitorStateRepository stateRepository;
    private final ThreadsReplyNotificationRepository notificationRepository;
    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    @Value("${slack.webhook-url:mock}")
    private String slackWebhookUrl;

    @Transactional
    public Instant getMonitoringStart(String accountId) {
        return stateRepository.findById(accountId)
                .orElseGet(() -> stateRepository.saveAndFlush(new ThreadsReplyMonitorState(accountId, Instant.now())))
                .getStartedAt();
    }

    @Transactional
    public void notifyReply(String accountId, ThreadsMediaPage.Media reply) {
        // 여러 서버가 같은 댓글을 발견해도 계정 잠금 안에서 전달 여부를 다시 확인합니다.
        stateRepository.findByAccountIdWithLock(accountId).orElseThrow();
        if (notificationRepository.existsById(reply.id())) {
            return;
        }
        String text = reply.text() == null || reply.text().isBlank() ? "(이미지·영상 댓글)" : reply.text();
        if (text.length() > 2400) {
            text = text.substring(0, 2400) + "…";
        }
        String author = reply.username() == null || reply.username().isBlank() ? "비공개 사용자" : "@" + reply.username();
        String message = "🧵 새 Threads 댓글\n작성자. " + author + "\n\n" + text;
        if (reply.permalink() != null && reply.permalink().matches("https://(?:www\\.)?threads\\.(?:com|net)/@[A-Za-z0-9._]+/post/[A-Za-z0-9_-]+/?")) {
            message += "\n\n" + reply.permalink();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 댓글을 평문 블록으로 보내 채널 전체 멘션과 링크 마크업을 실행하지 않습니다.
        Map<String, Object> body = Map.of(
                "text", "Threads에 새 댓글이 도착했습니다.",
                "blocks", List.of(Map.of("type", "section", "text", Map.of("type", "plain_text", "text", message))));
        ResponseEntity<String> response = restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful() || !"ok".equals(response.getBody())) {
            throw new IllegalStateException("쓰레드 댓글 슬랙 알림을 전달하지 못했습니다.");
        }
        notificationRepository.save(new ThreadsReplyNotification(reply.id(), Instant.now()));
    }
}
