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
    private Integer gradeCount; // 등급 종류 수 (예: A~G상 총 7종)
    private Boolean isWished; // 찜 여부 추가
    private List<KujiBoardImageResponse> images;

    public static KujiBoardResponse from(com.kuji.backend.domain.kuji.entity.KujiBoard board) {
        return KujiBoardResponse.builder()
                .id(board.getId())
                .title(board.getTitle())
                .pricePerDraw(board.getPricePerDraw())
                .status(board.getStatus())
                .rewardRate(board.getRewardRate())
                .createdAt(board.getCreatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class KujiBoardImageResponse {
        private Long id;
        private String imageUrl;
        private Integer sequence;
        private String imageType;
    }
}
