package com.kuji.backend.domain.kuji.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KujiDrawResponse {
    private List<KujiItemResponse> results;
    private Integer totalRemaining;
}
