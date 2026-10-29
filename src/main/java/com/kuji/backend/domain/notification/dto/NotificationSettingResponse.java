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
        boolean nightPush,
        boolean marketingAgreed
) {
    public static NotificationSettingResponse from(NotificationSetting setting, boolean marketingAgreed) {
        return new NotificationSettingResponse(
                setting.isPushEnabled(),
                setting.isKakaoWinning(),
                setting.isKakaoDelivery(),
                setting.isKakaoInquiry(),
                setting.isKakaoBizOrder(),
                setting.isKakaoBizCancel(),
                setting.isKakaoBizInquiry(),
                marketingAgreed && setting.isMarketingOpen(),
                marketingAgreed && setting.isMarketingRestock(),
                marketingAgreed && setting.isMarketingEvent(),
                marketingAgreed && setting.isNightPush(),
                marketingAgreed
        );
    }

    /** 설정이 없는 신규 유저에게 반환할 기본값 */
    public static NotificationSettingResponse defaultValue(boolean marketingAgreed) {
        return new NotificationSettingResponse(true, true, true, true, true, true, true, false, false, false, false, marketingAgreed);
    }
}
