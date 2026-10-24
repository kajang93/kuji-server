package com.kuji.backend.domain.report.service;

import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.global.infra.ExternalApiHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final DrawHistoryRepository drawHistoryRepository;

    @Value("${gemini.api-key}")
    private String geminiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    @Value("${slack.webhook-url}")
    private String slackWebhookUrl;

    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    /** 매일 한국 시간 오전 9시에 전날 자정부터 자정 직전까지의 운영 통계를 발송합니다. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public boolean generateAndSendDailyReport() {
        if (!isConfigured(slackWebhookUrl)) {
            log.warn("[운영 리포트] 슬랙 웹훅이 설정되지 않아 발송을 건너뜁니다.");
            return false;
        }

        try {
            LocalDate reportDate = LocalDate.now(REPORT_ZONE).minusDays(1);
            LocalDateTime start = reportDate.atStartOfDay();
            LocalDateTime end = reportDate.plusDays(1).atStartOfDay();
            long newUsers = memberRepository.countMembersCreatedBetween(start, end);
            long newDraws = drawHistoryRepository.countDrawsCreatedBetween(start, end);

            String rawStats = String.format(
                    "집계 기간. %s 00:00 이상 ~ %s 00:00 미만 (한국 시간, Asia/Seoul)\n" +
                    "• 신규 가입. %d명\n" +
                    "• 뽑기 내역. %d건\n" +
                    "• 방문자·페이지뷰, 결제 성공·실패, 서버 다운타임. 미집계.",
                    reportDate, reportDate.plusDays(1), newUsers, newDraws);

            String aiSummary = askGemini(rawStats);
            sendToSlack(rawStats + "\n\n*운영 메모*\n" + aiSummary);
            log.info("[운영 리포트] {} 집계 결과를 슬랙으로 발송했습니다.", reportDate);
            return true;
        } catch (Exception e) {
            // 외부 오류 본문이나 URL에는 인증 정보가 포함될 수 있으므로 기록하지 않습니다.
            log.error("[운영 리포트] 통계 조회 또는 슬랙 발송에 실패했습니다. 오류 유형={}", e.getClass().getSimpleName());
            return false;
        }
    }

    private String askGemini(String stats) {
        if (!isConfigured(geminiKey)) {
            return "AI 요약이 설정되지 않아 원본 통계만 전달합니다.";
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiKey);

        String prompt = "쿠지 서비스의 일간 운영 통계를 한국어로 최대 세 문장으로 요약해 주세요. " +
                "제공된 수치만 사용하고 뽑기 내역을 결제 건수나 매출로 해석하지 마세요. " +
                "전일 비교나 원인 추정, 과장된 칭찬, 마케팅 제안은 하지 마세요. " +
                "미집계 항목은 알 수 없다고 명시하세요.\n\n" + stats;
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        try {
            Map<?, ?> responseBody = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            if (responseBody != null && responseBody.get("candidates") instanceof List<?> candidates &&
                    !candidates.isEmpty() && candidates.get(0) instanceof Map<?, ?> candidate &&
                    candidate.get("content") instanceof Map<?, ?> content &&
                    content.get("parts") instanceof List<?> parts && !parts.isEmpty() &&
                    parts.get(0) instanceof Map<?, ?> part && part.get("text") instanceof String text &&
                    StringUtils.hasText(text)) {
                return text;
            }
            log.warn("[운영 리포트] AI 요약이 비어 있어 원본 통계를 전달합니다.");
        } catch (Exception e) {
            log.warn("[운영 리포트] AI 요약을 사용할 수 없습니다. 오류 유형={}", e.getClass().getSimpleName());
        }
        return "AI 요약을 불러오지 못했습니다. 위 원본 통계를 확인해 주세요.";
    }

    private boolean isConfigured(String value) {
        return StringUtils.hasText(value) && !"mock".equalsIgnoreCase(value.trim());
    }

    private void sendToSlack(String messageText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("text", "📊 *일간 운영 리포트*\n\n" + messageText);
        restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(body, headers), String.class);
    }
}
