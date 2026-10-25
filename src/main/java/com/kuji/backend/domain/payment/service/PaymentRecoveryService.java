// 승인 전 내구 기록과 완료 결과 재사용 및 소유자별 결제 상태를 관리한다.
package com.kuji.backend.domain.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.payment.entity.PaymentRecoveryAttempt;
import com.kuji.backend.domain.payment.entity.PaymentSession;
import com.kuji.backend.domain.payment.enums.SessionStatus;
import com.kuji.backend.domain.payment.enums.SessionType;
import com.kuji.backend.domain.payment.repository.PaymentRecoveryAttemptRepository;
import com.kuji.backend.domain.payment.repository.PaymentSessionRepository;
import com.kuji.backend.global.infra.toss.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryService {
    private final MemberRepository members;
    private final PaymentSessionRepository sessions;
    private final PaymentRecoveryAttemptRepository attempts;
    private final PaymentCompensationService compensation;
    private final PaymentSubmissionService submission;
    private final TossPaymentClient toss;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerAttempt(Long memberId, String paymentKey, String orderId, Integer amount,
            SessionType type, Long boardId, Integer count) {
        TossPaymentClient.validateRequest(paymentKey, orderId, amount);
        members.findByIdWithLock(memberId).orElseThrow(() -> new IllegalArgumentException("결제 정보를 확인할 수 없습니다."));
        PaymentSession session = sessions.findByOrderIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 확인할 수 없습니다."));
        if (!session.getMember().getId().equals(memberId) || !session.getAmount().equals(amount) || session.getSessionType() != type
                || (type == SessionType.KUJI_DRAW && (session.getBoard() == null || !session.getBoard().getId().equals(boardId)
                || !session.getCount().equals(count)))) throw new IllegalArgumentException("결제 요청이 준비된 주문과 일치하지 않습니다.");
        PaymentRecoveryAttempt existing = attempts.findByOrderIdWithLock(orderId).orElse(null);
        if (existing != null) verifyAttempt(existing, session, paymentKey, amount);
        // 이전 버전에서 이미 완료한 주문에 새 복구 의도를 만들지 않습니다.
        if (session.getStatus() == SessionStatus.COMPLETED) return;
        if (session.getStatus() != SessionStatus.PENDING || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("이미 처리되었거나 만료된 결제입니다.");
        }
        if (existing == null) attempts.saveAndFlush(new PaymentRecoveryAttempt(orderId, memberId, paymentKey, amount, type));
        else if (existing.getState() != PaymentRecoveryAttempt.State.OPEN) throw new IllegalStateException("결제를 복구하고 있습니다. 결제 상태를 확인해주세요.");
    }

    public JsonNode completedResult(PaymentSession session, String paymentKey, Integer amount) {
        if (session.getStatus() != SessionStatus.COMPLETED) return null;
        PaymentRecoveryAttempt attempt = attempts.findByOrderIdWithLock(session.getOrderId()).orElse(null);
        if (attempt != null) verifyAttempt(attempt, session, paymentKey, amount);
        if (attempt == null || attempt.getState() != PaymentRecoveryAttempt.State.COMPLETED || attempt.getResultJson() == null) {
            throw new IllegalStateException("이미 완료된 결제입니다. 결제 내역에서 결과를 확인해주세요.");
        }
        try { return mapper.readTree(attempt.getResultJson()); }
        catch (Exception e) { throw new IllegalStateException("완료된 결제 결과를 확인하지 못했습니다. 결제 내역을 확인해주세요."); }
    }

    public void confirm(PaymentSession session, String paymentKey, Integer amount) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("결제 트랜잭션이 필요합니다.");
        if (session.getStatus() != SessionStatus.PENDING) throw new IllegalStateException("이미 처리된 결제입니다.");
        submission.start(session.getOrderId(), session.getMember().getId(), paymentKey, amount, session.getSessionType());
        PaymentRecoveryAttempt attempt = attempts.findByOrderIdWithLock(session.getOrderId())
                .orElseThrow(() -> new IllegalStateException("결제 복구 기록이 없어 승인을 진행하지 않았습니다."));
        verifyAttempt(attempt, session, paymentKey, amount);
        if (attempt.getState() != PaymentRecoveryAttempt.State.SUBMITTING || session.getStatus() != SessionStatus.PENDING) {
            throw new IllegalStateException("이미 처리 중인 결제입니다. 결제 상태를 확인해주세요.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { compensation.recover(session.getOrderId()); }
                    catch (RuntimeException e) { log.warn("결제 복구가 예약된 상태입니다. orderId={}", session.getOrderId()); }
                }
            }
        });
        toss.confirmPayment(paymentKey, session.getOrderId(), amount);
    }

    public void complete(PaymentSession session, Object response) {
        PaymentRecoveryAttempt attempt = attempts.findByOrderIdWithLock(session.getOrderId()).orElseThrow();
        if (attempt.getState() != PaymentRecoveryAttempt.State.SUBMITTING) throw new IllegalStateException("복구 중인 결제는 완료할 수 없습니다.");
        try { attempt.complete(mapper.writeValueAsString(response)); }
        catch (Exception e) { throw new IllegalStateException("결제 결과를 저장하지 못했습니다."); }
        session.updateStatus(SessionStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public String getStatus(Long memberId, String orderId) {
        PaymentSession session = sessions.findByOrderId(orderId)
                .filter(s -> s.getMember().getId().equals(memberId))
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 확인할 수 없습니다."));
        if (session.getStatus() == SessionStatus.COMPLETED) return "COMPLETED";
        PaymentRecoveryAttempt attempt = attempts.findByOrderId(orderId).orElse(null);
        if (attempt != null) {
            return switch (attempt.getState()) {
                case CANCELLED -> "CANCELLED";
                case FAILED -> "FAILED";
                default -> "RECOVERING";
            };
        }
        return session.getStatus() == SessionStatus.PENDING && !session.getExpiresAt().isBefore(LocalDateTime.now()) ? "PENDING" : "FAILED";
    }

    private void verifyAttempt(PaymentRecoveryAttempt attempt, PaymentSession session, String paymentKey, Integer amount) {
        if (!attempt.getMemberId().equals(session.getMember().getId()) || !attempt.getPaymentKey().equals(paymentKey)
                || !attempt.getAmount().equals(amount) || attempt.getSessionType() != session.getSessionType()) {
            throw new IllegalArgumentException("기존 결제 요청과 다른 승인 정보입니다.");
        }
    }
}
