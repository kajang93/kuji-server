package com.kuji.backend.domain.kuji.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KujiItemResponse {
    private Long id;
    private String grade;
    private String name;
    private Integer totalQty;
    private Integer remainQty;
    private List<String> imageUrls;
    private List<Boolean> opened; // 각 아이템별 오픈 상태 리스트 추가
    private Long drawHistoryId; // 개별 당첨 이력 ID 추가
    private String options; // 옵션 정보 (JSON)
}
