package com.kuji.backend.domain.statistics.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record DailySalesResponse(
        LocalDate date,
        Long totalAmount // 포인트(매출액 또는 충전액)
) {
}
