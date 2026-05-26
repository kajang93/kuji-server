package com.kuji.backend.domain.shipping.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiItem;
import com.kuji.backend.domain.kuji.enums.BoardStatus;
import com.kuji.backend.domain.kuji.enums.DrawStatus;
import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.notification.entity.Notification;
import com.kuji.backend.domain.notification.repository.NotificationRepository;
import com.kuji.backend.domain.shipping.dto.ShippingRequest;
import com.kuji.backend.domain.shipping.entity.Shipping;
import com.kuji.backend.domain.shipping.enums.ShippingStatus;
import com.kuji.backend.domain.shipping.repository.ShippingRepository;
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
class ShippingServiceTest {

    @Autowired
    private ShippingService shippingService;

    @Autowired
    private ShippingRepository shippingRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private KujiBoardRepository kujiBoardRepository;

    @Autowired
    private KujiItemRepository kujiItemRepository;

    @Autowired
    private DrawHistoryRepository drawHistoryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Member customer;
    private Member seller;
    private KujiBoard board;
    private KujiItem item;
    private DrawHistory drawHistory;

    @BeforeEach
    void setUp() {
        customer = Member.builder()
                .email("customer@test.com")
                .nickname("구매자")
                .role(RoleType.USER)
                .socialType(SocialType.LOCAL)
                .isTermsAgreed(true)
                .isPrivacyAgreed(true)
                .isMarketingAgreed(false)
                .build();
        memberRepository.save(customer);

        seller = Member.builder()
                .email("seller@test.com")
                .nickname("판매자")
                .role(RoleType.BIZ)
                .socialType(SocialType.LOCAL)
                .isTermsAgreed(true)
                .isPrivacyAgreed(true)
                .isMarketingAgreed(false)
                .build();
        memberRepository.save(seller);

        board = KujiBoard.builder()
                .title("원피스 피규어 쿠지")
                .pricePerDraw(10000L)
                .status(BoardStatus.ACTIVE)
                .member(seller)
                .build();
        kujiBoardRepository.save(board);

        item = KujiItem.builder()
                .grade("A")
                .name("루피 피규어")
                .totalQty(10)
                .kujiBoard(board)
                .build();
        kujiItemRepository.save(item);

        drawHistory = DrawHistory.builder()
                .status(DrawStatus.DRAWN)
                .member(customer)
                .kujiBoard(board)
                .kujiItem(item)
                .build();
        drawHistoryRepository.save(drawHistory);
    }

    @Test
    @DisplayName("배송 신청, 배송 시작(운송장 등록) 및 배송 완료 라이프사이클 테스트")
    void shippingLifeCycleTest() throws Exception {
        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

            BatchResponse mockBatchResponse = mock(BatchResponse.class);
            when(mockMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

            // 1. 배송 신청
            ShippingRequest request = new ShippingRequest(
                    List.of(drawHistory.getId()),
                    "홍길동",
                    "010-1234-5678",
                    "12345",
                    "서울시 강남구",
                    "101호",
                    "문 앞에 놔주세요"
            );

            Long shippingId = shippingService.requestShipping(customer.getId(), request);
            assertThat(shippingId).isNotNull();

            // 배송 신청 정보 검증
            Shipping shipping = shippingRepository.findById(shippingId).orElseThrow();
            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
            assertThat(shipping.getRecipientName()).isEqualTo("홍길동");
            assertThat(shipping.getPhone()).isEqualTo("010-1234-5678");

            // 당첨 이력 상태 및 매핑 검증
            DrawHistory updatedHistory = drawHistoryRepository.findById(drawHistory.getId()).orElseThrow();
            assertThat(updatedHistory.getStatus()).isEqualTo(DrawStatus.SHIPPING_REQUESTED);
            assertThat(updatedHistory.getShipping().getId()).isEqualTo(shippingId);

            // 알림 저장 검증 (배송 신청 완료)
            List<Notification> allNotifications = notificationRepository.findAll();
            assertThat(allNotifications).isNotEmpty();
            boolean hasRequestNotification = allNotifications.stream()
                    .anyMatch(n -> n.getMember().getId().equals(customer.getId()) && n.getTitle().equals("배송 신청 완료"));
            assertThat(hasRequestNotification).isTrue();

            // 2. 배송 시작 (운송장 등록)
            shippingService.updateTrackingInfo(shippingId, "대한통운", "123456789");

            // 배송 시작 상태 검증
            shipping = shippingRepository.findById(shippingId).orElseThrow();
            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPING);
            assertThat(shipping.getTrackingNumber()).isEqualTo("123456789");
            assertThat(shipping.getCourierName()).isEqualTo("대한통운");

            // 당첨 이력 상태 배송 중 검증
            updatedHistory = drawHistoryRepository.findById(drawHistory.getId()).orElseThrow();
            assertThat(updatedHistory.getStatus()).isEqualTo(DrawStatus.SHIPPING);

            // 알림 저장 검증 (배송 시작 안내)
            allNotifications = notificationRepository.findAll();
            boolean hasStartNotification = allNotifications.stream()
                    .anyMatch(n -> n.getMember().getId().equals(customer.getId()) 
                            && n.getTitle().equals("배송 시작 안내") 
                            && n.getBody().contains("123456789"));
            assertThat(hasStartNotification).isTrue();

            // 3. 배송 완료
            shippingService.completeShipping(shippingId);

            // 배송 완료 상태 검증
            shipping = shippingRepository.findById(shippingId).orElseThrow();
            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);

            // 당첨 이력 상태 배송 완료 검증
            updatedHistory = drawHistoryRepository.findById(drawHistory.getId()).orElseThrow();
            assertThat(updatedHistory.getStatus()).isEqualTo(DrawStatus.DELIVERED);

            // 알림 저장 검증 (배송 완료 안내)
            allNotifications = notificationRepository.findAll();
            boolean hasCompleteNotification = allNotifications.stream()
                    .anyMatch(n -> n.getMember().getId().equals(customer.getId()) 
                            && n.getTitle().equals("배송 완료 안내") 
                            && n.getBody().contains("상품 배송이 완료되었습니다"));
            assertThat(hasCompleteNotification).isTrue();
        }
    }
}
