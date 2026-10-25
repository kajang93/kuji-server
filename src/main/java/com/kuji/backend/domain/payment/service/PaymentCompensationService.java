// 중단된 결제의 복구 의도를 먼저 확정한 뒤 미지급 결제를 반복 취소한다.
package com.kuji.backend.domain.payment.service;

import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.payment.entity.PaymentRecoveryAttempt;
import com.kuji.backend.domain.payment.entity.PaymentSession;
import com.kuji.backend.domain.payment.enums.SessionStatus;
import com.kuji.backend.domain.payment.repository.PaymentRecoveryAttemptRepository;
import com.kuji.backend.domain.payment.repository.PaymentSessionRepository;
import com.kuji.backend.global.infra.toss.TossPaymentClient;
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
    private final TossPaymentClient toss;
    private final TransactionTemplate transaction;

    public PaymentCompensationService(MemberRepository members, PaymentSessionRepository sessions,
            PaymentRecoveryAttemptRepository attempts, TossPaymentClient toss, PlatformTransactionManager manager) {
        this.members = members; this.sessions = sessions; this.attempts = attempts; this.toss = toss;
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
        if (attempt.getState() == PaymentRecoveryAttempt.State.OPEN) {
            // 전송 시작 표식이 없으면 PG 요청이 없었으므로 외부 조회 없이 종료할 수 있습니다.
            attempt.fail();
            session.updateStatus(SessionStatus.FAILED);
            return false;
        }
        if (attempt.getState() != PaymentRecoveryAttempt.State.SUBMITTING && attempt.getState() != PaymentRecoveryAttempt.State.RECOVERING) return false;
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
            var payment = toss.findPayment(attempt.getPaymentKey(), orderId, attempt.getAmount());
            if (payment.isEmpty()) {
                // 조회 반영이 늦는 경우에도 나중에 승인된 결제를 놓치지 않도록 기록을 유지합니다.
                attempt.retryLater();
                return;
            }
            if (payment.get().isFullyCancelled()) {
                attempt.cancel();
                session.updateStatus(SessionStatus.FAILED);
            } else if (payment.get().isUnpaidTerminal()) {
                attempt.fail();
                session.updateStatus(SessionStatus.FAILED);
            } else if (payment.get().canCompensate()) {
                toss.cancelPayment(attempt.getPaymentKey(), orderId, attempt.getAmount());
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
