package com.kuji.backend.domain.member.dto;

public record FindIdRequest(
        String phoneNumber,
        String verificationCode,
        String type
) {
}
