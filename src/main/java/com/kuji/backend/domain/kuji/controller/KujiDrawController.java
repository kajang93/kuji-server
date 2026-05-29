package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.dto.DrawHistoryResponse;
import com.kuji.backend.domain.kuji.dto.KujiDrawRequest;
import com.kuji.backend.domain.kuji.dto.KujiDrawResponse;
import com.kuji.backend.domain.kuji.dto.PreparePaymentRequest;
import com.kuji.backend.domain.kuji.dto.PreparePaymentResponse;
import com.kuji.backend.domain.kuji.dto.RecentDrawResponse;
import com.kuji.backend.domain.kuji.service.KujiDrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kuji")
public class KujiDrawController {

    private final KujiDrawService kujiDrawService;

    /**
     * PG 결제 준비 (세션 생성 및 orderId 발급)
     */
    @PostMapping("/{id}/payment/prepare")
    public ResponseEntity<PreparePaymentResponse> preparePayment(
            Authentication authentication,
            @PathVariable("id") Long boardId,
            @RequestBody PreparePaymentRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(kujiDrawService.preparePayment(memberId, boardId, request));
    }

    /**
     * 쿠지 무작위 뽑기 실행
     */
    @PostMapping("/{id}/draw")
    public ResponseEntity<KujiDrawResponse> draw(
            Authentication authentication,
            @PathVariable("id") Long boardId,
            @RequestBody KujiDrawRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        
        return ResponseEntity.ok(kujiDrawService.draw(memberId, boardId, request));
    }

    /**
     * 내 당첨 내역(보관함) 조회 API
     */
    @GetMapping("/draw-history/me")
    public ResponseEntity<List<DrawHistoryResponse>> getMyDrawHistory(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(kujiDrawService.getMyDrawHistory(memberId));
    }

    /**
     * 전역 최근 당첨 내역 조회 (티커용, 비로그인 허용)
     */
    @GetMapping("/draw-history/recent")
    public ResponseEntity<List<RecentDrawResponse>> getRecentDrawHistory() {
        return ResponseEntity.ok(kujiDrawService.getRecentDrawHistory());
    }
}
