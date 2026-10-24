// 외부 서비스 통신의 연결과 응답 대기 시간을 제한합니다.
package com.kuji.backend.global.infra;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

public final class ExternalApiHttpClient {
    private ExternalApiHttpClient() {
    }

    public static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }
}
