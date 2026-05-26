package com.kuji.backend.domain.notification.service;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.dto.NotificationSettingRequest;
import com.kuji.backend.domain.notification.dto.NotificationSettingResponse;
import com.kuji.backend.domain.notification.entity.NotificationSetting;
import com.kuji.backend.domain.notification.repository.NotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NotificationSettingServiceTest {

    @Autowired
    private NotificationSettingService settingService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationSettingRepository settingRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("testuser@test.com")
                .nickname("테스트유저")
                .role(RoleType.USER)
                .socialType(SocialType.LOCAL)
                .isTermsAgreed(true)
                .isPrivacyAgreed(true)
                .isMarketingAgreed(false)
                .build();
        memberRepository.save(member);
    }

    @Test
    @DisplayName("기존 설정이 없을 때 getSettings 호출 시 기본값을 반환해야 한다")
    void getSettingsDefaultTest() {
        NotificationSettingResponse settings = settingService.getSettings(member.getId());
        
        assertThat(settings.pushEnabled()).isTrue();
        assertThat(settings.kakaoWinning()).isTrue();
        assertThat(settings.kakaoDelivery()).isTrue();
        assertThat(settings.kakaoInquiry()).isTrue();
        assertThat(settings.marketingOpen()).isTrue();
        assertThat(settings.marketingRestock()).isTrue();
        assertThat(settings.marketingEvent()).isTrue();
        assertThat(settings.nightPush()).isFalse();
    }

    @Test
    @DisplayName("알림 설정 부분(PATCH) 업데이트 시 NullPointerException이 없어야 하고, 전달한 값만 반영되고 다른 값은 유지되어야 한다")
    void updateSettingsPartialTest() {
        // 1. 기존 설정 정보 저장 (초기 모든 값을 True로 세팅)
        NotificationSetting initialSetting = NotificationSetting.builder()
                .member(member)
                .build();
        initialSetting.updateSettings(true, true, true, true, true, true, true, true);
        settingRepository.save(initialSetting);

        // 2. 부분 업데이트 요청 준비 (kakaoWinning을 false로 끄고, 나머지는 null로 전송)
        NotificationSettingRequest request = new NotificationSettingRequest(
                null,       // pushEnabled
                false,      // kakaoWinning
                null,       // kakaoDelivery
                null,       // kakaoInquiry
                null,       // marketingOpen
                null,       // marketingRestock
                null,       // marketingEvent
                null        // nightPush
        );

        // 3. 업데이트 실행
        settingService.updateSettings(member.getId(), request);

        // 4. 결과 검증
        NotificationSettingResponse updatedSettings = settingService.getSettings(member.getId());
        
        // kakaoWinning만 false로 바뀌고, 기존 true였던 다른 필드들은 true로 유지되어야 함
        assertThat(updatedSettings.kakaoWinning()).isFalse();
        assertThat(updatedSettings.pushEnabled()).isTrue();
        assertThat(updatedSettings.nightPush()).isTrue();
        assertThat(updatedSettings.kakaoDelivery()).isTrue();
    }
}
