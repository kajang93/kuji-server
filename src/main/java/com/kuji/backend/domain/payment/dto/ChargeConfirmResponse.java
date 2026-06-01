package com.kuji.backend.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChargeConfirmResponse {
    private Integer pointsCharged;
    private Integer totalPoints;
}
