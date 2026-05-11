package com.kuji.backend.domain.community.controller;

import com.kuji.backend.domain.community.dto.PostCreateRequest;
import com.kuji.backend.domain.community.dto.PostResponse;
import com.kuji.backend.domain.community.enums.PostCategory;
import com.kuji.backend.domain.community.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    /**
     * 게시글 작성 API
     */
    @PostMapping
    public ResponseEntity<Long> createPost(
            Authentication authentication,
            @RequestBody PostCreateRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(postService.createPost(memberId, request));
    }

    /**
     * 게시글 목록 조회 API
     */
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPosts(
            @RequestParam(name = "category", required = false) PostCategory category,
            @PageableDefault(size = 10) Pageable pageable) {
        
        return ResponseEntity.ok(postService.getPosts(category, pageable));
    }

    /**
     * 게시글 상세 조회 API
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostDetail(@PathVariable("id") Long postId) {
        return ResponseEntity.ok(postService.getPostDetail(postId));
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
}
