package com.kuji.backend.domain.community.dto;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.enums.PostCategory;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostResponse(
        Long id,
        String title,
        String content,
        PostCategory category,
        int viewCount,
        String authorName,
        String authorEmail,
        LocalDateTime createdAt
) {
    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .category(post.getCategory())
                .viewCount(post.getViewCount())
                .authorName(post.getMember().getNickname())
                .authorEmail(post.getMember().getEmail())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
