package com.kuji.backend.domain.statistics.dto;

import lombok.Builder;

@Builder
public record AdminSummaryResponse(
        Long totalChargedPoints,    // 전체 누적 충전액
        Long totalKujiSalesPoints,  // 전체 누적 쿠지 판매액
        Long totalMembers,          // 전체 가입자 수
        Long newMembersToday        // 오늘 가입자 수
) {
}
