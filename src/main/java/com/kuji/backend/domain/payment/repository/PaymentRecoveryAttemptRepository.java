// 복구 대상 결제를 조회하고 주문별 복구 기록을 잠근다.
package com.kuji.backend.domain.payment.repository;

import com.kuji.backend.domain.payment.entity.PaymentRecoveryAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRecoveryAttemptRepository extends JpaRepository<PaymentRecoveryAttempt, Long> {
    Optional<PaymentRecoveryAttempt> findByOrderId(String orderId);
    @Query("select a.memberId from PaymentRecoveryAttempt a where a.orderId = :orderId")
    Optional<Long> findMemberIdByOrderId(@Param("orderId") String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentRecoveryAttempt a where a.orderId = :orderId")
    Optional<PaymentRecoveryAttempt> findByOrderIdWithLock(@Param("orderId") String orderId);
    @Query("select a.orderId from PaymentRecoveryAttempt a where a.state in :states and a.retryAt <= :now order by a.retryAt")
    List<String> findDueOrderIds(@Param("states") List<PaymentRecoveryAttempt.State> states,
                                @Param("now") LocalDateTime now, Pageable pageable);
}
