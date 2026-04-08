package com.kuji.backend.domain.member.dto;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;

import java.time.LocalDate;

/**
 * 회원 프로필 정보 응답 DTO (Record 사용)
 */
public record MemberProfileResponse(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDate birthDate,
        RoleType role) {

    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getBirthDate(),
                member.getRole());
    }
}