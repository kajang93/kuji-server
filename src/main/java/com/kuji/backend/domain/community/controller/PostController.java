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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal Long memberId,
            @RequestPart("request") PostCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        
        return ResponseEntity.ok(postService.createPost(memberId, request, files));
    }

    /**
     * 게시글 목록 조회 API
     */
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPosts(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(name = "category", required = false) PostCategory category,
            @PageableDefault(size = 10) Pageable pageable) {
        
        return ResponseEntity.ok(postService.getPosts(memberId, category, pageable));
    }

    /**
     * 게시글 상세 조회 API
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("id") Long postId) {
        return ResponseEntity.ok(postService.getPostDetail(memberId, postId));
    }

    /**
     * 게시글 수정 API (이미지 수정 포함)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updatePost(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("id") Long postId,
            @RequestPart("request") PostCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        
        postService.updatePost(memberId, postId, request, files);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 삭제 API
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("id") Long postId) {
        
        postService.deletePost(memberId, postId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 좋아요 토글 API
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> toggleLike(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("id") Long postId) {
        
        postInteractionService.toggleLike(memberId, postId);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시글 찜하기 토글 API
     */
    @PostMapping("/{id}/wish")
    public ResponseEntity<Void> toggleWishlist(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("id") Long postId) {
        
        postInteractionService.toggleWishlist(memberId, postId);
        return ResponseEntity.ok().build();
    }
}
