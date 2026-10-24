package com.kuji.backend.domain.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {

    private String kakaoAccessToken;
    private String authorizationCode;
    private String redirectUri;

    // 💡 선택적으로 전달받는 약관 동의 여부 필드 추가
    private Boolean isTermsAgreed;
    private Boolean isPrivacyAgreed;
    private Boolean isMarketingAgreed;
}
