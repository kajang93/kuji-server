package com.kuji.backend.domain.kuji.dto;

import com.kuji.backend.domain.kuji.enums.BoardStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KujiBoardCreateRequest {
    private String title;
    private Long pricePerDraw;
    private BoardStatus status;
    private Integer rewardRate;
}
