package com.kuji.backend.global.infra.kakao;

import com.kuji.backend.global.infra.kakao.dto.KakaoUserInfoResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KakaoClient {

    private final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    public KakaoUserInfoResponse getKakaoUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정 (Authorization: Bearer {accessToken})
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. 카카오 서버로 GET 요청 보내기
        ResponseEntity<KakaoUserInfoResponse> response = restTemplate.exchange(
                KAKAO_USER_INFO_URL,
                java.util.Objects.requireNonNull(org.springframework.http.HttpMethod.GET),
                entity,
                KakaoUserInfoResponse.class
        );

        // 3. 결과 반환
        return response.getBody();
    }
}
