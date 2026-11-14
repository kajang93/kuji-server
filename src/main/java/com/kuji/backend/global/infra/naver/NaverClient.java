// 네이버 인가 코드를 서버에서 우리 앱의 시크릿으로 토큰으로 바꾸고 사용자 정보를 조회한다.
package com.kuji.backend.global.infra.naver;

import com.kuji.backend.global.infra.naver.dto.NaverUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class NaverClient {

    private static final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";
    private static final String NAVER_TOKEN_URL = "https://nid.naver.com/oauth2.0/token"
            + "?grant_type=authorization_code&client_id={id}&client_secret={secret}&code={code}&state={state}";

    @Value("${naver.client-id:}")
    private String clientId;

    @Value("${naver.client-secret:}")
    private String clientSecret;

    /** 서버에 네이버 앱 시크릿이 설정되어 있으면 인가 코드 방식만 허용한다. */
    public boolean codeFlowConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    @SuppressWarnings("unchecked")
    public String exchangeAuthorizationCode(String code, String state) {
        if (!codeFlowConfigured()) {
            throw new IllegalStateException("네이버 로그인 서버 설정이 준비되지 않았습니다.");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException("네이버 인가 코드가 필요합니다.");
        }
        try {
            Map<String, Object> body = new RestTemplate().getForObject(NAVER_TOKEN_URL, Map.class,
                    clientId, clientSecret, code, state);
            if (body != null && body.get("access_token") instanceof String token && !token.isBlank()) {
                return token;
            }
        } catch (RestClientException e) {
            throw new IllegalArgumentException("네이버 인가 코드를 확인하지 못했습니다. 다시 로그인해 주세요.");
        }
        throw new IllegalArgumentException("네이버 로그인 응답을 확인하지 못했습니다. 다시 로그인해 주세요.");
    }

    public NaverUserInfoResponse getNaverUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("네이버 인가 코드가 필요합니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        ResponseEntity<NaverUserInfoResponse> response = new RestTemplate().exchange(
                NAVER_USER_INFO_URL, HttpMethod.GET, new HttpEntity<>(headers), NaverUserInfoResponse.class);
        return response.getBody();
    }
}
