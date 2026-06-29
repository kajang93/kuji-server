package com.kuji.backend.domain.report.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiReportService {

    @Value("${gemini.api-key}")
    private String geminiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${slack.webhook-url}")
    private String slackWebhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 매일 오전 9시에 전날의 운영 상태를 분석하여 슬랙으로 발송
     * 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void generateAndSendDailyReport() {
        if ("mock".equals(geminiKey) || "mock".equals(slackWebhookUrl)) {
            log.warn("[AI 리포트] API 키 또는 슬랙 웹훅 URL이 설정되지 않아 리포트 발송을 건너뜁니다.");
            return;
        }

        log.info("[AI 리포트] 일간 운영 리포트 생성 및 발송을 시작합니다.");

        try {
            // 1. 모니터링 데이터(또는 DB 통계)를 요약해 텍스트로 구성 (임시 더미 데이터)
            String rawStats = "어제 하루 결제 성공 건수: 152건, 결제 실패 건수: 3건 (사유: 잔액 부족), 새로 가입한 유저: 45명. 서버 다운타임: 0분.";

            // 2. Gemini에 분석 요청
            String aiSummary = askGemini(rawStats);

            // 3. 분석된 내용을 Slack으로 발송
            sendToSlack(aiSummary);

            log.info("[AI 리포트] 일간 리포트 발송 완료!");
        } catch (Exception e) {
            log.error("[AI 리포트] 리포트 발송 중 오류 발생: {}", e.getMessage());
        }
    }

    private String askGemini(String stats) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = "너는 쿠지(랜덤 뽑기) 앱의 친절하고 분석적인 최고 운영 책임자(COO)야. 다음 통계 데이터를 바탕으로 CEO에게 보고할 '매우 상세하고 심도 있는' 일간 운영 브리핑을 작성해줘. " +
                "단순한 수치 나열을 넘어서, 1. 전반적인 요약, 2. 주요 지표 분석 (결제 성공/실패 분석, 가입자 동향 등), 3. 향후 비즈니스 인사이트 및 제안사항을 포함해서 마크다운 형식으로 길고 자세하게 써줘. 이모지도 적절히 써줘:\n\n" + stats;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> contentMap = Map.of("parts", List.of(textPart));
        Map<String, Object> body = Map.of("contents", List.of(contentMap));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (!parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Gemini API 오류] {}", e.getMessage());
            return "⚠️ AI 분석 중 오류가 발생했습니다.\n데이터 원본: " + stats;
        }
        return "⚠️ 분석 결과를 불러오지 못했습니다.";
    }

    private void sendToSlack(String messageText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", "📊 *[일간 AI 운영 리포트]*\n\n" + messageText);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(slackWebhookUrl, request, String.class);
    }
}
