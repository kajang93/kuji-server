// 관리자에게 환불 요청의 조회와 실행 전 검토만 허용합니다.
package com.kuji.backend.domain.refund.controller;

import com.kuji.backend.domain.refund.dto.RefundDtos;
import com.kuji.backend.domain.refund.entity.RefundRequest;
import com.kuji.backend.domain.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {
    private final RefundService service;
    @GetMapping
    public RefundDtos.PageResponse<RefundDtos.Response> list(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) RefundRequest.Status status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.adminList(memberId, status, page, size);
    }
    @PatchMapping("/{id}/review")
    public RefundDtos.Response review(@AuthenticationPrincipal Long memberId, @PathVariable Long id,
            @Valid @RequestBody RefundDtos.Review input) {
        return service.review(memberId, id, input);
    }
}
