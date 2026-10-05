package com.kuji.backend.domain.threads.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadsPostResult {
    private boolean success;
    private String postId;
    private String replyId;
    private String postContent;
    private String replyContent;
    private String errorMessage;
    private LocalDateTime publishedAt;
}
