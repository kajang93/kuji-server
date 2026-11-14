package com.kuji.backend.domain.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NaverLoginRequest {

    // 예전 방식(브라우저가 받은 토큰). 서버에 네이버 시크릿이 설정되면 더 이상 받지 않는다.
    private String naverAccessToken;
    // 권장 방식: 네이버 인가 코드와 state 를 서버가 직접 토큰으로 교환한다.
    private String authorizationCode;
    private String state;

    // 선택적으로 전달받는 약관 동의 여부 필드 추가
    private Boolean isTermsAgreed;
    private Boolean isPrivacyAgreed;
    private Boolean isMarketingAgreed;
}
