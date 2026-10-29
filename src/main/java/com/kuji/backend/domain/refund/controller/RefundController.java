// 로그인한 고객의 환불 대상과 접수 및 진행 상태 조회를 제공합니다.
package com.kuji.backend.domain.refund.controller;

import com.kuji.backend.domain.refund.dto.RefundDtos;
import com.kuji.backend.domain.refund.entity.RefundRequest;
import com.kuji.backend.domain.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RefundController {
    private final RefundService service;
    @GetMapping("/targets")
    public RefundDtos.PageResponse<RefundDtos.Target> targets(@AuthenticationPrincipal Long memberId,
            @RequestParam RefundRequest.TargetType targetType, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.targets(memberId, targetType, page, size);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundDtos.Response create(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RefundDtos.Create input) {
        return service.create(memberId, input);
    }
    @GetMapping("/me")
    public RefundDtos.PageResponse<RefundDtos.Response> mine(@AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.mine(memberId, page, size);
    }
}
