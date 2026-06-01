package com.kuji.backend.domain.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChargePrepareRequest {
    private Integer amount; // 충전할 금액 (원 단위, 1원 = 1포인트)
}
