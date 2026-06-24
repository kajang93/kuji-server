package com.kuji.backend.domain.member.dto;

public record DirectResetPasswordRequest(
    String email,
    String phoneNumber,
    String verificationCode,
    String newPassword
) {}
