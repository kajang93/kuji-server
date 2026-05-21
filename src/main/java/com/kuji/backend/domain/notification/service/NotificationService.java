package com.kuji.backend.domain.notification.service;

import com.google.firebase.messaging.*;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.dto.NotificationResponse;
import com.kuji.backend.domain.notification.dto.TokenRequest;
import com.kuji.backend.domain.notification.entity.DeviceToken;
import com.kuji.backend.domain.notification.entity.Notification;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.repository.DeviceTokenRepository;
import com.kuji.backend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 알림 발송 (FCM + DB 저장)
     */
    public void sendNotification(Member receiver, String title, String body, NotificationType type, String targetId) {
        // 1. DB에 알림 저장
        Notification notification = Notification.builder()
                .member(receiver)
                .title(title)
                .body(body)
                .type(type)
                .targetId(targetId)
                .build();
        notificationRepository.save(notification);

        // 2. 해당 유저의 유효한 모든 디바이스 토큰 조회
        List<DeviceToken> deviceTokens = deviceTokenRepository.findAllByMember(receiver);
        if (deviceTokens.isEmpty()) {
            log.info("발송 가능한 기기 토큰이 없습니다. memberId={}", receiver.getId());
            return;
        }

        List<String> tokens = deviceTokens.stream()
                .map(DeviceToken::getToken)
                .collect(Collectors.toList());

        // 3. FCM 메시지 구성
        MulticastMessage message = MulticastMessage.builder()
                .putData("title", title)
                .putData("body", body)
                .putData("type", type.name())
                .putData("targetId", targetId != null ? targetId : "")
                .addAllTokens(tokens)
                // iOS 등 백그라운드 처리를 위한 Notification 객체 추가 (선택사항)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        // 4. FCM 발송
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            
            // 5. 유효하지 않은 토큰(앱 삭제 등) 자동 정리
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
