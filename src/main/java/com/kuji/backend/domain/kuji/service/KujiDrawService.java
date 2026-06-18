package com.kuji.backend.domain.kuji.service;

import com.kuji.backend.domain.kuji.dto.DrawHistoryResponse;
import com.kuji.backend.domain.kuji.dto.KujiDrawRequest;
import com.kuji.backend.domain.kuji.dto.KujiDrawResponse;
import com.kuji.backend.domain.kuji.dto.KujiItemResponse;
import com.kuji.backend.domain.kuji.dto.PreparePaymentRequest;
import com.kuji.backend.domain.kuji.dto.PreparePaymentResponse;
import com.kuji.backend.domain.kuji.dto.RecentDrawResponse;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.KujiItem;
import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.kuji.enums.BoardStatus;
import com.kuji.backend.domain.kuji.enums.DrawStatus;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.KujiItemRepository;
import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.entity.PointHistory;
import com.kuji.backend.domain.member.enums.PointType;
import com.kuji.backend.domain.payment.entity.Payment;
import com.kuji.backend.domain.payment.entity.PaymentSession;
import com.kuji.backend.domain.payment.enums.PaymentStatus;
import com.kuji.backend.domain.payment.enums.PaymentType;
import com.kuji.backend.domain.payment.enums.SessionStatus;
import com.kuji.backend.domain.payment.enums.SessionType;
import com.kuji.backend.domain.payment.repository.PaymentRepository;
import com.kuji.backend.domain.payment.repository.PaymentSessionRepository;
import com.kuji.backend.global.infra.toss.TossPaymentClient;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.member.repository.PointHistoryRepository;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.service.NotificationService;
import com.kuji.backend.domain.notification.service.WishlistNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class KujiDrawService {

    private static final Logger businessLogger = LoggerFactory.getLogger("BUSINESS_LOGGER");

    private final KujiBoardRepository kujiBoardRepository;
    private final KujiItemRepository kujiItemRepository;
    private final DrawHistoryRepository drawHistoryRepository;
    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final KujiItemService kujiItemService;
    private final NotificationService notificationService;
    private final WishlistNotificationService wishlistNotificationService;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRepository paymentRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * PG 결제 준비 (세션 생성)
     */
    @Transactional
    public PreparePaymentResponse preparePayment(Long memberId, Long boardId, PreparePaymentRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        
        KujiBoard board = kujiBoardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
                
        int count = (request.getCount() != null) ? request.getCount() : 1;
        int pointsUsed = (request.getPointsUsed() != null) ? request.getPointsUsed() : 0;
        
        if (pointsUsed > 0 && member.getPoint() < pointsUsed) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }
        
        int totalOriginalAmount = (int) (board.getPricePerDraw() * count);
        if (pointsUsed > totalOriginalAmount) {
            pointsUsed = totalOriginalAmount;
        }
        
        int amount = totalOriginalAmount - pointsUsed;
        String orderId = "KUJI-" + UUID.randomUUID().toString();
        
        String metadata = String.format(
                "{\"type\":\"KUJI_DRAW\",\"boardId\":%d,\"count\":%d,\"pointsUsed\":%d}",
                boardId, count, pointsUsed);

        PaymentSession session = PaymentSession.builder()
                .member(member)
                .board(board)
                .count(count)
                .amount(amount)
                .orderId(orderId)
                .status(SessionStatus.PENDING)
                .sessionType(SessionType.KUJI_DRAW)
                .metadata(metadata)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
                
        paymentSessionRepository.save(session);
        
        return new PreparePaymentResponse(orderId, amount, board.getTitle());
    }

    /**
     * 무작위 뽑기 실행 (결제 연동 및 비관적 락 적용)
     */
    @Transactional
    public KujiDrawResponse draw(Long memberId, Long boardId, KujiDrawRequest request) {
        int count = (request.getCount() != null) ? request.getCount() : 1;
        
        // 1. 쿠지 판 조회 및 비관적 락 적용
        KujiBoard board = kujiBoardRepository.findByIdWithLock(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));

        if (board.getStatus() != BoardStatus.ACTIVE) {
            throw new IllegalStateException("현재 판매 중인 쿠지 판이 아닙니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        // 1-1. 결제 처리 분기
        int totalRequiredAmount = (int) (board.getPricePerDraw() * count);
        Payment savedPayment = null;
        int usedPoints = 0;

        if (request.getPaymentType() == PaymentType.POINT) {
            // 포인트 결제: 잔여 포인트 차감 시도
            usedPoints = totalRequiredAmount;
            member.deductPoint(totalRequiredAmount); 
        } else if (request.getPaymentType() == PaymentType.PG) {
            // PG 결제: 넘어온 orderId로 결제 세션 검증
            if (request.getOrderId() == null) {
                throw new IllegalArgumentException("주문 번호(orderId)가 필요합니다.");
            }
            
            PaymentSession session = paymentSessionRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제 세션입니다."));
                    
            if (session.getStatus() != SessionStatus.PENDING) {
                throw new IllegalArgumentException("이미 처리되었거나 유효하지 않은 결제 세션입니다.");
            }
            
            if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
                session.updateStatus(SessionStatus.FAILED);
                throw new IllegalArgumentException("결제 유효 시간이 만료되었습니다.");
            }
            
            if (request.getAmount() == null || !request.getAmount().equals(session.getAmount())) {
                throw new IllegalArgumentException("결제 요청 금액이 세션과 일치하지 않습니다.");
            }
            
            // 토스페이먼츠 승인 요청 (실패 시 예외 발생 및 롤백)
            try {
                tossPaymentClient.confirmPayment(request.getPaymentKey(), request.getOrderId(), request.getAmount());
            } catch (Exception e) {
                businessLogger.error("[PAYMENT_FAIL] type=KUJI_DRAW, memberId={}, orderId={}, reason=\"{}\"", memberId, request.getOrderId(), e.getMessage());
                throw e;
            }
            
            // 승인 성공: 세션 상태 변경
            session.updateStatus(SessionStatus.COMPLETED);
            
            // metadata에서 pointsUsed 파싱 및 차감
            if (session.getMetadata() != null && session.getMetadata().contains("\"pointsUsed\":")) {
                try {
                    String md = session.getMetadata();
                    int idx = md.indexOf("\"pointsUsed\":") + 13;
                    int endIdx = md.indexOf("}", idx);
                    usedPoints = Integer.parseInt(md.substring(idx, endIdx).trim());
                    if (usedPoints > 0) {
                        member.deductPoint(usedPoints);
                    }
                } catch (Exception e) {
                    businessLogger.warn("[POINT_DEDUCTION_FAIL] Failed to parse pointsUsed from metadata: {}", session.getMetadata());
                }
            }
            
            // 결제 성공 시 결제(영수증) 내역 저장
            Payment payment = Payment.builder()
                    .member(member)
                    .pguid(request.getPaymentKey())
                    .merchantUid(request.getOrderId())
                    .amount(BigDecimal.valueOf(request.getAmount()))
                    .paymentMethod("TOSS") // 임의 지정
                    .status(PaymentStatus.PAID)
                    .paidAt(OffsetDateTime.now())
                    .build();
            savedPayment = paymentRepository.save(payment);
            businessLogger.info("[PAYMENT_SUCCESS] type=KUJI_DRAW, memberId={}, orderId={}, paymentKey={}, amount={}",
                    memberId, request.getOrderId(), request.getPaymentKey(), request.getAmount());
        } else {
            throw new IllegalArgumentException("지원하지 않는 결제 방식입니다.");
        }

        List<KujiItem> winningItems = new ArrayList<>();
        List<DrawHistory> drawHistories = new ArrayList<>();
        Random random = new Random();

        // 2. 전체 상품 목록 한 번만 조회
        List<KujiItem> allItems = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);

        // 3. 요청한 갯수만큼 반복 추첨
        for (int i = 0; i < count; i++) {
            List<KujiItem> availableItems = allItems.stream()
                    .filter(it -> it.getRemainQty() > 0)
                    .collect(Collectors.toList());

            int totalRemain = availableItems.stream()
                    .mapToInt(KujiItem::getRemainQty)
                    .sum();

            if (totalRemain <= 0) {
                throw new IllegalStateException("남은 상품이 없습니다.");
            }

            // 가중치 기반 랜덤 선택
            int n = random.nextInt(totalRemain) + 1;
            int sum = 0;
            for (KujiItem item : availableItems) {
                sum += item.getRemainQty();
                if (n <= sum) {
                    // 당첨!
                    item.decreaseRemainQty();
                    winningItems.add(item);

                    // 당첨 이력 생성 (drawhistory 테이블 매핑)
                    DrawHistory history = DrawHistory.builder()
                            .status(DrawStatus.DRAWN)
                            .member(member)
                            .kujiBoard(board)
                            .kujiItem(item)
                            .build();
                    DrawHistory savedHistory = drawHistoryRepository.save(history);
                    drawHistories.add(savedHistory);
                    
                    businessLogger.info("[DRAW_SUCCESS] memberId={}, boardId={}, grade={}, itemName={}",
                            memberId, boardId, item.getGrade(), item.getName());

                    // WebSocket 실시간 티커 전송 (A상~C상, Last One)
                    String grade = item.getGrade().toUpperCase();
                    if (grade.startsWith("A") || grade.startsWith("B") || grade.startsWith("C") || grade.contains("LAST")) {
                        RecentDrawResponse tickerDto = RecentDrawResponse.builder()
                                .maskedNickname(maskNickname(member.getNickname()))
                                .boardTitle(board.getTitle())
                                .grade(item.getGrade())
                                .itemName(item.getName())
                                .createdAt(LocalDateTime.now())
                                .build();
                        messagingTemplate.convertAndSend("/topic/draw-ticker", tickerDto);
                    }

                    break;
                }
            }
        }
        
        DrawHistory firstHistory = drawHistories.isEmpty() ? null : drawHistories.get(0);

        // 3-1. 포인트 결제인 경우 '사용' 내역 기록
        if (usedPoints > 0 && firstHistory != null) {
            PointHistory useHistory = PointHistory.builder()
                    .member(member)
                    .drawHistory(firstHistory) // NOT NULL 우회를 위해 첫 번째 추첨내역 연결
                    .amount(usedPoints)
                    .type(PointType.USE)
                    .description(String.format("[%s] %d회 뽑기 포인트 결제", board.getTitle(), count))
                    .appliedRewardRate(0)
                    .build();
            pointHistoryRepository.save(useHistory);
        }

        // 4. 포인트 적립 로직 (rewardRate를 1회당 고정 포인트로 사용)
        if (board.getRewardRate() != null && board.getRewardRate() > 0) {
            int rewardAmount = count * board.getRewardRate();
            if (rewardAmount > 0) {
                member.addPoint(rewardAmount);

                PointHistory pointHistory = PointHistory.builder()
                        .member(member)
                        .drawHistory(firstHistory)
                        .payment(savedPayment) // PG 결제였다면 payment 객체 연결
                        .amount(rewardAmount)
                        .type(PointType.REWARD)
                        .description(
                                String.format("[%s] %d회 뽑기 적립 (%d%%)", board.getTitle(), count, board.getRewardRate()))
                        .appliedRewardRate(board.getRewardRate())
                        .build();
                pointHistoryRepository.save(pointHistory);
            }
        }

        // 5. 결과 반환
        List<KujiItemResponse> resultDtos = new java.util.ArrayList<>();
        for (DrawHistory history : drawHistories) {
            KujiItemResponse dto = kujiItemService.convertToResponse(history.getKujiItem());
            // Since DTO might be immutable, we assume it has @Data or we use a custom
            // setter
            // If it doesn't have a setter, we would need a new builder call
            dto.setDrawHistoryId(history.getId());
            resultDtos.add(dto);
        }

        int finalTotalRemain = allItems.stream()
                .mapToInt(KujiItem::getRemainQty)
                .sum();

        // 6. 당첨 알림 발송
        if (!winningItems.isEmpty()) {
            String mainPrizeName = winningItems.get(0).getName();
            String winBody = winningItems.size() > 1
                    ? String.format("축하합니다! [%s] 외 %d건의 상품에 당첨되셨습니다.", mainPrizeName, winningItems.size() - 1)
                    : String.format("축하합니다! [%s] 상품에 당첨되셨습니다.", mainPrizeName);
            notificationService.sendNotification(
                    member,
                    "개 당첨을 축하합니다!",
                    winBody,
                    NotificationType.SYSTEM,
                    "WINNING",
                    boardId.toString()
            );
        }

        // 7. 마감임박 알림 발송 트리거 (잔여 수량이 5개 이하로 떨어졌을 때, 그리고 당첨 전에는 5개 초과였을 때 딱 한 번만 발송)
        int initialTotalRemain = finalTotalRemain + count;
        if (initialTotalRemain > 5 && finalTotalRemain <= 5 && finalTotalRemain > 0) {
            wishlistNotificationService.notifyClosingSoon(board, finalTotalRemain);
        }

        return KujiDrawResponse.builder()
                .results(resultDtos)
                .totalRemaining(finalTotalRemain)
                .build();
    }

    /**
     * 내 당첨 내역(보관함) 조회
     */
    public List<DrawHistoryResponse> getMyDrawHistory(Long memberId) {
        List<DrawHistory> histories = drawHistoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);

        return histories.stream()
                .map(h -> DrawHistoryResponse.builder()
                        .id(h.getId())
                        .boardTitle(h.getKujiBoard().getTitle())
                        .grade(h.getKujiItem().getGrade())
                        .itemName(h.getKujiItem().getName())
                        .itemImageUrl(h.getKujiItem().getKujiItemImages().isEmpty() ? ""
                                : h.getKujiItem().getKujiItemImages().get(0).getImageUrl())
                        .status(h.getStatus())
                        .createdAt(h.getCreatedAt())
                        .shippingId(h.getShipping() != null ? h.getShipping().getId() : null)
                        .build())
                .toList();
    }

    /**
     * 전역 최근 당첨 내역 조회 (티커용)
     */
    public List<RecentDrawResponse> getRecentDrawHistory() {
        // 최신 20건 중 A, B, C, Last 상만 조회 (티커는 하위 상을 노출하지 않음)
        List<DrawHistory> histories = drawHistoryRepository.findRecentHighGradeDraws(org.springframework.data.domain.PageRequest.of(0, 20));

        return histories.stream()
                .map(h -> RecentDrawResponse.builder()
                        .maskedNickname(maskNickname(h.getMember().getNickname()))
                        .boardTitle(h.getKujiBoard().getTitle())
                        .grade(h.getKujiItem().getGrade())
                        .itemName(h.getKujiItem().getName())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 닉네임 마스킹 (예: 홍길동 -> 홍** / ka**** -> ka***)
     */
    private String maskNickname(String nickname) {
        if (nickname == null || nickname.length() < 2) return "*";
        return nickname.substring(0, 2) + "*".repeat(Math.max(1, nickname.length() - 2));
    }
}
