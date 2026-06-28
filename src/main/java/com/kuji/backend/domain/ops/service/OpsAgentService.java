package com.kuji.backend.domain.ops.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpsAgentService {

    @Value("${groq.api-key:mock}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${slack.webhook-url:mock}")
    private String slackWebhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private ChatLanguageModel groqChatModel;

    // 대기 중인 장애 조치 승인 목록 (메모리 저장소)
    private final Map<String, Incident> pendingIncidents = new ConcurrentHashMap<>();

    // 장애 정보 보관용 내부 레코드 (보안 토큰 추가)
    public record Incident(String id, String description, String suggestedAction, String token) {}

    @PostConstruct
    public void init() {
        if (!"mock".equals(groqApiKey)) {
            this.groqChatModel = OpenAiChatModel.builder()
                    .apiKey(groqApiKey)
                    .modelName(groqModel)
                    .baseUrl("https://api.groq.com/openai/v1")
                    .temperature(0.2)
                    .build();
            log.info("[Ops Agent] Groq LLM 모델 초기화 완료");
        }
    }

    public void analyzeAndReportAnomaly(String errorLog) {
        if ("mock".equals(groqApiKey) || groqChatModel == null) {
            log.warn("[Ops Agent] API 키가 없어 에이전트 분석을 건너뜁니다.");
            return;
        }

        log.info("[Ops Agent] 🚨 이상 징후 감지. Groq AI 분석 시작...");
        
        try {
            String prompt = """
                    너는 최고의 서버 운영(DevOps) SRE 엔지니어 에이전트야.
                    다음 백엔드 서버 에러 로그를 분석해서 아래 형식으로 답변해줘.
                    
                    [원인 분석] (무슨 문제인지 1~2줄 요약)
                    [추천 조치] (어떻게 해결해야 하는지 1~2줄 요약)
                    
                    에러 로그:
                    """ + errorLog;

            String aiAnalysis = groqChatModel.generate(prompt);
            log.info("[Ops Agent] 💡 AI 분석 완료: {}", aiAnalysis);

            String incidentId = UUID.randomUUID().toString();
            String token = UUID.randomUUID().toString().replace("-", ""); // 보안 토큰
            Incident incident = new Incident(incidentId, errorLog, aiAnalysis, token);
            pendingIncidents.put(incidentId, incident);

            sendApprovalRequestToSlack(incident);

        } catch (Exception e) {
            log.error("[Ops Agent] 분석 중 오류 발생: {}", e.getMessage());
        }
    }

    private void sendApprovalRequestToSlack(Incident incident) {
        if ("mock".equals(slackWebhookUrl)) return;

        String serverBaseUrl = "https://kujishop.shop";
        String approveUrl = serverBaseUrl + "/api/ops/incident/" + incident.id() + "/approve?token=" + incident.token();
        String rejectUrl = serverBaseUrl + "/api/ops/incident/" + incident.id() + "/reject?token=" + incident.token();

        String slackMessage = String.format("""
                🚨 *[운영 에이전트 긴급 보고]*
                
                서버에서 이상 징후가 감지되어 AI 분석 결과를 보고합니다.
                
                %s
                
                ⚠️ *조치 승인 대기 중입니다. 버튼을 클릭하여 결정해 주세요.*
                ✅ <%-s|승인(Approve) 및 자동 조치 실행>
                ❌ <%-s|승인 거절(Reject) 및 무시>
                """, incident.suggestedAction(), approveUrl, rejectUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", slackMessage);

        try {
            restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(body, headers), String.class);
            log.info("[Ops Agent] 📨 슬랙으로 승인 요청 발송 완료 (Incident ID: {})", incident.id());
        } catch (Exception e) {
            log.error("[Ops Agent] 슬랙 발송 실패: {}", e.getMessage());
        }
    }

    public String approveIncident(String incidentId, String token) {
        Incident incident = pendingIncidents.get(incidentId);
        if (incident == null) return "❌ 만료되었거나 존재하지 않는 안건입니다.";
        if (!incident.token().equals(token)) return "❌ 잘못된 접근입니다 (토큰 불일치).";
        
        pendingIncidents.remove(incidentId);
        log.info("[Ops Agent] ✅ 사장님 승인 완료! 조치 실행: {}", incident.suggestedAction());
        return "✅ 승인 완료되었습니다. AI가 자동으로 조치를 실행합니다.";
    }

    public String rejectIncident(String incidentId, String token) {
        Incident incident = pendingIncidents.get(incidentId);
        if (incident == null) return "❌ 만료되었거나 존재하지 않는 안건입니다.";
        if (!incident.token().equals(token)) return "❌ 잘못된 접근입니다 (토큰 불일치).";
        
        pendingIncidents.remove(incidentId);
        log.info("[Ops Agent] ❌ 사장님 승인 거절. 조치를 취소합니다.");
        return "❌ 승인이 거절되었습니다. 아무런 조치도 취하지 않습니다.";
    }
}
