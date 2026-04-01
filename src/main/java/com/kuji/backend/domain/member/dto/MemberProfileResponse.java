package com.kuji.backend.domain.member.dto;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.member.enums.RoleType;

import java.time.LocalDate;

public record MemberProfileResponse(
        Long memberId,
        String email,
        String nickname,
        LocalDate birthDate,
        RoleType role) {
    // 💡 Entity를 DTO로 변환해 주는 깔끔한 편의 메서드!
    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getBirthDate(),
                member.getRole());
    }
}