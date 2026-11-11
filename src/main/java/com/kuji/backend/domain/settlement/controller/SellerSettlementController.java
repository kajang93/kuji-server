// 판매자가 본인에게 실제 지급됐다고 기록된 내역만 조회하도록 제한한다.
package com.kuji.backend.domain.settlement.controller;

import com.kuji.backend.domain.settlement.dto.SettlementDtos;
import com.kuji.backend.domain.settlement.service.SellerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/settlements/payouts", produces="application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BIZ','ADMIN')")
public class SellerSettlementController {
    private final SellerPayoutService service;
    @GetMapping
    public SettlementDtos.PageResponse<SettlementDtos.Payout> mine(@AuthenticationPrincipal Long sellerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.myPayouts(sellerId, page, size);
    }
    @GetMapping("/{id}")
    public SettlementDtos.Payout detail(@AuthenticationPrincipal Long sellerId, @PathVariable Long id) { return service.myPayout(sellerId, id); }
}
