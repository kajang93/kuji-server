package com.kuji.backend.domain.kuji.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PreparePaymentRequest {
    private Integer count;
    private Integer pointsUsed;
}
