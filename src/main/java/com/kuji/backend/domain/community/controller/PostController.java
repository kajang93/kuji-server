package com.kuji.backend.domain.community.controller;

import com.kuji.backend.domain.community.dto.PostCreateRequest;
import com.kuji.backend.domain.community.dto.PostResponse;
import com.kuji.backend.domain.community.enums.PostCategory;
import com.kuji.backend.domain.community.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final com.kuji.backend.domain.community.service.PostInteractionService postInteractionService;

    /**
     * 게시글 작성 API (이미지 포함)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createPost(
            Authentication authentication,
            @RequestPart("request") PostCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(postService.createPost(memberId, request, files));
    }

    /**
     * 게시글 목록 조회 API
     */
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPosts(
            Authentication authentication,
            @RequestParam(name = "category", required = false) PostCategory category,
            @PageableDefault(size = 10) Pageable pageable) {
        
        Long memberId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return ResponseEntity.ok(postService.getPosts(memberId, category, pageable));
    }

    /**
     * 게시글 상세 조회 API
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostDetail(
            Authentication authentication,
            @PathVariable("id") Long postId) {
        Long memberId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return ResponseEntity.ok(postService.getPostDetail(memberId, postId));
    }

    /**
     * 게시글 수정 API (이미지 수정 포함)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updatePost(
            Authentication authentication,
            @PathVariable("id") Long postId,
            @RequestPart("request") PostCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        
        Long memberId = (Long) authentication.getPrincipal();
        postService.updatePost(memberId, postId, request, files);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 삭제 API
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            Authentication authentication,
            @PathVariable("id") Long postId) {
        
        Long memberId = (Long) authentication.getPrincipal();
        postService.deletePost(memberId, postId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 좋아요 토글 API
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> toggleLike(
            Authentication authentication,
            @PathVariable("id") Long postId) {
        
        Long memberId = (Long) authentication.getPrincipal();
        postInteractionService.toggleLike(memberId, postId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 찜하기 토글 API
     */
    @PostMapping("/{id}/wish")
    public ResponseEntity<Void> toggleWishlist(
            Authentication authentication,
            @PathVariable("id") Long postId) {
        
        Long memberId = (Long) authentication.getPrincipal();
        postInteractionService.toggleWishlist(memberId, postId);
        return ResponseEntity.ok().build();
    }
}
