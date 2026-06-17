package com.kuji.backend.global.infra.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleUserInfoResponse {

    private String sub;          // 구글의 고유 사용자 ID
    private String email;        // 이메일
    private String name;         // 이름 (닉네임으로 사용)
    private String picture;      // 프로필 사진 URL

    // 구글은 sub 필드를 고유 식별자로 사용합니다.
    public String getId() {
        return sub;
    }

    public String getNickname() {
        return name;
    }

    public String getProfileImageUrl() {
        return picture;
    }
}
