package com.kuji.backend.domain.marketing.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAgentService {

    @Value("${gemini.api-key:mock}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    private ChatLanguageModel geminiChatModel;

    @PostConstruct
    public void init() {
        if (!"mock".equals(geminiApiKey)) {
            try {
                this.geminiChatModel = GoogleAiGeminiChatModel.builder()
                        .apiKey(geminiApiKey)
                        .modelName(geminiModel)
                        .temperature(0.8) // 마케팅 문구는 창의적이어야 하므로 온도(Temperature)를 높게 설정
                        .build();
                log.info("[Marketing Agent] Gemini 2.5 Flash LLM 모델 초기화 완료");
            } catch (Exception e) {
                log.error("[Marketing Agent] 초기화 실패 (혹시 랭체인 버전과 클래스명이 다를 경우를 대비): {}", e.getMessage());
            }
        }
    }

    /**
     * 라스트상 임박 시, 유저의 데이터를 기반으로 소름 돋게 맞춤형 푸시 메시지를 생성합니다.
     * @param kujiTitle 진행 중인 쿠지 이름
     * @param remainingCount 남은 잔여 스크래치 개수
     * @param userName 타겟 유저 이름
     * @param recentBehavior 유저의 최근 행동 (예: "3일 전 조회", "어제 장바구니 담음" 등)
     * @return AI가 생성한 푸시 메시지 (제목\n내용)
     */
    public String generatePersonalizedLastPrizePush(String kujiTitle, int remainingCount, String userName, String recentBehavior) {
        if (geminiChatModel == null) {
            return "[긴급 알림]\n" + userName + "님! " + kujiTitle + " 라스트상까지 단 " + remainingCount + "개 남았습니다!";
        }

        log.info("[Marketing Agent] ✍️ {}님을 위한 라스트상 꼬시기 문구 작성 중...", userName);

        String prompt = String.format("""
                너는 이커머스에서 구매 전환율을 극대화하는 천재 마케팅 카피라이터 에이전트야.
                아래 상황을 보고, 유저가 당장 앱에 들어와서 결제하고 싶게 만드는 모바일 푸시 알림 문구를 작성해줘.
                
                [상황]
                - 쿠지(뽑기) 상품명: %s
                - 남은 잔여 개수: %d개 (이것만 다 사면 라스트상을 무조건 받음)
                - 유저 이름: %s
                - 유저의 최근 행동: %s
                
                [조건]
                1. 첫 줄은 제목, 두 번째 줄부터는 본문으로 작성할 것 (최대 3줄 이내).
                2. 유저의 '최근 행동'을 자연스럽게 언급하면서 FOMO(매진 임박 공포)를 자극할 것.
                3. 친근하고 약간 다급한 톤으로 작성할 것. 이모지(🔥, 🚨, 🎁 등) 적극 활용.
                """, kujiTitle, remainingCount, userName, recentBehavior);

        try {
            String copy = geminiChatModel.generate(prompt);
            log.info("[Marketing Agent] 💡 AI 카피라이팅 완료: \n{}", copy);
            return copy;
        } catch (Exception e) {
            log.error("[Marketing Agent] 문구 생성 실패: {}", e.getMessage());
            return "[긴급 알림]\n" + userName + "님! " + kujiTitle + " 라스트상까지 단 " + remainingCount + "개 남았습니다!";
        }
    }
}
