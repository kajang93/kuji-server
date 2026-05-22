package com.kuji.backend.domain.notification.dto;

public record NotificationSettingRequest(
        boolean pushEnabled,
        boolean kakaoWinning,
        boolean kakaoDelivery,
        boolean kakaoInquiry,
        boolean marketingOpen,
        boolean marketingRestock,
        boolean marketingEvent,
        boolean nightPush
) {}
