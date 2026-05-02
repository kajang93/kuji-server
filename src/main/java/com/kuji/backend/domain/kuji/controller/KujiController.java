package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.dto.KujiBoardCreateRequest;
import com.kuji.backend.domain.kuji.dto.KujiBoardResponse;
import com.kuji.backend.domain.kuji.dto.KujiItemCreateRequest;
import com.kuji.backend.domain.kuji.dto.KujiItemResponse;
import com.kuji.backend.domain.kuji.enums.BoardImageType;
import com.kuji.backend.domain.kuji.service.KujiBoardService;
import com.kuji.backend.domain.kuji.service.KujiItemService;
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
    private final KujiItemService kujiItemService;

    /**
     * 쿠지 판 전체 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<KujiBoardResponse>> getAllBoards() {
        return ResponseEntity.ok(kujiBoardService.getAllBoards());
    }

    /**
     * 쿠지 판 상세 조회 (상품 목록 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<List<KujiItemResponse>> getBoardItems(@PathVariable("id") Long boardId) {
        return ResponseEntity.ok(kujiItemService.getItemsByBoardId(boardId));
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

    /**
     * 쿠지 판 상태 수정
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateBoardStatus(
            @PathVariable("id") Long boardId,
            @RequestParam("status") com.kuji.backend.domain.kuji.enums.BoardStatus status) {
        
        kujiBoardService.updateBoardStatus(boardId, status);
        return ResponseEntity.ok().build();
    }

    /**
     * 쿠지 상품 대량 등록
     */
    @PostMapping("/{id}/items")
    public ResponseEntity<Void> registerItems(
            @PathVariable("id") Long boardId,
            @RequestPart("items") List<KujiItemCreateRequest> requests,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        
        kujiItemService.createItems(boardId, requests, files);
        return ResponseEntity.ok().build();
    }

    /**
     * 쿠지 상품 수정
     */
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<Void> updateItem(
            @PathVariable("itemId") Long itemId,
            @RequestBody KujiItemCreateRequest request) {
        
        kujiItemService.updateItem(itemId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 상품 이미지 단건 수정/추가
     */
    @PostMapping("/items/{itemId}/images")
    public ResponseEntity<Void> updateItemImage(
            @PathVariable("itemId") Long itemId,
            @RequestParam("file") MultipartFile file) {
        
        kujiItemService.updateItemImage(itemId, file);
        return ResponseEntity.ok().build();
    }

    /**
     * 쿠지 상품 삭제
     */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable("itemId") Long itemId) {
        kujiItemService.deleteItem(itemId);
        return ResponseEntity.ok().build();
    }
}
