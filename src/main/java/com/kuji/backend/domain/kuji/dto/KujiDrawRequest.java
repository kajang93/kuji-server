package com.kuji.backend.domain.kuji.dto;

import com.kuji.backend.domain.payment.enums.PaymentType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KujiDrawRequest {
    private Integer count;
    private PaymentType paymentType;
    
    // PG 결제 시에만 넘어오는 필드
    private String paymentKey;
    private String orderId;
    private Integer amount;
}
