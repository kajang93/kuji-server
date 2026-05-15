package com.kuji.backend.domain.kuji.controller;

import com.kuji.backend.domain.kuji.dto.KujiBoardResponse;
import com.kuji.backend.domain.kuji.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * 찜 토글 API
     */
    @PostMapping("/{boardId}")
    public ResponseEntity<Map<String, Boolean>> toggleWishlist(
            Authentication authentication,
            @PathVariable(name = "boardId") Long boardId) {
        
        Long memberId = (Long) authentication.getPrincipal();
        boolean isWished = wishlistService.toggleWishlist(memberId, boardId);
        
        return ResponseEntity.ok(Map.of("wished", isWished));
    }

    /**
     * 나의 찜 목록 조회 API
     */
    @GetMapping("/me")
    public ResponseEntity<List<KujiBoardResponse>> getMyWishlist(Authentication authentication) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(wishlistService.getMyWishlist(memberId));
    }
}
