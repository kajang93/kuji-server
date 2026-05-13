package com.kuji.backend.domain.inquiry.dto;

import com.kuji.backend.domain.inquiry.entity.Inquiry;
import com.kuji.backend.domain.inquiry.enums.InquiryCategory;
import com.kuji.backend.domain.inquiry.enums.InquiryStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record InquiryResponse(
        Long id,
        String title,
        String content,
        InquiryCategory inquiryType,
        String categoryDescription,
        InquiryStatus status,
        String statusDescription,
        String answerContent,
        LocalDateTime answeredAt,
        Long shippingId,
        LocalDateTime createdAt
) {
    public static InquiryResponse from(Inquiry inquiry) {
        return InquiryResponse.builder()
                .id(inquiry.getId())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .inquiryType(inquiry.getInquiryType())
                .categoryDescription(inquiry.getInquiryType().getDescription())
                .status(inquiry.getStatus())
                .statusDescription(inquiry.getStatus().getDescription())
                .answerContent(inquiry.getAnswerContent())
                .answeredAt(inquiry.getAnsweredAt())
                .shippingId(inquiry.getShippingId())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
