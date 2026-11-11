// 관리자에게 정산 대상 조회와 실제 송금 확인 후 수동 지급 기록만 허용한다.
package com.kuji.backend.domain.settlement.controller;

import com.kuji.backend.domain.settlement.dto.SettlementDtos;
import com.kuji.backend.domain.settlement.service.SellerPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/admin/settlements", produces="application/json")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {
    private final SellerPayoutService service;
    @GetMapping("/sellers")
    public SettlementDtos.PageResponse<SettlementDtos.Seller> sellers(@AuthenticationPrincipal Long adminId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.sellers(adminId, page, size);
    }
    @GetMapping("/sellers/{sellerId}/sales")
    public SettlementDtos.PageResponse<SettlementDtos.Sale> sales(@AuthenticationPrincipal Long adminId, @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.sales(adminId, sellerId, page, size);
    }
    @PostMapping("/sellers/{sellerId}/preview")
    public SettlementDtos.Preview preview(@AuthenticationPrincipal Long adminId, @PathVariable Long sellerId,
            @Valid @RequestBody SettlementDtos.Selection input) { return service.preview(adminId, sellerId, input); }
    @PostMapping("/sellers/{sellerId}/payouts")
    public SettlementDtos.Payout record(@AuthenticationPrincipal Long adminId, @PathVariable Long sellerId,
            @Valid @RequestBody SettlementDtos.CreatePayout input) { return service.record(adminId, sellerId, input); }
    @GetMapping("/sellers/{sellerId}/payouts")
    public SettlementDtos.PageResponse<SettlementDtos.Payout> payouts(@AuthenticationPrincipal Long adminId, @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.adminPayouts(adminId, sellerId, page, size);
    }
    @GetMapping("/payouts/{id}")
    public SettlementDtos.Payout payout(@AuthenticationPrincipal Long adminId, @PathVariable Long id) { return service.adminPayout(adminId, id); }
}
