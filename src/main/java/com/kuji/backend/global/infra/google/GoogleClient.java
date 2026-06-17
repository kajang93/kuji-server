package com.kuji.backend.global.infra.google;

import com.kuji.backend.global.infra.google.dto.GoogleUserInfoResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GoogleClient {

    private final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    public GoogleUserInfoResponse getGoogleUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<GoogleUserInfoResponse> response = restTemplate.exchange(
                GOOGLE_USER_INFO_URL,
                java.util.Objects.requireNonNull(org.springframework.http.HttpMethod.GET),
                entity,
                GoogleUserInfoResponse.class
        );

        return response.getBody();
    }
}
