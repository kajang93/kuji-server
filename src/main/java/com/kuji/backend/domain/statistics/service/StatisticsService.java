package com.kuji.backend.domain.statistics.service;

import com.kuji.backend.domain.kuji.enums.DrawStatus;
import com.kuji.backend.domain.kuji.repository.DrawHistoryRepository;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.PointType;
import com.kuji.backend.domain.member.repository.MemberRepository;
import com.kuji.backend.domain.member.repository.PointHistoryRepository;
import com.kuji.backend.domain.statistics.dto.AdminSummaryResponse;
import com.kuji.backend.domain.statistics.dto.DailySalesResponse;
import com.kuji.backend.domain.statistics.dto.SellerSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final PointHistoryRepository pointHistoryRepository;
    private final MemberRepository memberRepository;
    private final DrawHistoryRepository drawHistoryRepository;

    /**
     * [Admin] 전체 요약 통계
     */
    public AdminSummaryResponse getAdminSummary() {
        Long totalCharged = pointHistoryRepository.sumAmountByType(PointType.CHARGE);
        Long totalKujiSales = pointHistoryRepository.sumAmountByType(PointType.USE);
        Long totalMembers = memberRepository.count();

        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        Long newMembersToday = memberRepository.countMembersCreatedAfter(startOfToday);

        return AdminSummaryResponse.builder()
                .totalChargedPoints(totalCharged)
                .totalKujiSalesPoints(totalKujiSales)
                .totalMembers(totalMembers)
                .newMembersToday(newMembersToday)
                .build();
    }

    /**
     * [Admin] 일별 매출 차트 데이터 (최근 7일/30일 등)
     */
    public List<DailySalesResponse> getAdminDailySales(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days).with(LocalTime.MIN);
        
        List<Object[]> results = pointHistoryRepository.getDailySumByType(PointType.USE, startDate);
        return mapToDailySales(results, startDate.toLocalDate(), days);
    }

    /**
     * [Seller] 사업자 요약 통계 (이번 달 정산 기준 포함)
     */
    public SellerSummaryResponse getSellerSummary(Long sellerId) {
        Member seller = memberRepository.findById(sellerId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        // 1. 총 누적 매출
        Long totalSales = pointHistoryRepository.sumTotalSalesBySellerId(sellerId, PointType.USE);
        
        // 2. 배송 대기 건수
        Long pendingShipping = drawHistoryRepository.countByKujiBoardMemberIdAndStatus(sellerId, DrawStatus.SHIPPING_REQUESTED);

        // 3. 이번 달 정산 대상 매출 (전월 1일 ~ 말일 기준이 원칙이나, 현재 데모상 당월 기준으로 예시 구현 가능)
        // 여기서는 플랜에 맞게 전월(Previous Month) 매출로 계산
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDateTime startOfLastMonth = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfLastMonth = lastMonth.atEndOfMonth().atTime(23, 59, 59, 999999999);
        
        Long lastMonthSales = pointHistoryRepository.sumSalesBySellerIdAndDateRange(
                sellerId, PointType.USE, startOfLastMonth, endOfLastMonth);

        // 4. 수수료 로직: 가입 30일 내 0%, 이후 10%
        boolean isFirstMonthFree = seller.getCreatedAt().plusDays(30).isAfter(LocalDateTime.now());
        int appliedFeeRate = isFirstMonthFree ? 0 : 10;
        
        // 정산금 = 전월 매출 * (100 - 수수료율) / 100
        Long estimatedSettlement = lastMonthSales * (100 - appliedFeeRate) / 100;

        return SellerSummaryResponse.builder()
                .totalSalesPoints(totalSales)
                .estimatedSettlement(estimatedSettlement)
                .appliedFeeRate(appliedFeeRate)
                .isFirstMonthFree(isFirstMonthFree)
                .pendingShippingCount(pendingShipping)
                .build();
    }

    /**
     * [Seller] 일별 쿠지 판매 차트 데이터
     */
    public List<DailySalesResponse> getSellerDailySales(Long sellerId, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days).with(LocalTime.MIN);
        
        List<Object[]> results = pointHistoryRepository.getDailySalesBySellerId(sellerId, PointType.USE, startDate);
        return mapToDailySales(results, startDate.toLocalDate(), days);
    }

    /**
     * DB의 Group By Date 결과를 DailySalesResponse 리스트로 매핑 (빈 날짜 채우기 포함)
     */
    private List<DailySalesResponse> mapToDailySales(List<Object[]> results, LocalDate startDate, int days) {
        List<DailySalesResponse> response = new ArrayList<>();
        
        // 날짜별 빈 맵핑 생성
        for (int i = 0; i <= days; i++) {
            LocalDate date = startDate.plusDays(i);
            long amountForDate = 0L;
            
            // 결과에서 해당 날짜 찾기
            for (Object[] row : results) {
                java.sql.Date dbDate = (java.sql.Date) row[0];
                Number amount = (Number) row[1];
                
                if (dbDate.toLocalDate().equals(date)) {
                    amountForDate = amount != null ? amount.longValue() : 0L;
                    break;
                }
            }
            
            response.add(new DailySalesResponse(date, amountForDate));
        }
        
        return response;
    }
}
