package com.kuji.backend.domain.community.dto;

import com.kuji.backend.domain.community.entity.Post;
import com.kuji.backend.domain.community.enums.PostCategory;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
public record PostResponse(
        Long id,
        String title,
        String content,
        PostCategory category,
        int viewCount,
        String authorName,
        String authorEmail,
        List<String> imageUrls,
        LocalDateTime createdAt
) {
    public static PostResponse from(Post post) {
        List<String> urls = new ArrayList<>();
        if (post.getImage1() != null) urls.add(post.getImage1());
        if (post.getImage2() != null) urls.add(post.getImage2());
        if (post.getImage3() != null) urls.add(post.getImage3());

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .category(post.getCategory())
                .viewCount(post.getViewCount())
                .authorName(post.getMember().getNickname())
                .authorEmail(post.getMember().getEmail())
                .imageUrls(urls)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
