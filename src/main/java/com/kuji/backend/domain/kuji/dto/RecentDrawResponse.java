package com.kuji.backend.domain.kuji.dto;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record RecentDrawResponse(
        String maskedNickname,
        String boardTitle,
        String grade,
        String itemName,
        LocalDateTime createdAt
) {
}
