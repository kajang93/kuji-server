package com.kuji.backend.domain.notification.controller;

import com.kuji.backend.domain.notification.dto.NotificationSettingRequest;
import com.kuji.backend.domain.notification.dto.NotificationSettingResponse;
import com.kuji.backend.domain.notification.service.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/settings")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService settingService;

    /**
     * 내 알림 수신 설정 조회
     */
    @GetMapping
    public ResponseEntity<NotificationSettingResponse> getSettings(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(settingService.getSettings(memberId));
    }

    /**
     * 알림 수신 설정 업데이트 (전체 토글 일괄 저장)
     */
    @PatchMapping
    public ResponseEntity<Void> updateSettings(
            Authentication authentication,
            @RequestBody NotificationSettingRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        settingService.updateSettings(memberId, request);
        return ResponseEntity.ok().build();
    }
}
