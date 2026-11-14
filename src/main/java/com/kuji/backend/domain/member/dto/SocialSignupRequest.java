// 간편 로그인 신규 회원의 약관 동의 결과로 가입을 완료하는 요청.
package com.kuji.backend.domain.member.dto;

public record SocialSignupRequest(
        String signupTicket,
        Boolean isAgeOver14,
        Boolean isTermsAgreed,
        Boolean isPrivacyAgreed,
        Boolean isMarketingAgreed
) {}
