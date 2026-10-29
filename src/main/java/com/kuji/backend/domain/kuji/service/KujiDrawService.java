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
import com.kuji.backend.domain.payment.service.PaymentRecoveryService;
import com.kuji.backend.domain.settlement.service.SettlementService;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.member.repository.PointHistoryRepository;
import com.kuji.backend.domain.notification.entity.NotificationType;
import com.kuji.backend.domain.notification.service.NotificationService;
import com.kuji.backend.domain.notification.service.WishlistNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
    private final PaymentRecoveryService paymentRecoveryService;
    private final SettlementService settlementService;
    private final PaymentRepository paymentRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private void validateSaleAvailable(KujiBoard board) {
        if (board.getStatus() != BoardStatus.ACTIVE) {
            throw new IllegalStateException("현재 판매 중인 쿠지 판이 아닙니다.");
        }
        Member seller = board.getMember();
        if (seller.getBusinessInfo() == null
                || seller.getBusinessInfo().getStatus() != com.kuji.backend.domain.member.enums.BusinessStatus.APPROVED) {
            throw new IllegalStateException("사업자 심사가 승인되지 않은 판매자의 상품입니다.");
        }
    }

    private int calculateRequiredAmount(KujiBoard board, int count) {
        if (count < 1 || board.getPricePerDraw() == null || board.getPricePerDraw() < 1) {
            throw new IllegalArgumentException("뽑기 수량과 상품 가격은 양수여야 합니다.");
        }
        try {
            return Math.toIntExact(Math.multiplyExact(board.getPricePerDraw(), (long) count));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("결제 가능한 금액 범위를 초과했습니다.");
        }
    }

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
        
        validateSaleAvailable(board);
        int totalOriginalAmount = calculateRequiredAmount(board, count);
        List<KujiItem> items = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);
        LastPrizeRules.validateLastPrize(items);
        if (LastPrizeRules.remainingTickets(items) < count) {
            throw new IllegalStateException("요청한 수량보다 남은 일반 티켓이 부족합니다.");
        }
        if (pointsUsed < 0) {
            throw new IllegalArgumentException("사용 포인트는 음수일 수 없습니다.");
        }
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
        if (request.getPaymentType() == PaymentType.PG) {
            paymentRecoveryService.registerAttempt(memberId, request.getPaymentKey(), request.getOrderId(),
                    request.getAmount(), SessionType.KUJI_DRAW, boardId, count);
        }

        // 충전과 같은 순서로 회원을 먼저 잠가 포인트 차감과 적립을 직렬화합니다.
        Member member = memberRepository.findByIdWithLock(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        // 회원, 결제 세션, 쿠지 판 순서로 잠가 중복 승인과 재고 경합을 막습니다.
        PaymentSession session = null;
        if (request.getPaymentType() == PaymentType.PG) {
            if (request.getOrderId() == null) {
                throw new IllegalArgumentException("주문 번호(orderId)가 필요합니다.");
            }
            session = paymentSessionRepository.findByOrderIdWithLock(request.getOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제 세션입니다."));
            if (!session.getMember().getId().equals(memberId) || session.getSessionType() != SessionType.KUJI_DRAW
                    || session.getBoard() == null || !session.getBoard().getId().equals(boardId)
                    || !Integer.valueOf(count).equals(session.getCount()) || !session.getAmount().equals(request.getAmount())) {
                throw new IllegalArgumentException("결제 요청이 준비된 주문과 일치하지 않습니다.");
            }
            // 완료 주문은 판매 종료나 재고 소진과 무관하게 원래 추첨 결과를 재사용합니다.
            com.fasterxml.jackson.databind.JsonNode completed = paymentRecoveryService.completedResult(session,
                    request.getPaymentKey(), request.getAmount());
            if (completed != null) {
                List<KujiItemResponse> results = objectMapper.convertValue(completed.path("results"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<KujiItemResponse>>() {});
                return KujiDrawResponse.builder().results(results)
                        .totalRemaining(completed.path("totalRemaining").intValue()).build();
            }
        }

        KujiBoard board = kujiBoardRepository.findByIdWithLock(boardId)
                .orElseThrow(() -> new IllegalArgumentException("쿠지 판을 찾을 수 없습니다."));
        validateSaleAvailable(board);
        int totalRequiredAmount = calculateRequiredAmount(board, count);
        List<KujiItem> allItems = kujiItemRepository.findAllByKujiBoardIdOrderByGradeAsc(boardId);
        LastPrizeRules.validateLastPrize(allItems);
        long availableCount = LastPrizeRules.remainingTickets(allItems);
        if (availableCount < count) {
            throw new IllegalStateException("요청한 수량보다 남은 일반 티켓이 부족합니다.");
        }

        // 결제 승인은 잠근 재고가 요청 수량을 충족하는 경우에만 진행합니다.
        Payment savedPayment = null;
        int usedPoints = 0;

        if (request.getPaymentType() == PaymentType.POINT) {
            // 포인트 결제: 잔여 포인트 차감 시도
            usedPoints = totalRequiredAmount;
            member.deductPoint(totalRequiredAmount); 
        } else if (request.getPaymentType() == PaymentType.PG) {
            // PG 결제: 잠근 결제 세션이 현재 구매 요청과 일치하는지 확인합니다.
            if (!session.getMember().getId().equals(memberId)
                    || session.getSessionType() != SessionType.KUJI_DRAW
                    || session.getBoard() == null || !session.getBoard().getId().equals(boardId)
                    || !Integer.valueOf(count).equals(session.getCount())) {
                throw new IllegalArgumentException("결제 세션의 구매자, 쿠지 판 또는 수량이 요청과 일치하지 않습니다.");
            }
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
            
            try {
                usedPoints = session.getMetadata() == null ? 0
                        : objectMapper.readTree(session.getMetadata()).path("pointsUsed").asInt(0);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalArgumentException("결제 세션의 포인트 정보를 확인하지 못했습니다.");
            }
            if (usedPoints < 0 || usedPoints > totalRequiredAmount
                    || session.getAmount() != totalRequiredAmount - usedPoints) {
                throw new IllegalArgumentException("결제 세션의 총 결제 금액이 일치하지 않습니다.");
            }
            if (usedPoints > member.getPoint()) {
                throw new IllegalArgumentException("포인트가 부족합니다.");
            }

            // 토스페이먼츠 승인 요청 (실패 시 예외 발생 및 롤백)
            try {
                paymentRecoveryService.confirm(session, request.getPaymentKey(), request.getAmount());
            } catch (Exception e) {
                businessLogger.error("[PAYMENT_FAIL] type=KUJI_DRAW, memberId={}, orderId={}, reason=\"{}\"", memberId, request.getOrderId(), e.getMessage());
                throw e;
            }
            
            if (usedPoints > 0) {
                member.deductPoint(usedPoints);
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
            businessLogger.info("[PAYMENT_SUCCESS] type=KUJI_DRAW, memberId={}, orderId={}, amount={}",
                    memberId, request.getOrderId(), request.getAmount());
        } else {
            throw new IllegalArgumentException("지원하지 않는 결제 방식입니다.");
        }

        // 2. 승인 전 검증한 쿠지 판과 상품 목록으로 추첨합니다.
        List<KujiItem> winningItems = new ArrayList<>();
        List<DrawHistory> drawHistories = new ArrayList<>();
        Random random = new Random();

        // 라스트상은 일반 티켓에 포함하지 않고 마지막 일반 티켓 구매자에게만 추가합니다.
        for (int i = 0; i < count; i++) {
            List<KujiItem> availableItems = allItems.stream()
                    .filter(item -> !LastPrizeRules.isLastGrade(item.getGrade()) && item.getRemainQty() > 0)
                    .toList();
            long totalRemain = LastPrizeRules.remainingTickets(availableItems);
            long selected = random.nextLong(totalRemain) + 1;
            long cumulative = 0;
            for (KujiItem item : availableItems) {
                cumulative += item.getRemainQty();
                if (selected <= cumulative) {
                    item.decreaseRemainQty();
                    winningItems.add(item);
                    break;
                }
            }
        }
        if (LastPrizeRules.remainingTickets(allItems) == 0) {
            allItems.stream().filter(item -> LastPrizeRules.isLastGrade(item.getGrade()) && item.getRemainQty() > 0)
                    .findFirst().ifPresent(item -> {
                        item.decreaseRemainQty();
                        winningItems.add(item);
                    });
        }

        for (KujiItem item : winningItems) {
            DrawHistory history = DrawHistory.builder().status(DrawStatus.DRAWN).member(member)
                    .kujiBoard(board).kujiItem(item).build();
            drawHistories.add(drawHistoryRepository.save(history));
            businessLogger.info("[DRAW_SUCCESS] memberId={}, boardId={}, grade={}, itemName={}",
                    memberId, boardId, item.getGrade(), item.getName());

            String grade = item.getGrade().toUpperCase(java.util.Locale.ROOT);
            if (grade.startsWith("A") || grade.startsWith("B") || grade.startsWith("C") || LastPrizeRules.isLastGrade(grade)) {
                RecentDrawResponse tickerDto = RecentDrawResponse.builder()
                        .maskedNickname(maskNickname(member.getNickname())).boardTitle(board.getTitle())
                        .grade(item.getGrade()).itemName(item.getName()).createdAt(LocalDateTime.now()).build();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { messagingTemplate.convertAndSend("/topic/draw-ticker", tickerDto); }
                        catch (RuntimeException e) { businessLogger.warn("당첨 티커 전송 실패. boardId={}", boardId); }
                    }
                });
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

        settlementService.recordSale(member, board, savedPayment, count, totalRequiredAmount, usedPoints, drawHistories);

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

        int finalTotalRemain = Math.toIntExact(LastPrizeRules.remainingTickets(allItems));

        // 당첨 알림은 구매 트랜잭션에 저장하고 기기 푸시만 알림 서비스가 커밋 후 전송합니다.
        if (!winningItems.isEmpty()) {
            String mainPrizeName = winningItems.get(0).getName();
            String winBody = winningItems.size() > 1
                    ? String.format("축하합니다! [%s] 외 %d건의 상품에 당첨되셨습니다.", mainPrizeName, winningItems.size() - 1)
                    : String.format("축하합니다! [%s] 상품에 당첨되셨습니다.", mainPrizeName);
            notificationService.sendNotification(member, "쿠지 당첨을 축하합니다!", winBody,
                    NotificationType.SYSTEM, "WINNING", boardId.toString());
        }

        // 7. 마감임박 알림 발송 트리거 (잔여 수량이 5개 이하로 떨어졌을 때, 그리고 당첨 전에는 5개 초과였을 때 딱 한 번만 발송)
        int initialTotalRemain = finalTotalRemain + count;
        if (initialTotalRemain > 5 && finalTotalRemain <= 5 && finalTotalRemain > 0) {
            wishlistNotificationService.notifyClosingSoon(board, finalTotalRemain);
        }

        KujiDrawResponse response = KujiDrawResponse.builder()
                .results(resultDtos)
                .totalRemaining(finalTotalRemain)
                .build();
        if (session != null) paymentRecoveryService.complete(session, response);
        return response;
    }

    /**
     * 내 당첨 내역(보관함) 조회
     */
    @Transactional(readOnly = true)
    public List<DrawHistoryResponse> getMyDrawHistory(Long memberId) {
        // fetch join으로 board/item/이미지/배송/판매자 정보를 한 번에 로딩 (행당 5~6쿼리 N+1 제거)
        List<DrawHistory> histories = drawHistoryRepository.findAllByMemberIdWithDetails(memberId);

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
                        .price(h.getKujiBoard().getPricePerDraw())
                        .sellerName(h.getKujiBoard().getMember().getBusinessInfo() != null
                                ? h.getKujiBoard().getMember().getBusinessInfo().getCompanyName()
                                : h.getKujiBoard().getMember().getNickname())
                        .build())
                .toList();
    }

    /**
     * 전역 최근 당첨 내역 조회 (티커용)
     */
    @Transactional(readOnly = true)
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
