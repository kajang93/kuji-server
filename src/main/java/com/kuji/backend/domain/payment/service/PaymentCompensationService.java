// 중단된 결제의 복구 의도를 먼저 확정한 뒤 미지급 결제를 반복 취소한다.
package com.kuji.backend.domain.payment.service;

import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.payment.entity.PaymentRecoveryAttempt;
import com.kuji.backend.domain.payment.entity.PaymentSession;
import com.kuji.backend.domain.payment.enums.SessionStatus;
import com.kuji.backend.domain.payment.repository.PaymentRecoveryAttemptRepository;
import com.kuji.backend.domain.payment.repository.PaymentSessionRepository;
import com.kuji.backend.global.infra.portone.PortOnePaymentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class PaymentCompensationService {
    private final MemberRepository members;
    private final PaymentSessionRepository sessions;
    private final PaymentRecoveryAttemptRepository attempts;
    private final PortOnePaymentClient portone;
    private final TransactionTemplate transaction;

    public PaymentCompensationService(MemberRepository members, PaymentSessionRepository sessions,
            PaymentRecoveryAttemptRepository attempts, PortOnePaymentClient portone, PlatformTransactionManager manager) {
        this.members = members; this.sessions = sessions; this.attempts = attempts; this.portone = portone;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recover(String orderId) {
        // 취소 요청이 서버 중단 뒤 늦게 완료되어도 재승인하지 못하도록 복구 상태를 먼저 커밋합니다.
        Boolean claimed = transaction.execute(status -> claim(orderId));
        if (Boolean.TRUE.equals(claimed)) transaction.executeWithoutResult(status -> compensate(orderId));
    }

    private boolean claim(String orderId) {
        Long memberId = attempts.findMemberIdByOrderId(orderId).orElse(null);
        if (memberId == null) return false;
        members.findByIdWithLock(memberId).orElseThrow(() -> new IllegalStateException("복구 대상 회원을 확인하지 못했습니다."));
        PaymentSession session = sessions.findByOrderIdWithLock(orderId).orElseThrow();
        PaymentRecoveryAttempt attempt = attempts.findByOrderIdWithLock(orderId).orElseThrow();
        if (session.getStatus() == SessionStatus.COMPLETED) return false;
        // 포트원 결제창은 앱으로 돌아오기 전에 이미 결제를 끝낸다. 그래서 확인 요청을 받은(OPEN) 주문도
        // 판매 중지·재고 부족 등으로 처리되지 못했다면 결제가 남아 있을 수 있어, 포트원 조회로 확인한 뒤 취소한다.
        if (attempt.getState() != PaymentRecoveryAttempt.State.OPEN && attempt.getState() != PaymentRecoveryAttempt.State.SUBMITTING
                && attempt.getState() != PaymentRecoveryAttempt.State.RECOVERING) return false;
        attempt.startRecovery();
        return true;
    }

    private void compensate(String orderId) {
        Long memberId = attempts.findMemberIdByOrderId(orderId).orElseThrow();
        members.findByIdWithLock(memberId).orElseThrow();
        PaymentSession session = sessions.findByOrderIdWithLock(orderId).orElseThrow();
        PaymentRecoveryAttempt attempt = attempts.findByOrderIdWithLock(orderId).orElseThrow();
        if (session.getStatus() == SessionStatus.COMPLETED) return;
        if (attempt.getState() != PaymentRecoveryAttempt.State.RECOVERING) return;
        try {
            var payment = portone.findPayment(attempt.getPaymentKey(), orderId, attempt.getAmount());
            if (payment.isEmpty()) {
                // 조회 반영이 늦는 경우에도 나중에 승인된 결제를 놓치지 않도록 기록을 유지하되,
                // 30분이 지나도 결제가 없으면 결제되지 않은 주문으로 닫습니다.
                if (attempt.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(30))) {
                    attempt.fail();
                    session.updateStatus(SessionStatus.FAILED);
                } else {
                    attempt.retryLater();
                }
                return;
            }
            if (payment.get().isFullyCancelled()) {
                attempt.cancel();
                session.updateStatus(SessionStatus.FAILED);
            } else if (payment.get().isUnpaidTerminal()) {
                attempt.fail();
                session.updateStatus(SessionStatus.FAILED);
            } else if (payment.get().canCompensate()) {
                portone.cancelPayment(attempt.getPaymentKey(), orderId, attempt.getAmount());
                attempt.cancel();
                session.updateStatus(SessionStatus.FAILED);
            } else {
                attempt.retryLater();
            }
        } catch (RuntimeException e) {
            attempt.retryLater();
            // 결제 키와 PG 원문은 로그에 남기지 않습니다.
            log.warn("결제 보상 취소 재시도 필요. orderId={}", orderId);
        }
    }
}
