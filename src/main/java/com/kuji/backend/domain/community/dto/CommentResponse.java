package com.kuji.backend.domain.community.dto;

import com.kuji.backend.domain.community.entity.Comment;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CommentResponse(
        Long id,
        String content,
        String authorName,
        String authorEmail,
        String profileImageUrl,
        LocalDateTime createdAt
) {
    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .authorName(comment.getMember().getNickname())
                .authorEmail(comment.getMember().getEmail())
                .profileImageUrl(comment.getMember().getProfileImageUrl())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
