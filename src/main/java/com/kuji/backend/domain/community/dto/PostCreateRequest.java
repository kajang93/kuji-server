package com.kuji.backend.domain.community.dto;

import com.kuji.backend.domain.community.enums.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,
        
        @NotBlank(message = "내용은 필수입니다.")
        String content,
        
        @NotNull(message = "카테고리는 필수입니다.")
        PostCategory category
) {
}
