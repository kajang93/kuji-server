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
@RequestMapping("/api/admin/marketing")
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
    }
}
