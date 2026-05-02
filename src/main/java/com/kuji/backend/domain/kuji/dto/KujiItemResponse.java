package com.kuji.backend.domain.kuji.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KujiItemResponse {
    private Long id;
    private String grade;
    private String name;
    private Integer totalQty;
    private Integer remainQty;
    private List<String> imageUrls;
}
