package com.kuji.backend.domain.business.controller;

import com.kuji.backend.domain.business.dto.BusinessRegistrationRequest;
import com.kuji.backend.domain.business.service.BusinessInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/business")
public class BusinessController {

    private final BusinessInfoService businessInfoService;

    /**
     * 사업자 권한 등록 API
     */
    @PostMapping("/register")
    public ResponseEntity<Long> registerBusiness(
            @AuthenticationPrincipal Long memberId,
            @RequestBody BusinessRegistrationRequest request) {

        System.out.println("🚩 [BusinessController] 사업자 등록 요청 도달! 데이터: " + request);

        // 서비스로 넘겨서 사업자 등록 처리
        Long businessId = businessInfoService.registerBusiness(memberId, request);

        return ResponseEntity.ok(businessId);
    }
}
