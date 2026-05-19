package com.kuji.backend.domain.community.controller;

import com.kuji.backend.domain.community.dto.CommentRequest;
import com.kuji.backend.domain.community.dto.CommentResponse;
import com.kuji.backend.domain.community.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<Void> createComment(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody CommentRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        commentService.createComment(memberId, postId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable("postId") Long postId) {
        return ResponseEntity.ok(commentService.getCommentsByPost(postId));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<Void> updateComment(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody CommentRequest request) {
        
        Long memberId = (Long) authentication.getPrincipal();
        commentService.updateComment(memberId, commentId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId) {
        
        Long memberId = (Long) authentication.getPrincipal();
        commentService.deleteComment(memberId, commentId);
        return ResponseEntity.ok().build();
    }
}
