// 나이스페이먼츠 결제창(Server 승인 모델)의 승인·조회·취소 API 클라이언트.
// 결제 키(paymentKey)는 나이스 거래 키(tid)이며, 응답은 항상 주문번호·금액·위변조 서명까지 확인한 뒤에만 신뢰한다.
package com.kuji.backend.global.infra.nice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Component
public class NicePaymentClient {
    private static final String LIVE_API = "https://api.nicepay.co.kr";
    private static final String SANDBOX_API = "https://sandbox-api.nicepay.co.kr";

    private final String clientId;
    private final String secretKey;
    private final String apiBase;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NicePaymentClient(@Value("${nice.client-id:}") String clientId,
                             @Value("${nice.secret-key:}") String secretKey,
                             @Value("${nice.api-url:}") String apiUrl) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        // 운영 키는 R1_/R2_, 샌드박스 키는 S1_/S2_ 로 시작한다. 별도 지정이 없으면 키 종류로 API 도메인을 고른다.
        this.apiBase = apiUrl != null && !apiUrl.isBlank() ? apiUrl.trim().replaceAll("/+$", "")
                : this.clientId.startsWith("S") ? SANDBOX_API : LIVE_API;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    /** 운영 키면 LIVE, 샌드박스 키면 TEST, 그 외에는 null. */
    public Boolean isLive() {
        if (clientId.startsWith("R")) return true;
        if (clientId.startsWith("S")) return false;
        return null;
    }

    /** 결제창 인증 결과의 위변조 서명 확인: hex(sha256(authToken + clientId + amount + secretKey)). */
    public boolean verifyAuthSignature(String authToken, String responseClientId, String amount, String signature) {
        if (secretKey.isBlank() || authToken == null || signature == null || amount == null
                || !clientId.equals(responseClientId)) return false;
        return MessageDigest.isEqual(sha256Hex(authToken + clientId + amount + secretKey).getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** 결제창 인증(tid)을 실제 결제로 승인한다. 응답 유실 시 거래 조회로만 결과를 확정한다. */
    public void confirmPayment(String tid, String orderId, Integer amount) {
        validateRequest(tid, orderId, amount);
        PaymentDetails payment;
        try {
            payment = parse(restTemplate.exchange(paymentUri(tid), HttpMethod.POST,
                    new HttpEntity<>(Map.of("amount", amount), headers()), String.class));
            if (!payment.success()) {
                // 이미 승인된 재요청 등은 결과 코드가 실패로 와도 거래 조회로 실제 상태를 확인한다.
                payment = findPayment(tid, orderId, amount).filter(PaymentDetails::isPaid)
                        .orElseThrow(() -> new IllegalStateException("카드 결제가 승인되지 않았습니다. 다시 시도해 주세요."));
            }
        } catch (RestClientException e) {
            payment = findPayment(tid, orderId, amount).orElseGet(() -> {
                // 승인 결과를 알 수 없으면 나이스 권고대로 망취소해 결제가 남지 않게 한다.
                netCancel(orderId);
                throw new IllegalStateException("결제 승인 응답이 지연되어 결제를 취소했습니다. 다시 시도해 주세요.");
            });
        }
        verifyIdentity(payment, tid, orderId, amount);
        if (!payment.isPaid() || payment.balanceAmount() != amount) {
            throw new IllegalStateException("완료된 전액 결제가 아니므로 주문을 처리하지 않았습니다. 결제 상태를 확인해주세요.");
        }
    }

    public Optional<PaymentDetails> findPayment(String tid, String orderId, Integer amount) {
        validateRequest(tid, orderId, amount);
        try {
            PaymentDetails payment = parse(restTemplate.exchange(paymentUri(tid), HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class));
            if (payment.status() == null || payment.tid() == null) return Optional.empty();
            verifyIdentity(payment, tid, orderId, amount);
            return Optional.of(payment);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw new IllegalStateException("결제 상태를 조회하지 못했습니다. 잠시 후 다시 확인해주세요.");
        } catch (RestClientException e) {
            throw new IllegalStateException("결제 상태 조회 응답이 지연되고 있습니다.");
        }
    }

    public void cancelPayment(String tid, String orderId, Integer amount) {
        validateRequest(tid, orderId, amount);
        PaymentDetails payment;
        try {
            URI uri = UriComponentsBuilder.fromUriString(apiBase).pathSegment("v1", "payments", tid, "cancel").build().encode().toUri();
            payment = parse(restTemplate.exchange(uri, HttpMethod.POST,
                    new HttpEntity<>(Map.of("reason", "주문 처리 실패에 따른 자동 결제 취소", "orderId", orderId), headers()), String.class));
            if (!payment.success() || payment.status() == null) {
                payment = findPayment(tid, orderId, amount)
                        .orElseThrow(() -> new IllegalStateException("결제 취소 결과를 아직 확인하지 못했습니다."));
            }
        } catch (RestClientException e) {
            // 취소 응답도 유실될 수 있으므로 취소 완료 여부를 다시 조회합니다.
            payment = findPayment(tid, orderId, amount)
                    .orElseThrow(() -> new IllegalStateException("결제 취소 결과를 아직 확인하지 못했습니다."));
        }
        verifyIdentity(payment, tid, orderId, amount);
        if (!payment.isFullyCancelled()) throw new IllegalStateException("전액 취소가 확인되지 않아 결제 복구를 계속합니다.");
    }

    /** 승인 결과가 불확실할 때의 망취소(요청 후 1시간 이내만 유효). 실패해도 보상 작업이 거래 조회로 다시 확인한다. */
    private void netCancel(String orderId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(apiBase).pathSegment("v1", "payments", "netcancel").build().encode().toUri();
            restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(Map.of("orderId", orderId), headers()), String.class);
        } catch (RuntimeException ignored) {
            // 결제 키·PG 원문은 남기지 않는다. 보상 스케줄러가 다시 확인한다.
        }
    }

