package com.kuji.backend.domain.inquiry.controller;

import com.kuji.backend.domain.inquiry.dto.InquiryResponse;
import com.kuji.backend.domain.inquiry.service.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final InquiryService inquiryService;

    /**
     * 전체 문의 내역 조회
     */
    @GetMapping
    public ResponseEntity<List<InquiryResponse>> getAllInquiries() {
        return ResponseEntity.ok(inquiryService.getAllInquiries());
    }

    /**
     * 문의 답변 등록/수정
     */
    @PutMapping("/{id}/answer")
    public ResponseEntity<Void> answerInquiry(
            @PathVariable(name = "id") Long id,
            @RequestBody AnswerRequest request) {
        
        inquiryService.answerInquiry(id, request.answerContent());
        return ResponseEntity.ok().build();
    }

    public record AnswerRequest(String answerContent) {}
}
