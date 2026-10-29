// 가입에 사용할 일회용 휴대폰 인증 증명과 만료 시각을 전달합니다.
package com.kuji.backend.domain.member.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

public record SignupPhoneVerificationResponse(String verificationToken, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt) {}
