package com.kuji.backend.domain.notification.dto;

import com.kuji.backend.domain.notification.entity.NotificationSetting;

public record NotificationSettingResponse(
        boolean pushEnabled,
        boolean kakaoWinning,
        boolean kakaoDelivery,
        boolean kakaoInquiry,
        boolean kakaoBizOrder,
        boolean kakaoBizCancel,
        boolean kakaoBizInquiry,
        boolean marketingOpen,
        boolean marketingRestock,
        boolean marketingEvent,
        boolean nightPush
) {
    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.isPushEnabled(),
                setting.isKakaoWinning(),
                setting.isKakaoDelivery(),
                setting.isKakaoInquiry(),
                setting.isKakaoBizOrder(),
                setting.isKakaoBizCancel(),
                setting.isKakaoBizInquiry(),
                setting.isMarketingOpen(),
                setting.isMarketingRestock(),
                setting.isMarketingEvent(),
                setting.isNightPush()
        );
    }

    /** 설정이 없는 신규 유저에게 반환할 기본값 */
    public static NotificationSettingResponse defaultValue() {
        return new NotificationSettingResponse(true, true, true, true, true, true, true, true, true, true, false);
    }
}
