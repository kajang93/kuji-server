package com.kuji.backend.domain.inquiry.dto;

import com.kuji.backend.domain.inquiry.enums.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InquiryCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        @NotNull(message = "문의 유형은 필수입니다.")
        InquiryCategory inquiryType,

        Long shippingId
) {
}
