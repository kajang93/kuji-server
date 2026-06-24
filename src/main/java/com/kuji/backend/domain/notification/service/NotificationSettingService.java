package com.kuji.backend.domain.notification.service;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.dto.NotificationSettingRequest;
import com.kuji.backend.domain.notification.dto.NotificationSettingResponse;
import com.kuji.backend.domain.notification.entity.NotificationSetting;
import com.kuji.backend.domain.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final MemberRepository memberRepository;

    /**
     * 내 알림 설정 조회 (없으면 기본값 반환, DB에는 저장하지 않음)
     */
    @Transactional(readOnly = true)
    public NotificationSettingResponse getSettings(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return settingRepository.findByMember(member)
                .map(NotificationSettingResponse::from)
                .orElse(NotificationSettingResponse.defaultValue());
    }

    /**
     * 알림 설정 업데이트 (없으면 새로 생성)
     */
    public void updateSettings(Long memberId, NotificationSettingRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        NotificationSetting setting = settingRepository.findByMember(member)
                .orElseGet(() -> settingRepository.save(
                        NotificationSetting.builder().member(member).build()
                ));

        setting.updateSettings(
                request.pushEnabled() != null ? request.pushEnabled() : setting.isPushEnabled(),
                request.kakaoWinning() != null ? request.kakaoWinning() : setting.isKakaoWinning(),
                request.kakaoDelivery() != null ? request.kakaoDelivery() : setting.isKakaoDelivery(),
                request.kakaoInquiry() != null ? request.kakaoInquiry() : setting.isKakaoInquiry(),
                request.kakaoBizOrder() != null ? request.kakaoBizOrder() : setting.isKakaoBizOrder(),
                request.kakaoBizCancel() != null ? request.kakaoBizCancel() : setting.isKakaoBizCancel(),
                request.kakaoBizInquiry() != null ? request.kakaoBizInquiry() : setting.isKakaoBizInquiry(),
                request.marketingOpen() != null ? request.marketingOpen() : setting.isMarketingOpen(),
                request.marketingRestock() != null ? request.marketingRestock() : setting.isMarketingRestock(),
                request.marketingEvent() != null ? request.marketingEvent() : setting.isMarketingEvent(),
                request.nightPush() != null ? request.nightPush() : setting.isNightPush()
        );
    }
}
