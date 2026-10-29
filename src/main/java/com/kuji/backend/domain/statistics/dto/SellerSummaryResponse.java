// 배송 완료 거래의 실제 정산 대상액과 대기 및 과거 미대사 금액을 구분합니다.
package com.kuji.backend.domain.statistics.dto;

import lombok.Builder;

@Builder
public record SellerSummaryResponse(
        Long totalSalesPoints,
        Long confirmedSalesAmount,
        Long pendingSalesAmount,
        Long platformFeeAmount,
        Long pendingFeeAmount,
        Long refundedAmount,
        Long confirmedSettlementAmount,
        Long pendingSettlementAmount,
        Long paidSettlementAmount,
        Long unpaidSettlementAmount,
        Long refundReviewHoldAmount,
        Long legacySalesAmount,
        Long legacyReviewCount,
        Long legacyUnreconciledCount,
        Long testSalesAmount,
        Long unknownSalesAmount,
        boolean settlementReviewRequired,
        String settlementNotice,
        Integer appliedFeeRate,
        boolean isFirstMonthFree,
        Long pendingShippingCount,
        Long totalRegisteredProducts,
        Long totalWishlistCount
) {}
