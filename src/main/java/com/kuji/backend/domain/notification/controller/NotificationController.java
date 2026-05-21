package com.kuji.backend.domain.notification.controller;

import com.kuji.backend.domain.notification.dto.NotificationResponse;
import com.kuji.backend.domain.notification.dto.TokenRequest;
import com.kuji.backend.domain.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 기기 토큰 등록 및 갱신 (알림 켜기)
     */
    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(
            Authentication authentication,
            @Valid @RequestBody TokenRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        notificationService.registerToken(memberId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 기기 토큰 삭제 (알림 끄기 / 로그아웃)
     */
    @DeleteMapping("/token/{deviceId}")
    public ResponseEntity<Void> deleteToken(
            Authentication authentication,
            @PathVariable("deviceId") String deviceId) {
        Long memberId = (Long) authentication.getPrincipal();
        notificationService.deleteToken(memberId, deviceId);
        return ResponseEntity.ok().build();
    }

    /**
     * 내 알림 목록 조회 (인앱 알림 센터)
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            Authentication authentication,
            @PageableDefault(size = 10) Pageable pageable) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getMyNotifications(memberId, pageable));
    }

    /**
     * 특정 알림 읽음 처리
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> readNotification(
            Authentication authentication,
            @PathVariable("id") Long notificationId) {
        Long memberId = (Long) authentication.getPrincipal();
        notificationService.readNotification(memberId, notificationId);
        return ResponseEntity.ok().build();
    }

    /**
     * 모든 알림 읽음 처리
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAllNotifications(
            Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        notificationService.readAllNotifications(memberId);
        return ResponseEntity.ok().build();
    }
}
