package com.kuji.backend.global.infra.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoUserInfoResponse {

    private Long id; // 카카오 고유 ID

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {
        private String email;
        private Profile profile;

        @Getter
        @NoArgsConstructor
        public static class Profile {
            private String nickname;
            @JsonProperty("profile_image_url")
            private String profileImageUrl;
        }
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.getEmail() : null;
    }

    public String getNickname() {
        return (kakaoAccount != null && kakaoAccount.getProfile() != null) 
                ? kakaoAccount.getProfile().getNickname() : null;
    }

    public String getProfileImageUrl() {
        return (kakaoAccount != null && kakaoAccount.getProfile() != null) 
                ? kakaoAccount.getProfile().getProfileImageUrl() : null;
    }
}
