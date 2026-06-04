package com.kuji.backend.global.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SlackWebhookAppender extends AppenderBase<ILoggingEvent> {

    private String webhookUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SlackWebhookAppender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (webhookUrl == null || webhookUrl.isBlank() || "mock".equals(webhookUrl)) {
            return;
        }

        try {
            // 메시지 구성
            String traceId = eventObject.getMDCPropertyMap().getOrDefault("traceId", "N/A");
            String memberId = eventObject.getMDCPropertyMap().getOrDefault("memberId", "N/A");
            String message = String.format("🚨 [ERROR] %s\n- Trace ID: %s\n- Member ID: %s\n- Message: %s",
                    eventObject.getLoggerName(), traceId, memberId, eventObject.getFormattedMessage());

            Map<String, String> payload = new HashMap<>();
            payload.put("text", message);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // 백그라운드 스레드에서 발송하므로 비동기로 요청
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());

        } catch (Exception e) {
            // 알림 발송 중 에러가 나더라도 서비스 로직에 영향을 주지 않도록 무시
            addError("Failed to send Slack webhook", e);
        }
    }
}
