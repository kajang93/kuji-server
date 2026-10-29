// 실제 구매 원장과 과거 미대사 거래를 구분하여 운영·판매자 통계를 제공합니다.
package com.kuji.backend.domain.statistics.service;

import com.kuji.backend.domain.kuji.enums.DrawStatus;
import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.member.enums.PointType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.member.repository.PointHistoryRepository;
import com.kuji.backend.domain.member.repository.BusinessInfoRepository;
import com.kuji.backend.domain.kuji.repository.KujiBoardRepository;
import com.kuji.backend.domain.kuji.repository.WishlistRepository;
import com.kuji.backend.domain.settlement.repository.SettlementQueryRepository;
import com.kuji.backend.domain.statistics.dto.AdminSummaryResponse;
import com.kuji.backend.domain.statistics.dto.DailySalesResponse;
import com.kuji.backend.domain.statistics.dto.SellerSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class StatisticsService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final PointHistoryRepository pointHistoryRepository;
    private final MemberRepository memberRepository;
    private final DrawHistoryRepository drawHistoryRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final KujiBoardRepository kujiBoardRepository;
    private final WishlistRepository wishlistRepository;
    private final SettlementQueryRepository settlementQueries;

    public AdminSummaryResponse getAdminSummary() {
        return AdminSummaryResponse.builder()
                .totalChargedPoints(pointHistoryRepository.sumAmountByType(PointType.CHARGE))
                .totalKujiSalesPoints(settlementQueries.totalSales())
                .totalMembers(memberRepository.count())
                .newMembersToday(memberRepository.countMembersCreatedAfter(LocalDate.now(KOREA).atStartOfDay()))
                .build();
    }
    public List<DailySalesResponse> getAdminDailySales(int days) { return dailySales(null, days); }

    public SellerSummaryResponse getSellerSummary(Long sellerId) {
        if (!memberRepository.existsById(sellerId)) throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        var business = businessInfoRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("사업자 정보를 찾을 수 없습니다."));
        var amounts = settlementQueries.sellerAmounts(sellerId);
        boolean promotion = business.getPromotionEndAt() != null
                && ZonedDateTime.now(KOREA).isBefore(business.getPromotionEndAt());
        int currentFeeRate = promotion ? 0 : business.getBaseFeeRate();
        return SellerSummaryResponse.builder()
                .totalSalesPoints(amounts.totalSales())
                .confirmedSalesAmount(amounts.confirmedSales())
                .pendingSalesAmount(amounts.pendingSales())
                .platformFeeAmount(amounts.platformFee())
                .pendingFeeAmount(amounts.pendingFee())
                // 접수·검토 승인은 실제 환불이나 송금으로 집계하지 않습니다.
                .refundedAmount(0L)
                .confirmedSettlementAmount(amounts.confirmedSettlement())
                .pendingSettlementAmount(amounts.pendingSettlement())
                .paidSettlementAmount(0L)
                .unpaidSettlementAmount(amounts.confirmedSettlement())
                .refundReviewHoldAmount(amounts.refundHold())
                .legacySalesAmount(amounts.legacySales())
                .legacyReviewCount(amounts.legacyReviewCount())
                .legacyUnreconciledCount(amounts.legacyUnreconciledCount())
                .testSalesAmount(amounts.testSales())
                .unknownSalesAmount(amounts.unknownSales())
                .settlementReviewRequired(amounts.legacyReviewCount() > 0 || amounts.unknownSales() > 0)
                .settlementNotice("신규 거래는 실제 결제 환경이 확인된 경우만 매출·정산에 포함하며 테스트 거래와 결제 환경 미확인 거래는 제외합니다. 일반 당첨과 라스트상을 포함한 주문 전체의 배송 완료 후 거래 당시 수수료를 차감합니다. 진행 중인 환불 검토는 정산을 보류합니다. 실제 지급은 별도이며, 과거 거래는 결제 환경·수수료·주문 대사 전까지 정산 미확정입니다. PG 비용을 임의로 추가 공제하지 않습니다.")
                .appliedFeeRate(currentFeeRate)
                .isFirstMonthFree(promotion)
                .pendingShippingCount(drawHistoryRepository.countByKujiBoardMemberIdAndStatus(sellerId, DrawStatus.SHIPPING_REQUESTED))
                .totalRegisteredProducts(kujiBoardRepository.countByMemberId(sellerId))
                .totalWishlistCount(wishlistRepository.countBySellerId(sellerId))
                .build();
    }
    public List<DailySalesResponse> getSellerDailySales(Long sellerId, int days) { return dailySales(sellerId, days); }
    private List<DailySalesResponse> dailySales(Long sellerId, int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("통계 조회 기간은 1일부터 90일까지 선택해주세요.");
        LocalDate today = LocalDate.now(KOREA);
        LocalDate from = today.minusDays(days - 1L);
        var byDate = settlementQueries.dailySales(sellerId, from, today).stream()
                .collect(Collectors.toMap(SettlementQueryRepository.DayAmount::date, Function.identity()));
        List<DailySalesResponse> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            var amount = byDate.get(date);
            result.add(new DailySalesResponse(date, amount == null ? 0L : amount.amount()));
        }
        return result;
    }
}
