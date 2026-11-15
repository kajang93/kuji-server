package com.kuji.backend.domain.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChargeConfirmRequest {
    private String paymentKey; // 나이스페이먼츠 거래 키(tid)
    private String orderId;    // prepare 단계에서 발급한 주문 ID
    private Integer amount;    // 결제 금액 (검증용)
}
