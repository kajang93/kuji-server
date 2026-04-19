package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.dto.KujiBoardCreateRequest;
import com.kuji.backend.domain.kuji.dto.KujiBoardResponse;
import com.kuji.backend.domain.kuji.enums.BoardImageType;
import com.kuji.backend.domain.kuji.service.KujiBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kuji")
public class KujiController {

    private final KujiBoardService kujiBoardService;

    /**
     * 쿠지 판 전체 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<KujiBoardResponse>> getAllBoards() {
        return ResponseEntity.ok(kujiBoardService.getAllBoards());
    }

    /**
     * 쿠지 판 생성
     */
    @PostMapping
    public ResponseEntity<Long> createBoard(
            Authentication authentication,
            @RequestBody KujiBoardCreateRequest request) {
        
        // JwtAuthenticationFilter에서 저장한 memberId(Long)를 꺼냅니다.
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(kujiBoardService.createBoard(memberId, request));
    }

    /**
     * 쿠지 판 이미지 업로드
     */
    @PostMapping("/{id}/images")
    public ResponseEntity<Void> uploadImages(
            @PathVariable("id") Long boardId,
            @RequestParam("type") BoardImageType type,
            @RequestParam("files") List<MultipartFile> files) {
        
        kujiBoardService.uploadImages(boardId, type, files);
        return ResponseEntity.ok().build();
    }
}
