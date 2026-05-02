package com.kuji.backend.domain.kuji.dto;

import com.kuji.backend.domain.kuji.enums.BoardStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class KujiBoardResponse {
    private Long id;
    private String title;
    private Long pricePerDraw;
    private BoardStatus status;
    private Integer rewardRate;
    private LocalDateTime createdAt;
    private Integer totalCount;
    private Integer remainCount;
    private List<KujiBoardImageResponse> images;

    @Getter
    @Builder
    public static class KujiBoardImageResponse {
        private Long id;
        private String imageUrl;
        private Integer sequence;
        private String imageType;
    }
}
