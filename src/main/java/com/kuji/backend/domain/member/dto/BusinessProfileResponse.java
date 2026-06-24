package com.kuji.backend.domain.member.dto;

import com.kuji.backend.domain.member.entity.BusinessInfo;

public record BusinessProfileResponse(
        String businessNumber,
        String companyName,
        String ceoName,
        String licenseImageUrl,
        String status,
        String rejectReason,
        java.time.LocalDateTime createdAt
) {
    public static BusinessProfileResponse from(BusinessInfo info) {
        return new BusinessProfileResponse(
                info.getBusinessNumber(),
                info.getCompanyName(),
                info.getCeoName(),
                info.getLicenseImageUrl(),
                info.getStatus().name(),
                info.getRejectReason(),
                info.getCreatedAt()
        );
    }
}
