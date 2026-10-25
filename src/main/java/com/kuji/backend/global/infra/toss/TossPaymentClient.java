package com.kuji.backend.global.infra.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuji.backend.global.infra.toss.dto.TossConfirmRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class TossPaymentClient {
    @Value("${toss.secret-key}")
    private String secretKey;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PAYMENTS_URL = "https://api.tosspayments.com/v1/payments";

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    public void confirmPayment(String paymentKey, String orderId, Integer amount) {
        validateRequest(paymentKey, orderId, amount);
        PaymentDetails payment;
        try {
            payment = parse(restTemplate.exchange(URI.create(PAYMENTS_URL + "/confirm"), HttpMethod.POST,
                    new HttpEntity<>(new TossConfirmRequest(paymentKey, orderId, amount), headers("confirm", orderId)), String.class));
        } catch (RestClientException e) {
            // 승인 응답 유실이나 이미 승인된 재요청은 PG 조회 결과로만 복구합니다.
            payment = findPayment(paymentKey, orderId, amount)
                    .orElseThrow(() -> new IllegalStateException("결제 승인 결과를 확인하고 있습니다. 잠시 후 결제 상태를 확인해주세요."));
        }
        verifyIdentity(payment, paymentKey, orderId, amount);
        if (!"DONE".equals(payment.status()) || payment.balanceAmount() != amount || payment.approvedAt() == null) {
            throw new IllegalStateException("완료된 전액 결제가 아니므로 주문을 처리하지 않았습니다. 결제 상태를 확인해주세요.");
        }
    }

    public Optional<PaymentDetails> findPayment(String paymentKey, String orderId, Integer amount) {
        validateRequest(paymentKey, orderId, amount);
        try {
            PaymentDetails payment = parse(restTemplate.exchange(paymentUri(paymentKey), HttpMethod.GET,
                    new HttpEntity<>(headers(null, orderId)), String.class));
            verifyIdentity(payment, paymentKey, orderId, amount);
            return Optional.of(payment);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw new IllegalStateException("결제 상태를 조회하지 못했습니다. 잠시 후 다시 확인해주세요.");
        } catch (RestClientException e) {
            throw new IllegalStateException("결제 상태 조회 응답이 지연되고 있습니다.");
        }
    }

    public void cancelPayment(String paymentKey, String orderId, Integer amount) {
        validateRequest(paymentKey, orderId, amount);
        PaymentDetails payment;
        try {
            URI uri = UriComponentsBuilder.fromUriString(PAYMENTS_URL).pathSegment(paymentKey, "cancel").build().encode().toUri();
            payment = parse(restTemplate.exchange(uri, HttpMethod.POST,
                    new HttpEntity<>(Map.of("cancelReason", "주문 처리 실패에 따른 자동 결제 취소"), headers("compensate", orderId)), String.class));
        } catch (RestClientException e) {
            // 취소 응답도 유실될 수 있으므로 취소 완료 여부를 다시 조회합니다.
            payment = findPayment(paymentKey, orderId, amount)
                    .orElseThrow(() -> new IllegalStateException("결제 취소 결과를 아직 확인하지 못했습니다."));
        }
        verifyIdentity(payment, paymentKey, orderId, amount);
        if (!payment.isFullyCancelled()) throw new IllegalStateException("전액 취소가 확인되지 않아 결제 복구를 계속합니다.");
    }

    public static void validateRequest(String paymentKey, String orderId, Integer amount) {
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200
                || orderId == null || orderId.isBlank() || orderId.length() > 100 || amount == null || amount < 1) {
            throw new IllegalArgumentException("결제 승인 요청 정보가 올바르지 않습니다.");
        }
    }

    private HttpHeaders headers(String operation, String orderId) {
        if (secretKey == null || secretKey.isBlank()) throw new IllegalStateException("결제 설정을 확인해주세요.");
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(secretKey, "", StandardCharsets.UTF_8);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (operation != null) {
            String id = UUID.nameUUIDFromBytes(orderId.getBytes(StandardCharsets.UTF_8)).toString();
            headers.set("Idempotency-Key", "kuji-" + operation + "-" + id);
        }
        return headers;
    }

    private URI paymentUri(String paymentKey) {
        return UriComponentsBuilder.fromUriString(PAYMENTS_URL).pathSegment(paymentKey).build().encode().toUri();
    }

    private PaymentDetails parse(ResponseEntity<String> response) {
        try {
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw new IllegalStateException();
            JsonNode body = objectMapper.readTree(response.getBody());
            if (!body.path("totalAmount").isIntegralNumber() || !body.path("totalAmount").canConvertToInt()
                    || !body.path("balanceAmount").isIntegralNumber() || !body.path("balanceAmount").canConvertToInt()) {
                throw new IllegalStateException();
            }
            String approvedAt = body.path("approvedAt").asText(null);
            return new PaymentDetails(body.path("paymentKey").asText(null), body.path("orderId").asText(null),
                    body.path("totalAmount").intValue(), body.path("balanceAmount").intValue(),
                    body.path("status").asText(null), body.path("currency").asText(null),
                    approvedAt == null ? null : OffsetDateTime.parse(approvedAt));
        } catch (Exception e) {
            throw new IllegalStateException("결제 응답을 검증하지 못했습니다. 결제 상태를 확인해주세요.");
        }
    }

    private void verifyIdentity(PaymentDetails payment, String paymentKey, String orderId, int amount) {
        if (!paymentKey.equals(payment.paymentKey()) || !orderId.equals(payment.orderId())
                || payment.totalAmount() != amount || !"KRW".equals(payment.currency())
                || payment.balanceAmount() < 0 || payment.balanceAmount() > amount) {
            throw new IllegalStateException("결제 응답이 주문 정보와 일치하지 않습니다.");
        }
    }

    public record PaymentDetails(String paymentKey, String orderId, int totalAmount, int balanceAmount,
                                 String status, String currency, OffsetDateTime approvedAt) {
        public boolean isFullyCancelled() { return "CANCELED".equals(status) && balanceAmount == 0; }
        public boolean isUnpaidTerminal() { return "ABORTED".equals(status) || "EXPIRED".equals(status); }
        public boolean canCompensate() {
            return "DONE".equals(status) || "PARTIAL_CANCELED".equals(status) || "WAITING_FOR_DEPOSIT".equals(status);
        }
    }
}
