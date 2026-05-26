package com.kuji.backend.domain.shipping.service;

import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.service.NotificationService;
import com.kuji.backend.domain.shipping.dto.ShippingRequest;
import com.kuji.backend.domain.shipping.dto.ShippingResponse;
import com.kuji.backend.domain.shipping.entity.Shipping;
import com.kuji.backend.domain.shipping.enums.ShippingStatus;
import com.kuji.backend.domain.shipping.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingService {

    private final ShippingRepository shippingRepository;
    private final DrawHistoryRepository drawHistoryRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    /**
     * 배송 신청 처리
     */
    @Transactional
    public Long requestShipping(Long memberId, ShippingRequest request) {
        // 1. 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 2. 당첨 이력 조회 및 검증
        List<DrawHistory> drawHistories = drawHistoryRepository.findAllById(request.drawHistoryIds());
        
        if (drawHistories.isEmpty()) {
            throw new IllegalArgumentException("배송 신청할 상품이 없습니다.");
        }

        // 3. 배송 정보(Shipping) 생성
        Shipping shipping = Shipping.builder()
                .member(member)
                .recipientName(request.recipientName())
                .phone(request.phone())
                .zipcode(request.zipcode())
                .address(request.address())
                .detailAddress(request.detailAddress())
                .deliveryMessage(request.deliveryMessage())
                .build();

        Shipping savedShipping = shippingRepository.save(shipping);

        // 4. 각 당첨 이력에 배송 정보 연결
        for (DrawHistory history : drawHistories) {
            // 본인 확인 (보안)
            if (!history.getMember().getId().equals(memberId)) {
                throw new IllegalArgumentException("본인의 당첨 상품만 배송 신청할 수 있습니다.");
            }
            // 이미 배송 신청된 건인지 확인
            if (history.getShipping() != null) {
                throw new IllegalArgumentException("이미 배송 신청이 완료된 상품이 포함되어 있습니다. (ID: " + history.getId() + ")");
            }
            
            history.setShipping(savedShipping);
        }

        // 배송 신청 완료 알림 발송
        notificationService.sendNotification(
                member,
                "배송 신청 완료",
                "신청하신 상품의 배송 준비가 시작됩니다.",
                NotificationType.SHIPPING,
                "DELIVERY_REQUEST",
                savedShipping.getId().toString()
        );

        return savedShipping.getId();
    }

    /**
     * 내 배송 목록 조회
     */
    public List<ShippingResponse> getMyShippingList(Long memberId) {
        List<Shipping> shippings = shippingRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
        
        return shippings.stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 전체 배송 목록 조회 (관리자용)
     */
    public List<ShippingResponse> getAllShippingList() {
        List<Shipping> shippings = shippingRepository.findAll();
        return shippings.stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 사업자용 배송 목록 조회
     */
    public List<ShippingResponse> getSellerShippingList(Long sellerId) {
        List<Shipping> shippings = shippingRepository.findAllBySellerId(sellerId);
        return shippings.stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 운송장 번호 등록 및 배송 시작
     */
    @Transactional
    public void updateTrackingInfo(Long shippingId, String courierName, String trackingNumber) {
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배송 정보입니다."));

        shipping.startShipping(courierName, trackingNumber);

        // 연관된 모든 당첨 이력의 상태도 SHIPPING(배송 중)으로 업데이트
        List<DrawHistory> drawHistories = drawHistoryRepository.findAllByShipping(shipping);
        for (DrawHistory history : drawHistories) {
            history.startShipping();
        }

        // 배송 출발 알림 발송
        notificationService.sendNotification(
                shipping.getMember(),
                "배송 시작 안내",
                String.format("[%s] 배송이 시작되었습니다. 운송장: %s (%s)",
                        shipping.getRecipientName(), trackingNumber, courierName),
                NotificationType.SHIPPING,
                "DELIVERY_START",
                shippingId.toString()
        );
    }

    /**
     * 배송 완료 처리
     */
    @Transactional
    public void completeShipping(Long shippingId) {
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배송 정보입니다."));

        if (shipping.getTrackingNumber() == null || shipping.getTrackingNumber().trim().isEmpty()) {
            throw new IllegalStateException("운송장 번호가 등록되지 않은 배송 건은 완료 처리할 수 없습니다.");
        }

        shipping.updateStatus(ShippingStatus.DELIVERED);

        // 연관된 모든 당첨 이력의 상태도 DELIVERED(수령 완료)로 업데이트
        List<DrawHistory> drawHistories = drawHistoryRepository.findAllByShipping(shipping);
        for (DrawHistory history : drawHistories) {
            history.completeDelivery();
        }

        // 배송 완료 알림 발송
        notificationService.sendNotification(
                shipping.getMember(),
                "배송 완료 안내",
                String.format("[%s] 상품 배송이 완료되었습니다. 이용해 주셔서 감사합니다.",
                        shipping.getRecipientName()),
                NotificationType.SHIPPING,
                "DELIVERY_COMPLETE",
                shippingId.toString()
        );
    }

    private ShippingResponse convertToResponse(Shipping shipping) {
        List<DrawHistory> histories = drawHistoryRepository.findAllByShipping(shipping);
        List<ShippingResponse.ShippedItemResponse> items = histories.stream()
                .map(h -> new ShippingResponse.ShippedItemResponse(
                        h.getId(),
                        h.getKujiBoard().getTitle(),
                        h.getKujiItem().getGrade(),
                        h.getKujiItem().getName(),
                        h.getKujiItem().getKujiItemImages().isEmpty() ? "" : h.getKujiItem().getKujiItemImages().get(0).getImageUrl()
                ))
                .toList();
        return ShippingResponse.from(shipping, items);
    }
}
