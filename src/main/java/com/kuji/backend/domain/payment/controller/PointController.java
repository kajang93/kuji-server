package com.kuji.backend.domain.payment.controller;

import com.kuji.backend.domain.payment.dto.ChargeConfirmRequest;
import com.kuji.backend.domain.payment.dto.ChargeConfirmResponse;
import com.kuji.backend.domain.payment.dto.ChargePrepareRequest;
import com.kuji.backend.domain.payment.dto.ChargePrepareResponse;
import com.kuji.backend.domain.payment.dto.PointHistoryResponse;
import com.kuji.backend.domain.payment.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

    private final PointService pointService;

    /**
     * 포인트 충전 준비 (결제 세션 생성 및 orderId 발급)
     * POST /api/points/charge/prepare
     */
    @PostMapping("/charge/prepare")
    public ResponseEntity<ChargePrepareResponse> prepareCharge(
            Authentication authentication,
            @RequestBody ChargePrepareRequest request) {

        Long memberId = (Long) authentication.getPrincipal();
        ChargePrepareResponse response = pointService.prepareCharge(memberId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 충전 승인 (토스 결제 확인 + 포인트 지급)
     * POST /api/points/charge/confirm
     */
    @PostMapping("/charge/confirm")
    public ResponseEntity<ChargeConfirmResponse> confirmCharge(
            Authentication authentication,
            @RequestBody ChargeConfirmRequest request) {

        Long memberId = (Long) authentication.getPrincipal();
        ChargeConfirmResponse response = pointService.confirmCharge(memberId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 포인트 사용/충전 내역 조회 (최신순)
     * GET /api/points/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<PointHistoryResponse>> getPointHistory(
            Authentication authentication) {

        Long memberId = (Long) authentication.getPrincipal();
        List<PointHistoryResponse> history = pointService.getPointHistory(memberId);
        return ResponseEntity.ok(history);
    }
}
