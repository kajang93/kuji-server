package com.kuji.backend.domain.notification.service;

import com.google.firebase.messaging.*;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.dto.TokenRequest;
import com.kuji.backend.domain.notification.entity.DeviceToken;
import com.kuji.backend.domain.notification.entity.Notification;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.repository.DeviceTokenRepository;
import com.kuji.backend.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Member receiver;

    @BeforeEach
    void setUp() {
        receiver = Member.builder()
                .email("receiver@test.com")
                .nickname("수신자")
                .role(RoleType.USER)
                .socialType(SocialType.LOCAL)
                .isTermsAgreed(true)
                .isPrivacyAgreed(true)
                .isMarketingAgreed(false)
                .build();
        memberRepository.save(receiver);
    }

    @Test
    @DisplayName("토큰 등록 및 갱신 기능 테스트")
    void registerTokenTest() {
        // given
        TokenRequest request = mock(TokenRequest.class);
        when(request.getDeviceId()).thenReturn("device-1");
        when(request.getToken()).thenReturn("token-initial");
        when(request.getPlatform()).thenReturn("WEB");

        // when
        notificationService.registerToken(receiver.getId(), request);

        // then
        List<DeviceToken> tokens = deviceTokenRepository.findAllByMember(receiver);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getToken()).isEqualTo("token-initial");

        // 갱신 테스트
        TokenRequest updateRequest = mock(TokenRequest.class);
        when(updateRequest.getDeviceId()).thenReturn("device-1");
        when(updateRequest.getToken()).thenReturn("token-updated");
        when(updateRequest.getPlatform()).thenReturn("WEB");

        notificationService.registerToken(receiver.getId(), updateRequest);

        tokens = deviceTokenRepository.findAllByMember(receiver);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getToken()).isEqualTo("token-updated");
    }

    @Test
    @DisplayName("토큰 삭제 기능 테스트")
    void deleteTokenTest() {
        // given
        DeviceToken token = DeviceToken.builder()
                .member(receiver)
                .deviceId("device-1")
                .token("token-1")
                .platform("WEB")
                .build();
        deviceTokenRepository.save(token);

        // when
        notificationService.deleteToken(receiver.getId(), "device-1");

        // then
        List<DeviceToken> tokens = deviceTokenRepository.findAllByMember(receiver);
        assertThat(tokens).isEmpty();
    }

    @Test
    @DisplayName("알림 발송 및 FCM 실패 토큰 자동 삭제 테스트")
    void sendNotificationAndCleanUpFailedTokensTest() throws Exception {
        // given
        DeviceToken token = DeviceToken.builder()
                .member(receiver)
                .deviceId("device-1")
                .token("invalid-token")
                .platform("WEB")
                .build();
        deviceTokenRepository.save(token);

        // Mock static FirebaseMessaging
        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            BatchResponse mockBatchResponse = mock(BatchResponse.class);
            SendResponse mockSendResponse = mock(SendResponse.class);
            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
            MessagingErrorCode mockErrorCode = MessagingErrorCode.UNREGISTERED;

            when(mockBatchResponse.getFailureCount()).thenReturn(1);
            when(mockBatchResponse.getResponses()).thenReturn(List.of(mockSendResponse));
            when(mockSendResponse.isSuccessful()).thenReturn(false);
            when(mockSendResponse.getException()).thenReturn(mockException);
            when(mockException.getMessagingErrorCode()).thenReturn(mockErrorCode);

            when(mockMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

            // when
            notificationService.sendNotification(receiver, "제목", "내용", NotificationType.COMMENT, "1");

            // then: DB에 알림 저장 확인
            List<Notification> notifications = notificationRepository.findAll();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getTitle()).isEqualTo("제목");

            // then: 실패한 토큰(UNREGISTERED)이 DB에서 자동 삭제되었는지 확인
            List<DeviceToken> tokens = deviceTokenRepository.findAllByMember(receiver);
            assertThat(tokens).isEmpty();
        }
    }
}
