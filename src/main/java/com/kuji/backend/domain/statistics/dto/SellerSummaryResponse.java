package com.kuji.backend.domain.statistics.dto;

import lombok.Builder;

@Builder
public record SellerSummaryResponse(
        Long totalSalesPoints,         // 총 발생 매출(포인트)
        Long estimatedSettlement,      // 수수료 공제 후 예상 정산금
        Integer appliedFeeRate,        // 적용된 수수료율 (10 또는 0)
        boolean isFirstMonthFree,      // 첫 달 무료 적용 여부
        Long pendingShippingCount,     // 배송 대기 건수
        Long totalRegisteredProducts,  // 등록된 상품 수
        Long totalWishlistCount        // 받은 찜 횟수
) {
}
