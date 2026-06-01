package com.kuji.backend.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChargePrepareResponse {
    private String orderId;
    private Integer amount;
    private Integer bonusPoints;
}
