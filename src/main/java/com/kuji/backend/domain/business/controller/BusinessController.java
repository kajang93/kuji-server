package com.kuji.backend.domain.business.controller;

import com.kuji.backend.domain.business.dto.BusinessRegistrationRequest;
import com.kuji.backend.domain.business.service.BusinessInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            Authentication authentication,
            @RequestBody BusinessRegistrationRequest request) {

        System.out.println("🚩 [BusinessController] 사업자 등록 요청 도달! 데이터: " + request);
        
        // 💡 검문소를 통과한 진짜 유저의 이메일만 꺼냅니다!
        String email = authentication.getName();

        // 서비스로 넘겨서 사업자 등록 처리
        Long businessId = businessInfoService.registerBusiness(email, request);

        return ResponseEntity.ok(businessId);
    }
}