package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.dto.DrawHistoryResponse;
import com.kuji.backend.domain.kuji.dto.KujiDrawRequest;
import com.kuji.backend.domain.kuji.dto.KujiDrawResponse;
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
     * 쿠지 무작위 뽑기 실행
     */
    @PostMapping("/{id}/draw")
    public ResponseEntity<KujiDrawResponse> draw(
            Authentication authentication,
            @PathVariable("id") Long boardId,
            @RequestBody KujiDrawRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        int count = (request.getCount() != null) ? request.getCount() : 1;
        
        return ResponseEntity.ok(kujiDrawService.draw(memberId, boardId, count));
    }

    /**
     * 내 당첨 내역(보관함) 조회 API
     */
    @GetMapping("/draw-history/me")
    public ResponseEntity<List<DrawHistoryResponse>> getMyDrawHistory(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(kujiDrawService.getMyDrawHistory(memberId));
    }
}
