package com.kuji.backend.global.infra.naver.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NaverUserInfoResponse {
    private String resultcode;
    private String message;
    private Response response;

    @Getter
    @Setter
    public static class Response {
        private String id;
        private String email;
        private String nickname;
        private String profile_image;
        private String mobile;
    }

    public String getId() {
        return response != null ? response.getId() : null;
    }

    public String getEmail() {
        return response != null ? response.getEmail() : null;
    }

    public String getNickname() {
        return response != null ? response.getNickname() : null;
    }

    public String getProfileImageUrl() {
        return response != null ? response.getProfile_image() : null;
    }
}
