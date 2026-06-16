package com.kuji.backend.global.infra.naver;

import com.kuji.backend.global.infra.naver.dto.NaverUserInfoResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NaverClient {

    private final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    public NaverUserInfoResponse getNaverUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정 (Authorization: Bearer {accessToken})
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. 네이버 서버로 GET 요청 보내기
        ResponseEntity<NaverUserInfoResponse> response = restTemplate.exchange(
                NAVER_USER_INFO_URL,
                org.springframework.http.HttpMethod.GET,
                entity,
                NaverUserInfoResponse.class
        );

        // 3. 결과 반환
        return response.getBody();
    }
}
