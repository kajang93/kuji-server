package com.kuji.backend.domain.payment.repository;

import com.kuji.backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
