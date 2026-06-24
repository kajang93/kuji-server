package com.kuji.backend.domain.member.dto;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword
) {}