    public static void validateRequest(String tid, String orderId, Integer amount) {
        if (tid == null || tid.isBlank() || tid.length() > 200 || !tid.matches("[A-Za-z0-9_-]+")
                || orderId == null || orderId.isBlank() || orderId.length() > 64 || amount == null || amount < 1) {
            throw new IllegalArgumentException("결제 승인 요청 정보가 올바르지 않습니다.");
        }
    }

    private HttpHeaders headers() {
        if (clientId.isBlank() || secretKey.isBlank()) throw new IllegalStateException("결제 설정을 확인해주세요.");
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, secretKey, StandardCharsets.UTF_8);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private URI paymentUri(String tid) {
        return UriComponentsBuilder.fromUriString(apiBase).pathSegment("v1", "payments", tid).build().encode().toUri();
    }

    private PaymentDetails parse(ResponseEntity<String> response) {
        try {
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw new IllegalStateException();
            JsonNode body = objectMapper.readTree(response.getBody());
            String resultCode = body.path("resultCode").asText("");
            JsonNode amountNode = body.path("amount");
            JsonNode balanceNode = body.path("balanceAmt");
            boolean hasAmounts = amountNode.isIntegralNumber() && amountNode.canConvertToInt()
                    && balanceNode.isIntegralNumber() && balanceNode.canConvertToInt();
            String tid = text(body, "tid");
            String status = text(body, "status");
            String ediDate = text(body, "ediDate");
            String signature = text(body, "signature");
            if (status != null && !hasAmounts) throw new IllegalStateException();
            // 서명이 오면 반드시 일치해야 한다: hex(sha256(tid + amount + ediDate + secretKey))
            if (signature != null && tid != null && ediDate != null && hasAmounts
                    && !MessageDigest.isEqual(sha256Hex(tid + amountNode.intValue() + ediDate + secretKey).getBytes(StandardCharsets.UTF_8),
                    signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalStateException();
            }
            return new PaymentDetails("0000".equals(resultCode), tid, text(body, "orderId"),
                    hasAmounts ? amountNode.intValue() : 0, hasAmounts ? balanceNode.intValue() : 0,
                    status, text(body, "currency"));
        } catch (Exception e) {
            throw new IllegalStateException("결제 응답을 검증하지 못했습니다. 결제 상태를 확인해주세요.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.path(field);
        return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private void verifyIdentity(PaymentDetails payment, String tid, String orderId, int amount) {
        if (!tid.equals(payment.tid()) || !orderId.equals(payment.orderId())
                || payment.totalAmount() != amount || !"KRW".equals(payment.currency())
                || payment.balanceAmount() < 0 || payment.balanceAmount() > amount) {
            throw new IllegalStateException("결제 응답이 주문 정보와 일치하지 않습니다.");
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("서명 계산에 실패했습니다.");
        }
    }

    public record PaymentDetails(boolean success, String tid, String orderId, int totalAmount, int balanceAmount,
                                 String status, String currency) {
        public boolean isPaid() { return "paid".equals(status); }
        public boolean isFullyCancelled() { return "cancelled".equals(status) && balanceAmount == 0; }
        public boolean isUnpaidTerminal() { return "failed".equals(status) || "expired".equals(status); }
        public boolean canCompensate() { return "paid".equals(status) || "partialCancelled".equals(status); }
    }
}
