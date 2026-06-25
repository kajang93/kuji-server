package com.kuji.backend.domain.notification.service;

import com.google.firebase.messaging.*;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.dto.NotificationResponse;
import com.kuji.backend.domain.notification.dto.TokenRequest;
import com.kuji.backend.domain.notification.entity.DeviceToken;
import com.kuji.backend.domain.notification.entity.Notification;
import com.kuji.backend.domain.notification.entity.NotificationSetting;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.repository.DeviceTokenRepository;
import com.kuji.backend.domain.notification.repository.NotificationRepository;
import com.kuji.backend.domain.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final MemberRepository memberRepository;

    /**
     * 기기 토큰 등록 또는 갱신
     */
    public void registerToken(Long memberId, TokenRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        deviceTokenRepository.findByDeviceIdAndMemberId(request.getDeviceId(), memberId)
                .ifPresentOrElse(
                        deviceToken -> deviceToken.updateToken(request.getToken(), request.getPlatform()),
                        () -> {
                            DeviceToken newToken = DeviceToken.builder()
                                    .member(member)
                                    .token(request.getToken())
                                    .platform(request.getPlatform())
                                    .deviceId(request.getDeviceId())
                                    .build();
                            deviceTokenRepository.save(newToken);
                        }
                );
    }

    /**
     * 기기 토큰 삭제 (로그아웃 또는 알림 끄기)
     */
    public void deleteToken(Long memberId, String deviceId) {
        deviceTokenRepository.deleteByDeviceIdAndMemberId(deviceId, memberId);
    }

    /**
     * 알림 발송 (FCM + DB 저장) - subType 없는 단순 발송
     */
    public void sendNotification(Member receiver, String title, String body, NotificationType type, String targetId) {
        sendNotification(receiver, title, body, type, null, targetId);
    }

    /**
     * 알림 발송 (FCM + DB 저장) - subType 포함 (수신 설정 필터링)
     */
    public void sendNotification(Member receiver, String title, String body, NotificationType type, String subType, String targetId) {
        // 1. DB에 인앱 알림(앱 내부 종 모양 아이콘 리스트)은 무조건 저장합니다!
        Notification notification = Notification.builder()
                .member(receiver)
                .title(title)
                .body(body)
                .type(type)
                .targetId(targetId)
                .build();
        notificationRepository.save(notification);

        // 2. 수신 설정 필터링 (스마트폰 외부 푸시/카톡 알림을 꺼둔 경우 여기서 스킵)
        if (!isNotificationAllowed(receiver, type, subType)) {
            log.info("[외부 푸시 발송 스킵] 수신 설정 OFF. DB에만 저장됨. memberId={}, type={}, subType={}", receiver.getId(), type, subType);
            return;
        }

        // 3. 해당 유저의 유효한 모든 디바이스 토큰 조회
        List<DeviceToken> deviceTokens = deviceTokenRepository.findAllByMember(receiver);
        if (deviceTokens.isEmpty()) {
            log.info("발송 가능한 기기 토큰이 없습니다. memberId={}", receiver.getId());
            return;
        }

        List<String> tokens = deviceTokens.stream()
                .map(DeviceToken::getToken)
                .distinct()
                .collect(Collectors.toList());

        // 4. FCM 메시지 구성
        MulticastMessage message = MulticastMessage.builder()
                .putData("title", title)
                .putData("body", body)
                .putData("type", type.name())
                .putData("targetId", targetId != null ? targetId : "")
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        // 5. FCM 발송
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            // 6. 유효하지 않은 토큰(앱 삭제 등) 자동 정리
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                List<String> failedTokens = new ArrayList<>();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        String errorCode = responses.get(i).getException().getMessagingErrorCode().name();
                        if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                            failedTokens.add(tokens.get(i));
                        }
                    }
                }
                failedTokens.forEach(deviceTokenRepository::deleteByToken);
                log.info("유효하지 않은 토큰 {}개를 삭제했습니다.", failedTokens.size());
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM 푸시 알림 발송 실패: {}", e.getMessage());
        }
    }

    /**
     * 수신 설정 허용 여부 판별
     * - 설정이 없는 신규 유저는 기본 허용(true) 처리
     */
    private boolean isNotificationAllowed(Member receiver, NotificationType type, String subType) {
        NotificationSetting setting = settingRepository.findByMember(receiver).orElse(null);

        // 설정 없으면 기본 허용
        if (setting == null) return true;

        // 전체 푸시 OFF
        if (!setting.isPushEnabled()) return false;

        // 야간 푸시 체크 (오후 10시 ~ 오전 8시)
        if (!setting.isNightPush()) {
            LocalTime now = LocalTime.now();
            if (now.isAfter(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(8, 0))) {
                // 야간 시간대에 마케팅성 알림은 차단
                if (type == NotificationType.SYSTEM && subType != null &&
                        (subType.equals("WISHLIST_OPEN") || subType.equals("RESTOCK") || subType.equals("CLOSING_SOON"))) {
                    return false;
                }
            }
        }

        // 세부 타입별 허용 체크
        if (type == NotificationType.SHIPPING) {
            return setting.isKakaoDelivery();
        }
        if (type == NotificationType.COMMENT && "INQUIRY".equals(subType)) {
            return setting.isKakaoInquiry();
        }
        if (type == NotificationType.SYSTEM) {
            if ("WINNING".equals(subType)) return setting.isKakaoWinning();
            if ("WISHLIST_OPEN".equals(subType)) return setting.isMarketingOpen();
            if ("RESTOCK".equals(subType)) return setting.isMarketingRestock();
            if ("CLOSING_SOON".equals(subType)) return setting.isMarketingEvent();
        }

        return true;
    }

    /**
     * 내 알림 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long memberId, Pageable pageable) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return notificationRepository.findAllByMemberOrderByReadAscCreatedAtDesc(member, pageable)
                .map(NotificationResponse::from);
    }

    /**
     * 알림 단건 읽음 처리
     */
    public void readNotification(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다."));

        if (!notification.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        notification.markAsRead();
    }

    /**
     * 전체 읽음 처리
     */
    public void readAllNotifications(Long memberId) {
        Member member = memberRepository.getReferenceById(memberId);
        notificationRepository.markAllAsRead(member);
    }
}
