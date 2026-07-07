// 쿠지 상품 등급별 자동 입력 추천 응답 DTO
package com.kuji.backend.domain.kuji.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KujiItemSuggestionResponse {
    private String grade;
    private String suggestedName;
    private Integer suggestedTotalQty;
    private Double confidence;
    private Long sampleCount;
    private List<String> nameCandidates;
}
