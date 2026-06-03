package com.kuji.backend.domain.statistics.controller;

import com.kuji.backend.domain.statistics.dto.AdminSummaryResponse;
import com.kuji.backend.domain.statistics.dto.DailySalesResponse;
import com.kuji.backend.domain.statistics.dto.SellerSummaryResponse;
import com.kuji.backend.domain.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * [Admin] 요약 통계 조회
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/summary")
    public ResponseEntity<AdminSummaryResponse> getAdminSummary() {
        return ResponseEntity.ok(statisticsService.getAdminSummary());
    }

    /**
     * [Admin] 일별 매출 차트 데이터 조회
     * @param days 며칠 전부터 볼 것인지 (기본 7)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/daily-sales")
    public ResponseEntity<List<DailySalesResponse>> getAdminDailySales(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(statisticsService.getAdminDailySales(days));
    }

    /**
     * [Seller] 요약 통계 조회
     */
    @PreAuthorize("hasRole('BIZ')")
    @GetMapping("/seller/summary")
    public ResponseEntity<SellerSummaryResponse> getSellerSummary(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(statisticsService.getSellerSummary(memberId));
    }

    /**
     * [Seller] 일별 쿠지 매출 차트 데이터 조회
     * @param days 며칠 전부터 볼 것인지 (기본 7)
     */
    @PreAuthorize("hasRole('BIZ')")
    @GetMapping("/seller/daily-sales")
    public ResponseEntity<List<DailySalesResponse>> getSellerDailySales(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(statisticsService.getSellerDailySales(memberId, days));
    }
}
