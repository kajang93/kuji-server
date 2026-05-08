package com.kuji.backend.domain.shipping.controller;

import com.kuji.backend.domain.shipping.dto.ShippingRequest;
import com.kuji.backend.domain.shipping.dto.ShippingResponse;
import com.kuji.backend.domain.shipping.dto.TrackingRequest;
import com.kuji.backend.domain.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    /**
     * 배송 신청 API
     */
    @PostMapping
    public ResponseEntity<Long> requestShipping(
            @AuthenticationPrincipal Long memberId,
            @RequestBody ShippingRequest request) {
        
        Long shippingId = shippingService.requestShipping(memberId, request);
        return ResponseEntity.ok(shippingId);
    }

    /**
     * 내 배송 내역 조회 API
     */
    @GetMapping("/me")
    public ResponseEntity<List<ShippingResponse>> getMyShippings(
            @AuthenticationPrincipal Long memberId) {
        
        List<ShippingResponse> responses = shippingService.getMyShippingList(memberId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 전체 배송 목록 조회 (관리자용)
     */
    @GetMapping("/admin")
    public ResponseEntity<List<ShippingResponse>> getAllShippings() {
        List<ShippingResponse> responses = shippingService.getAllShippingList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 운송장 번호 등록 API (관리자용)
     */
    @PatchMapping("/{id}/tracking")
    public ResponseEntity<Void> updateTracking(
            @PathVariable Long id,
            @RequestBody TrackingRequest request) {
        
        shippingService.updateTrackingInfo(id, request.courierName(), request.trackingNumber());
        return ResponseEntity.ok().build();
    }
}
