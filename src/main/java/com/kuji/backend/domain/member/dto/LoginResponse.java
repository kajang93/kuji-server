package com.kuji.backend.domain.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String token;
    private Boolean isNewUser;
    
    // 신규 가입 시 필요한 닉네임, 이메일 등의 정보를 미리 보내줄 수 있습니다.
    private String email;
    private String nickname;
    private String profileImageUrl;
}
