package com.kuji.backend.domain.member.dto;

public record ResetPasswordRequest(
        String email,
        String phoneNumber
) {
}
