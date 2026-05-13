package com.kuji.backend.domain.inquiry.controller;

import com.kuji.backend.domain.inquiry.dto.InquiryCreateRequest;
import com.kuji.backend.domain.inquiry.dto.InquiryResponse;
import com.kuji.backend.domain.inquiry.service.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    /**
     * 문의 등록
     */
    @PostMapping
    public ResponseEntity<Long> createInquiry(
            Authentication authentication,
            @RequestBody InquiryCreateRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(inquiryService.createInquiry(memberId, request));
    }

    /**
     * 나의 문의 내역 조회
     */
    @GetMapping("/my")
    public ResponseEntity<List<InquiryResponse>> getMyInquiries(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(inquiryService.getMyInquiries(memberId));
    }

    /**
     * 문의 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<InquiryResponse> getInquiry(
            Authentication authentication,
            @PathVariable(name = "id") Long id) {
        
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(inquiryService.getInquiry(id, memberId));
    }
}
