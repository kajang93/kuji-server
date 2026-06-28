package com.kuji.backend.domain.marketing.controller;

import com.kuji.backend.domain.marketing.service.MarketingAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingAgentController {

    private final MarketingAgentService marketingAgentService;

    /**
     * 테스트용: 마케팅 에이전트(Gemini)에게 타겟팅 푸시 카피라이팅을 시켜봅니다.
     */
    @GetMapping("/test-push-copy")
    public ResponseEntity<Map<String, String>> testPushCopy(
            @RequestParam(defaultValue = "제일복권 원피스 EX") String kujiTitle,
            @RequestParam(defaultValue = "3") int remainingCount,
            @RequestParam(defaultValue = "김쿠지") String userName,
            @RequestParam(defaultValue = "어제 해당 쿠지를 찜(Wish) 해두고 아직 결제 안함") String recentBehavior
    ) {
        String generatedCopy = marketingAgentService.generatePersonalizedLastPrizePush(
                kujiTitle, remainingCount, userName, recentBehavior
        );

        return ResponseEntity.ok(Map.of(
                "userName", userName,
                "kujiTitle", kujiTitle,
                "remainingCount", String.valueOf(remainingCount),
                "aiGeneratedCopy", generatedCopy
        ));
    /**
     * 테스트용: 마케팅 에이전트가 캠페인을 기획하고 운영 에이전트에게 승인을 요청하는 전체 A2A 시나리오
     */
    @GetMapping("/test-a2a-campaign")
    public ResponseEntity<String> testA2aCampaign(
            @RequestParam(defaultValue = "나의 히어로 아카데미아") String kujiTitle,
            @RequestParam(defaultValue = "15000") int targetUserCount,
            @RequestParam(defaultValue = "VIP유저") String userName,
            @RequestParam(defaultValue = "1시간 전 로그인함") String recentBehavior
    ) {
        // 1. 마케팅 에이전트가 카피를 짜고 이벤트(승인 요청)를 발행
        marketingAgentService.proposeCampaignToOpsAgent(kujiTitle, targetUserCount, userName, recentBehavior);
        
        return ResponseEntity.ok("A2A 캠페인 트리거 완료! 백엔드 콘솔 로그(Ops Agent의 심사 결과)를 확인하세요.");
    }
}
