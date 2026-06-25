package com.kuji.backend.domain.member.dto;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;
import com.kuji.backend.domain.member.enums.SocialType;

import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long id,
        String email,
        String nickname,
        String phoneNumber,
        RoleType role,
        SocialType socialType,
        LocalDateTime createdAt,
        Integer baseFeeRate,
        LocalDateTime promotionEndAt
) {
    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getPhoneNumber(),
                member.getRole(),
                member.getSocialType(),
                member.getCreatedAt(),
                member.getBusinessInfo() != null ? member.getBusinessInfo().getBaseFeeRate() : null,
                member.getBusinessInfo() != null && member.getBusinessInfo().getPromotionEndAt() != null 
                        ? member.getBusinessInfo().getPromotionEndAt().toLocalDateTime() : null
        );
    }
}
