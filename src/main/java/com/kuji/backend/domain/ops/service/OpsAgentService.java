package com.kuji.backend.domain.ops.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.kuji.backend.domain.ops.entity.OpsIncident;
import com.kuji.backend.domain.ops.repository.OpsIncidentRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAgentService {

    private final OpsIncidentRepository opsIncidentRepository;

    @Value("${groq.api-key:mock}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${slack.webhook-url:mock}")
    private String slackWebhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private ChatLanguageModel groqChatModel;

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

    @Transactional
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

            OpsIncident incident = OpsIncident.builder()
                    .description(errorLog)
                    .suggestedAction(aiAnalysis)
                    .build();
                    
            opsIncidentRepository.save(incident);

            sendApprovalRequestToSlack(incident);

        } catch (Exception e) {
            log.error("[Ops Agent] 분석 중 오류 발생: {}", e.getMessage());
        }
    }

    private void sendApprovalRequestToSlack(OpsIncident incident) {
        if ("mock".equals(slackWebhookUrl)) return;

        String serverBaseUrl = "https://api.kujishop.shop";
        String approveUrl = serverBaseUrl + "/api/ops/incident/" + incident.getId() + "/approve?token=" + incident.getToken();
        String rejectUrl = serverBaseUrl + "/api/ops/incident/" + incident.getId() + "/reject?token=" + incident.getToken();

        String slackMessage = String.format("""
                🚨 *[운영 에이전트 긴급 보고]*
                
                서버에서 이상 징후가 감지되어 AI 분석 결과를 보고합니다.
                
                %s
                
                ⚠️ *조치 승인 대기 중입니다. 버튼을 클릭하여 결정해 주세요.*
                ✅ <%s|승인(Approve) 및 자동 조치 실행>
                ❌ <%s|승인 거절(Reject) 및 무시>
                """, incident.getSuggestedAction(), approveUrl, rejectUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("text", slackMessage);

        try {
            restTemplate.postForEntity(slackWebhookUrl, new HttpEntity<>(body, headers), String.class);
            log.info("[Ops Agent] 📨 슬랙으로 승인 요청 발송 완료 (Incident ID: {})", incident.getId());
        } catch (Exception e) {
            log.error("[Ops Agent] 슬랙 발송 실패: {}", e.getMessage());
        }
    }

    @Transactional
    public String approveIncident(String incidentId, String token) {
        Optional<OpsIncident> optionalIncident = opsIncidentRepository.findById(incidentId);
        if (optionalIncident.isEmpty()) return "❌ 존재하지 않는 안건입니다.";
        
        OpsIncident incident = optionalIncident.get();
        if (!incident.getToken().equals(token)) return "❌ 잘못된 접근입니다 (토큰 불일치).";
        if (!"PENDING".equals(incident.getStatus())) return "❌ 이미 처리된 안건입니다.";
        
        incident.approve();
        log.info("[Ops Agent] ✅ 사장님 승인 완료! 조치 실행: {}", incident.getSuggestedAction());
        return "✅ 승인 완료되었습니다. AI가 자동으로 조치를 실행합니다.";
    }

    @Transactional
    public String rejectIncident(String incidentId, String token) {
        Optional<OpsIncident> optionalIncident = opsIncidentRepository.findById(incidentId);
        if (optionalIncident.isEmpty()) return "❌ 존재하지 않는 안건입니다.";
        
        OpsIncident incident = optionalIncident.get();
        if (!incident.getToken().equals(token)) return "❌ 잘못된 접근입니다 (토큰 불일치).";
        if (!"PENDING".equals(incident.getStatus())) return "❌ 이미 처리된 안건입니다.";
        
        incident.reject();
        log.info("[Ops Agent] ❌ 사장님 승인 거절. 조치를 취소합니다.");
        return "❌ 승인이 거절되었습니다. 아무런 조치도 취하지 않습니다.";
    }

    @org.springframework.context.event.EventListener
    public void onMarketingCampaignProposal(com.kuji.backend.domain.a2a.event.MarketingCampaignProposalEvent event) {
        log.info("[Ops Agent] 🚨 마케팅 에이전트의 트래픽 허가 요청 수신! (타겟 수: {}명)", event.targetUserCount());
        
        if ("mock".equals(groqApiKey) || groqChatModel == null) {
            log.info("[Ops Agent] API 키가 없어 트래픽 자동 승인 처리합니다.");
            executeMarketingCampaign(event);
            return;
        }

        // 실제 OS 및 JVM 메트릭 실시간 수집
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double currentCpuUsage = osBean.getSystemLoadAverage(); 
        if (currentCpuUsage < 0) currentCpuUsage = 0.5; // LoadAverage 지원 안될 경우 기본값
        
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        
        String prompt = String.format("""
                너는 이커머스 서버 트래픽을 통제하는 깐깐한 SRE 운영 에이전트야.
                마케팅 에이전트가 방금 %d명에게 동시에 FCM 푸시 알림을 쏘겠다고 허락을 구했어.
                현재 서버 상태는 CPU Load %.2f, JVM 사용 메모리 %dMB / 최대 %dMB 야.
                이 트래픽을 허락할지, 분산해서 쏠지 결정해서 아래 형식으로 대답해줘.
                
                [결정] (APPROVE 또는 THROTTLE 또는 REJECT 중 택 1)
                [사유] (결정 사유 1줄)
                """, event.targetUserCount(), currentCpuUsage, usedMemory, maxMemory);

        try {
            String evaluation = groqChatModel.generate(prompt);
            log.info("[Ops Agent] 🛡️ 트래픽 심사 결과: \n{}", evaluation);
            
            if (evaluation.contains("APPROVE") || evaluation.contains("THROTTLE")) {
                log.info("[Ops Agent] ✅ 트래픽 승인 완료. 푸시 발송을 지시합니다.");
                executeMarketingCampaign(event);
            } else {
                log.warn("[Ops Agent] ❌ 서버 부하 우려로 캠페인이 반려되었습니다.");
            }
        } catch (Exception e) {
            log.error("[Ops Agent] 심사 실패. 기본 승인 처리합니다: {}", e.getMessage());
            executeMarketingCampaign(event);
        }
    }

    private void executeMarketingCampaign(com.kuji.backend.domain.a2a.event.MarketingCampaignProposalEvent event) {
        log.info("==================================================");
        log.info("🚀 [최종 실행] {}명에게 라스트상 푸시 알림이 발송 준비 중입니다!!", event.targetUserCount());
        
        try {
            // FCM 메시지 생성 및 발송 (테스트를 위해 topic 구독자에게 브로드캐스트)
            Message message = Message.builder()
                .setNotification(Notification.builder()
                    .setTitle("쿠지 라스트상 임박!")
                    .setBody(event.proposedCopy())
                    .build())
                .setTopic("marketing-promotions")
                .build();
                
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ FCM 푸시 발송 성공: {}", response);
        } catch (Exception e) {
            log.error("❌ FCM 발송 중 오류 발생: {}", e.getMessage());
        }
        
        log.info("==================================================");
    }
}
