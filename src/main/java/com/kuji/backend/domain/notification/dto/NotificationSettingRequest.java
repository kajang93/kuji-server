package com.kuji.backend.domain.notification.dto;

public record NotificationSettingRequest(
        Boolean pushEnabled,
        Boolean kakaoWinning,
        Boolean kakaoDelivery,
        Boolean kakaoInquiry,
        Boolean kakaoBizOrder,
        Boolean kakaoBizCancel,
        Boolean kakaoBizInquiry,
        Boolean marketingOpen,
        Boolean marketingRestock,
        Boolean marketingEvent,
        Boolean nightPush
) {}
