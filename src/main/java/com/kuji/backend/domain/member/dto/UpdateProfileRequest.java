package com.kuji.backend.domain.member.dto;

/**
 * 회원 프로필 수정 요청 DTO
 * - nickname: null이면 기존 닉네임 유지
 * - profileImageUrl은 Controller에서 이미지 업로드 후 별도 처리
 */
public record UpdateProfileRequest(
        String nickname,
        String phoneNumber,
        java.time.LocalDate birthDate,
        String address,
        String addressDetail
) {}
