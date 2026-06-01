package com.kuji.backend.domain.payment.service;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.entity.PointHistory;
import com.kuji.backend.domain.member.enums.PointType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.member.repository.PointHistoryRepository;
import com.kuji.backend.domain.payment.dto.ChargeConfirmRequest;
import com.kuji.backend.domain.payment.dto.ChargeConfirmResponse;
import com.kuji.backend.domain.payment.dto.ChargePrepareRequest;
import com.kuji.backend.domain.payment.dto.ChargePrepareResponse;
import com.kuji.backend.domain.payment.dto.PointHistoryResponse;
import com.kuji.backend.domain.payment.entity.Payment;
import com.kuji.backend.domain.payment.entity.PaymentSession;
import com.kuji.backend.domain.payment.enums.PaymentStatus;
import com.kuji.backend.domain.payment.enums.SessionStatus;
import com.kuji.backend.domain.payment.enums.SessionType;
import com.kuji.backend.domain.payment.repository.PaymentRepository;
import com.kuji.backend.domain.payment.repository.PaymentSessionRepository;
import com.kuji.backend.global.infra.toss.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PointService {

    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

    /**
     * 포인트 충전 준비 (결제 세션 생성)
     * - orderId를 발급하고 결제 대기열에 등록합니다.
     */
    public ChargePrepareResponse prepareCharge(Long memberId, ChargePrepareRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (request.getAmount() == null || request.getAmount() < 1000) {
            throw new IllegalArgumentException("최소 충전 금액은 1,000원입니다.");
        }

        String orderId = "CHARGE-" + UUID.randomUUID().toString();

        PaymentSession session = PaymentSession.builder()
                .member(member)
                .board(null)   // 포인트 충전이므로 쿠지 판 없음
                .count(null)   // 포인트 충전이므로 뽑기 횟수 없음
                .amount(request.getAmount())
                .orderId(orderId)
                .status(SessionStatus.PENDING)
                .sessionType(SessionType.POINT_CHARGE)
                .metadata(String.format("{\"type\":\"POINT_CHARGE\",\"bonusPoints\":0}"))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        paymentSessionRepository.save(session);

        log.info("[포인트 충전 준비] memberId={}, orderId={}, amount={}", memberId, orderId, request.getAmount());

        return new ChargePrepareResponse(orderId, request.getAmount());
    }

    /**
     * 포인트 충전 승인 (토스 결제 확인 + 포인트 지급)
     * - 토스페이먼츠 승인 API를 호출하고, 성공 시 회원의 포인트를 증가시킵니다.
     */
    public ChargeConfirmResponse confirmCharge(Long memberId, ChargeConfirmRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 1. 세션 검증
        PaymentSession session = paymentSessionRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제 세션입니다."));

        if (!session.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("결제 세션의 소유자가 아닙니다.");
        }

        if (session.getStatus() != SessionStatus.PENDING) {
            throw new IllegalArgumentException("이미 처리된 결제 세션입니다.");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.updateStatus(SessionStatus.EXPIRED);
            throw new IllegalArgumentException("결제 세션이 만료되었습니다. 다시 시도해주세요.");
        }

        if (!session.getAmount().equals(request.getAmount())) {
            throw new IllegalArgumentException("결제 요청 금액이 세션과 일치하지 않습니다.");
        }

        if (session.getSessionType() != SessionType.POINT_CHARGE) {
            throw new IllegalArgumentException("포인트 충전 세션이 아닙니다.");
        }

        // 2. 토스페이먼츠 승인 요청
        tossPaymentClient.confirmPayment(request.getPaymentKey(), request.getOrderId(), request.getAmount());

        // 3. 세션 상태 완료 처리
        session.updateStatus(SessionStatus.COMPLETED);

        // 4. 결제 영수증 저장
        Payment payment = Payment.builder()
                .member(member)
                .pguid(request.getPaymentKey())
                .merchantUid(request.getOrderId())
                .amount(BigDecimal.valueOf(request.getAmount()))
                .paymentMethod("TOSS")
                .status(PaymentStatus.PAID)
                .paidAt(OffsetDateTime.now())
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        // 5. 회원 포인트 증가 (1원 = 1포인트)
        member.addPoint(request.getAmount());

        // 6. 포인트 내역 기록
        PointHistory pointHistory = PointHistory.builder()
                .member(member)
                .payment(savedPayment)
                .amount(request.getAmount())
                .type(PointType.CHARGE)
                .description(String.format("포인트 충전 (%,d원)", request.getAmount()))
                .appliedRewardRate(0)
                .build();
        pointHistoryRepository.save(pointHistory);

        log.info("[포인트 충전 완료] memberId={}, orderId={}, amount={}, newBalance={}",
                memberId, request.getOrderId(), request.getAmount(), member.getPoint());

        return new ChargeConfirmResponse(request.getAmount(), member.getPoint());
    }

    /**
     * 포인트 사용/충전 내역 조회
     * - 최신순으로 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<PointHistoryResponse> getPointHistory(Long memberId) {
        return pointHistoryRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(PointHistoryResponse::new)
                .collect(Collectors.toList());
    }
}
