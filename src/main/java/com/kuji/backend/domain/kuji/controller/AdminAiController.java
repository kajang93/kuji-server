package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.service.AiVisionService;
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
}
