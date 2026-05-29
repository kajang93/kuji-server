package com.kuji.backend.global.infra.toss;

import com.kuji.backend.global.infra.toss.dto.TossConfirmRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TossPaymentClient {

    @Value("${toss.secret-key}")
    private String secretKey;

    private final String TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    public void confirmPayment(String paymentKey, String orderId, Integer amount) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        // 시크릿 키 뒤에 콜론(:)을 붙여 Base64 인코딩
        String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);

        TossConfirmRequest request = new TossConfirmRequest(paymentKey, orderId, amount);
        HttpEntity<TossConfirmRequest> entity = new HttpEntity<>(request, headers);

        // 실패 시 RestTemplate이 예외를 던지므로 자연스럽게 트랜잭션이 롤백됩니다.
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(TOSS_CONFIRM_URL, entity, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException("결제 승인에 실패했습니다.");
            }
        } catch (RestClientResponseException e) {
            // 서버 로그(콘솔)에는 상세 JSON 에러를 남겨서 개발자가 디버깅할 수 있게 합니다.
            System.err.println("토스 결제 승인 실패 응답: " + e.getResponseBodyAsString());
            
            // 프론트엔드(클라이언트)에는 원시 JSON 대신 안전하고 정제된 메시지만 전달합니다. (보안 및 UX 목적)
            throw new IllegalArgumentException("PG사 결제 승인 중 오류가 발생했습니다. 결제 정보를 다시 확인해주세요.");
        }
    }
}
