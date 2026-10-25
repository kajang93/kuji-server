// PG 전송 가능성이 생기는 순간을 업무 트랜잭션과 별개로 확정한다.
package com.kuji.backend.domain.payment.service;

import com.kuji.backend.domain.payment.entity.PaymentRecoveryAttempt;
import com.kuji.backend.domain.payment.enums.SessionType;
import com.kuji.backend.domain.payment.repository.PaymentRecoveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentSubmissionService {
    private final PaymentRecoveryAttemptRepository attempts;
    // 호출자는 회원과 결제 세션을 먼저 잠그고, 복구 기록은 아직 읽거나 잠그지 않아야 합니다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(String orderId, Long memberId, String paymentKey, Integer amount, SessionType type) {
        PaymentRecoveryAttempt attempt = attempts.findByOrderIdWithLock(orderId).orElseThrow();
        if (!attempt.getMemberId().equals(memberId) || !attempt.getPaymentKey().equals(paymentKey)
                || !attempt.getAmount().equals(amount) || attempt.getSessionType() != type) {
            throw new IllegalArgumentException("기존 결제 요청과 다른 승인 정보입니다.");
        }
        if (attempt.getState() != PaymentRecoveryAttempt.State.OPEN) {
            throw new IllegalStateException("이미 처리 중인 결제입니다. 결제 상태를 확인해주세요.");
        }
        attempt.startSubmission();
    }
}
