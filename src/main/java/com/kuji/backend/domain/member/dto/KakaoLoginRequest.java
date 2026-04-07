package com.kuji.backend.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {

    @NotBlank(message = "카카오 액세스 토큰은 필수입니다.")
    private String kakaoAccessToken;

    // 💡 선택적으로 전달받는 약관 동의 여부 필드 추가
    private Boolean isTermsAgreed;
    private Boolean isPrivacyAgreed;
    private Boolean isMarketingAgreed;
}
