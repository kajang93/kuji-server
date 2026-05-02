package com.kuji.backend.domain.kuji.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KujiItemCreateRequest {
    private String grade;
    private String name;
    private Integer totalQty;
}
