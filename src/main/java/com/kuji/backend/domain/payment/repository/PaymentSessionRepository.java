package com.kuji.backend.domain.payment.repository;

import com.kuji.backend.domain.payment.entity.PaymentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {
    Optional<PaymentSession> findByOrderId(String orderId);
}
