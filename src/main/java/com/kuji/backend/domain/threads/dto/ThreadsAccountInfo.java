package com.kuji.backend.domain.threads.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadsAccountInfo {
    private String id;
    private String username;
    private String name;
}
