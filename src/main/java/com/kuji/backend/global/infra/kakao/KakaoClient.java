package com.kuji.backend.global.infra.kakao;

import com.kuji.backend.global.infra.ExternalApiHttpClient;
import com.kuji.backend.global.infra.kakao.dto.KakaoUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;

@Component
public class KakaoClient {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final Set<String> ALLOWED_REDIRECT_URIS = Set.of(
            "https://kujishop.shop/auth/kakao/callback",
            "https://www.kujishop.shop/auth/kakao/callback",
            "http://localhost:5173/auth/kakao/callback",
            "http://127.0.0.1:5173/auth/kakao/callback");

    @Value("${kakao.rest-api-key:}")
    private String restApiKey;

    @Value("${kakao.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate = ExternalApiHttpClient.createRestTemplate();

    public String exchangeAuthorizationCode(String authorizationCode, String redirectUri) {
        if (redirectUri == null || !ALLOWED_REDIRECT_URIS.contains(redirectUri)) {
            throw new IllegalArgumentException("허용되지 않은 카카오 로그인 리다이렉트 주소입니다.");
        }
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("카카오 인가 코드가 필요합니다.");
        }
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new IllegalStateException("카카오 로그인 서버 설정이 준비되지 않았습니다.");
        }
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", restApiKey);
        params.add("redirect_uri", redirectUri);
        params.add("code", authorizationCode);
        if (clientSecret != null && !clientSecret.isBlank()) {
            params.add("client_secret", clientSecret);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(KAKAO_TOKEN_URL,
                    new HttpEntity<>(params, headers), Map.class);
            Map<?, ?> body = response.getBody();
            if (body != null && body.get("access_token") instanceof String token && !token.isBlank()) {
                return token;
            }
        } catch (RestClientException e) {
            throw new IllegalArgumentException("카카오 인가 코드를 확인하지 못했습니다. 다시 로그인해주세요.");
        }
        throw new IllegalArgumentException("카카오 로그인 응답을 확인하지 못했습니다.");
    }

    public KakaoUserInfoResponse getKakaoUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("카카오 액세스 토큰 또는 인가 코드가 필요합니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<KakaoUserInfoResponse> response = restTemplate.exchange(
                    KAKAO_USER_INFO_URL, HttpMethod.GET, new HttpEntity<>(headers), KakaoUserInfoResponse.class);
            KakaoUserInfoResponse body = response.getBody();
            if (body != null && body.getId() != null) {
                return body;
            }
        } catch (RestClientException e) {
            throw new IllegalArgumentException("카카오 계정을 확인하지 못했습니다. 다시 로그인해주세요.");
        }
        throw new IllegalArgumentException("카카오 사용자 정보를 확인하지 못했습니다.");
    }
}
