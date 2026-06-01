package com.kuji.backend.domain.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChargeConfirmRequest {
    private String paymentKey; // 토스에서 발급한 결제 고유 키
    private String orderId;    // prepare 단계에서 발급한 주문 ID
    private Integer amount;    // 결제 금액 (검증용)
}
