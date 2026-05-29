package com.kuji.backend.domain.kuji.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreparePaymentResponse {
    private String orderId;
    private Integer amount;
    private String boardTitle;
}
