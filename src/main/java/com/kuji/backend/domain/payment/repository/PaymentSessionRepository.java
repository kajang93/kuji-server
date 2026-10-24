package com.kuji.backend.domain.payment.repository;

import com.kuji.backend.domain.payment.entity.PaymentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {
    Optional<PaymentSession> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PaymentSession s where s.orderId = :orderId")
    Optional<PaymentSession> findByOrderIdWithLock(@Param("orderId") String orderId);
}
