// 구글 액세스 토큰이 우리 앱에 발급된 것인지 확인한 뒤 사용자 정보를 조회한다.
package com.kuji.backend.global.infra.google;

import com.kuji.backend.global.infra.google.dto.GoogleUserInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class GoogleClient {

    private static final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?access_token={token}";

    // 프론트의 VITE_GOOGLE_CLIENT_ID 와 같은 값 (비밀값 아님). 설정되면 다른 앱용 토큰으로 로그인하는 것을 막는다.
    @Value("${google.client-id:}")
    private String clientId;

    public GoogleUserInfoResponse getGoogleUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        verifyAudience(restTemplate, accessToken);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        ResponseEntity<GoogleUserInfoResponse> response = restTemplate.exchange(
                GOOGLE_USER_INFO_URL, HttpMethod.GET, new HttpEntity<>(headers), GoogleUserInfoResponse.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private void verifyAudience(RestTemplate restTemplate, String accessToken) {
        if (clientId == null || clientId.isBlank()) {
            log.warn("[구글 로그인] google.client-id 미설정: 토큰 발급 대상 앱 확인을 건너뜁니다. 서버 설정에 클라이언트 ID를 등록하세요.");
            return;
        }
        Map<String, Object> info;
        try {
            info = restTemplate.getForObject(GOOGLE_TOKEN_INFO_URL, Map.class, accessToken);
        } catch (RestClientException e) {
            throw new IllegalArgumentException("구글 로그인 정보를 확인하지 못했습니다. 다시 시도해 주세요.");
        }
        Object aud = info == null ? null : info.get("aud");
        Object azp = info == null ? null : info.get("azp");
        if (!clientId.equals(aud) && !clientId.equals(azp)) {
            log.warn("[구글 로그인] 다른 앱에 발급된 토큰으로 로그인 시도가 차단되었습니다.");
            throw new IllegalArgumentException("유효하지 않은 구글 로그인 요청입니다.");
        }
    }
}
