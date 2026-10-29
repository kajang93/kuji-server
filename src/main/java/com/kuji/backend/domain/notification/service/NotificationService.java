package com.kuji.backend.domain.notification.service;

import com.google.firebase.FirebaseApp;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final MemberRepository memberRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 기기 토큰 등록 또는 갱신
     */
    public void registerToken(Long memberId, TokenRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        String deviceId = request.getDeviceId();
        String token = request.getToken();
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 100
                || token == null || token.isBlank() || token.length() > 500) {
            throw new IllegalArgumentException("기기 알림 정보를 확인해 주세요.");
        }
        // 이전 계정에 남은 같은 기기 또는 같은 푸시 주소 연결을 원자적으로 교체합니다.
        java.util.stream.Stream.of("device:" + deviceId, "token:" + token).sorted()
                .forEach(deviceTokenRepository::lockIdentity);
        deviceTokenRepository.deleteOtherBindings(deviceId, token, memberId);
        deviceTokenRepository.findByDeviceIdAndMemberId(deviceId, memberId)
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
     * 거래 알림은 앱 안에 기록하고, 기기 푸시는 거래 커밋 후 발송합니다.
     */
    public void sendNotification(Member receiver, String title, String body, NotificationType type, String targetId) {
        sendNotification(receiver, title, body, type, null, targetId);
    }

    public void sendNotification(Member receiver, String title, String body, NotificationType type, String subType, String targetId) {
        NotificationSetting setting = settingRepository.findByMember(receiver).orElse(null);
        if (isMarketing(type, subType) && !hasMarketingConsent(receiver, setting, subType)) {
            return;
        }

        Notification notification = Notification.builder()
                .member(receiver)
                .title(title)
                .body(body)
                .type(type)
                .targetId(targetId)
                .build();
        notificationRepository.save(notification);
        log.info("[인앱 알림 저장] notificationId={}, memberId={}, type={}, subType={}",
                notification.getId(), receiver.getId(), type, subType);

        Long receiverId = receiver.getId();
        Runnable delivery = () -> deliverAfterCommit(receiverId, title, body, type, subType, targetId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
        } else {
            delivery.run();
        }
    }

    private void deliverAfterCommit(Long receiverId, String title, String body, NotificationType type, String subType, String targetId) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> memberRepository.findById(receiverId)
                    .ifPresent(receiver -> sendDevicePush(receiver, title, body, type, subType, targetId)));
        } catch (RuntimeException e) {
            // 푸시 실패가 이미 완료된 결제·배송 처리 결과를 바꾸지 않게 합니다.
            log.error("[FCM 푸시 처리 실패] memberId={}, type={}, subType={}, errorType={}",
                    receiverId, type, subType, e.getClass().getSimpleName());
        }
    }

    private void sendDevicePush(Member receiver, String title, String body, NotificationType type, String subType, String targetId) {
        NotificationSetting setting = settingRepository.findByMember(receiver).orElse(null);
        if (!allowsPush(receiver, setting, type, subType, LocalTime.now(ZoneId.of("Asia/Seoul")))) {
            return;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[FCM 푸시 미발송] Firebase 설정 없음. memberId={}", receiver.getId());
            return;
        }

        List<String> tokens = deviceTokenRepository.findAllByMember(receiver).stream()
                .map(DeviceToken::getToken).distinct().toList();
        for (int offset = 0; offset < tokens.size(); offset += 500) {
            List<String> batch = tokens.subList(offset, Math.min(offset + 500, tokens.size()));
            MulticastMessage message = MulticastMessage.builder()
                    .putData("title", title)
                    .putData("body", body)
                    .putData("type", type.name())
                    .putData("subType", subType != null ? subType : "")
                    .putData("targetId", targetId != null ? targetId : "")
                    .addAllTokens(batch)
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(title).setBody(body).build())
                    .build();
            try {
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                log.info("[FCM 푸시 결과] memberId={}, type={}, subType={}, tokenCount={}, successCount={}, failureCount={}",
                        receiver.getId(), type, subType, batch.size(), response.getSuccessCount(), response.getFailureCount());
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful() && responses.get(i).getException() != null) {
                        MessagingErrorCode code = responses.get(i).getException().getMessagingErrorCode();
                        if (code == MessagingErrorCode.UNREGISTERED) {
                            deviceTokenRepository.deleteByToken(batch.get(i));
                        }
                        log.warn("[FCM 개별 실패] memberId={}, errorCode={}", receiver.getId(), code);
                    }
                }
            } catch (FirebaseMessagingException | RuntimeException e) {
                log.error("[FCM 발송 실패] memberId={}, type={}, errorType={}",
                        receiver.getId(), type, e.getClass().getSimpleName());
            }
        }
    }

    static boolean isMarketing(NotificationType type, String subType) {
        return type == NotificationType.SYSTEM && ("WISHLIST_OPEN".equals(subType)
                || "RESTOCK".equals(subType) || "CLOSING_SOON".equals(subType));
    }

    private static boolean hasMarketingConsent(Member receiver, NotificationSetting setting, String subType) {
        if (!Boolean.TRUE.equals(receiver.getIsMarketingAgreed()) || setting == null) return false;
        return switch (subType) {
            case "WISHLIST_OPEN" -> setting.isMarketingOpen();
            case "RESTOCK" -> setting.isMarketingRestock();
            case "CLOSING_SOON" -> setting.isMarketingEvent();
            default -> false;
        };
    }

    static boolean allowsPush(Member receiver, NotificationSetting setting, NotificationType type, String subType, LocalTime now) {
        if (setting != null && !setting.isPushEnabled()) return false;
        if (isMarketing(type, subType)) {
            if (!hasMarketingConsent(receiver, setting, subType)) return false;
            boolean night = !now.isBefore(LocalTime.of(21, 0)) || now.isBefore(LocalTime.of(8, 0));
            return !night || setting.isNightPush();
        }
        if (setting == null) return true;
        if ("BIZ_ORDER".equals(subType)) return setting.isKakaoBizOrder();
        if ("BIZ_CANCEL".equals(subType)) return setting.isKakaoBizCancel();
        if ("BIZ_INQUIRY".equals(subType)) return setting.isKakaoBizInquiry();
        if (type == NotificationType.SHIPPING) return setting.isKakaoDelivery();
        if (type == NotificationType.COMMENT && "INQUIRY".equals(subType)) return setting.isKakaoInquiry();
        if (type == NotificationType.SYSTEM && "WINNING".equals(subType)) return setting.isKakaoWinning();
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
