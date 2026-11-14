package com.kuji.backend.domain.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String token;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String refreshToken;

    private Boolean isNewUser;
    
    // 신규 가입 시 필요한 닉네임, 이메일 등의 정보를 미리 보내줄 수 있습니다.
    private String email;
    private String nickname;
    private String profileImageUrl;

    // 간편 로그인 신규 회원: 약관 동의 화면에서 가입을 마칠 때 쓰는 1회용 표 (15분 유효)
    private String signupTicket;
}
