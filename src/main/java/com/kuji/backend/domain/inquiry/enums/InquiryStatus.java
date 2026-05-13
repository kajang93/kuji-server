package com.kuji.backend.domain.inquiry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InquiryStatus {
    WAITING("답변대기"),
    COMPLETED("답변완료");

    private final String description;
}
