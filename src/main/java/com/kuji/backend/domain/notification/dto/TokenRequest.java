package com.kuji.backend.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenRequest {
    
    @NotBlank(message = "토큰 값은 필수입니다.")
    private String token;

    @NotBlank(message = "플랫폼 정보는 필수입니다. (WEB, AOS, IOS)")
    private String platform;

    @NotBlank(message = "기기 식별자는 필수입니다.")
    private String deviceId;
}
