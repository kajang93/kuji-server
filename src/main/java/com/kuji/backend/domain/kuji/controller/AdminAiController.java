package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.service.AiVisionService;
import com.kuji.backend.domain.report.service.AiReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiVisionService aiVisionService;
    private final AiReportService aiReportService;

    /**
     * 관리자가 피규어/상품 이미지를 업로드하면:
     * 1) Hugging Face를 통해 배경(누끼) 제거
     * 2) S3에 투명 배경 이미지 업로드
     * 3) Gemini를 통해 멋진 이름/설명 스탯 생성
     * 4) 최종 결과를 JSON으로 반환
     */
    @PostMapping("/process-image")
    public ResponseEntity<Map<String, Object>> processPrizeImage(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = aiVisionService.processPrizeImage(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "AI 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 관리자가 수동으로 슬랙 일간 리포트를 즉시 발송 테스트
     */
    @GetMapping("/trigger-report")
    public ResponseEntity<Map<String, String>> triggerReportNow() {
        try {
            aiReportService.generateAndSendDailyReport();
            return ResponseEntity.ok(Map.of("message", "슬랙 리포트 수동 발송 성공! 슬랙을 확인해보세요."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "발송 실패: " + e.getMessage()));
        }
    }
}
