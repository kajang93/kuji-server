// 포트원 V2 결제 조회·취소 API 클라이언트.
// 결제창에서 결제가 끝나면 앱이 결제 ID(= 우리 주문번호)로 확인을 요청하고, 서버는 포트원에서 그 결제를 직접 조회해
// 상점·채널 환경·상태·금액·통화가 준비된 주문과 일치할 때만 주문을 처리한다. 처리에 실패하면 전액 취소한다.
package com.kuji.backend.global.infra.portone;

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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class PortOnePaymentClient {
    private final String apiSecret;
    private final String storeId;
    private final boolean live;
    private final String apiBase;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortOnePaymentClient(@Value("${portone.api-secret:}") String apiSecret,
                                @Value("${portone.store-id:}") String storeId,
                                @Value("${portone.live:false}") boolean live,
                                @Value("${portone.api-url:https://api.portone.io}") String apiUrl) {
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.storeId = storeId == null ? "" : storeId.trim();
        this.live = live;
        this.apiBase = apiUrl.trim().replaceAll("/+$", "");
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new RestTemplate(factory);
    }

    /** 실결제 채널로 운영 중이면 true. 정산 기록의 운영·테스트 구분에도 쓴다. */
    public Boolean isLive() { return live; }

    /**
     * 결제창에서 끝난 결제를 확인한다. 포트원 일반 결제는 결제창에서 이미 승인되므로 여기서는 조회·검증만 한다.
     * 검증에 실패해 예외가 나면 결제 복구 흐름이 이 결제를 전액 취소한다.
     */
    public void confirmPayment(String paymentId, String orderId, Integer amount) {
        validateRequest(paymentId, orderId, amount);
        PaymentDetails payment = findPayment(paymentId, orderId, amount)
                .orElseThrow(() -> new IllegalStateException("결제 내역을 찾지 못했습니다. 잠시 후 결제 상태를 확인해주세요."));
        if (payment.isUnpaidTerminal()) throw new IllegalStateException("카드 결제가 승인되지 않았습니다. 다시 시도해 주세요.");
        // 운영 중에는 자동 환불되는 테스트 채널 결제로 상품을 받아 가지 못하게 막는다 (이 결제는 복구 흐름이 취소한다).
        if (live && !"LIVE".equals(payment.channelType())) {
            throw new IllegalStateException("테스트 결제로는 주문할 수 없습니다.");
        }
        if (!payment.isPaid() || payment.balanceAmount() != amount) {
            throw new IllegalStateException("완료된 전액 결제가 아니므로 주문을 처리하지 않았습니다. 결제 상태를 확인해주세요.");
        }
    }

    public Optional<PaymentDetails> findPayment(String paymentId, String orderId, Integer amount) {
        validateRequest(paymentId, orderId, amount);
        try {
            PaymentDetails payment = parse(restTemplate.exchange(paymentUri(paymentId, null), HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class));
            verifyIdentity(payment, paymentId, orderId, amount);
            return Optional.of(payment);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw new IllegalStateException("결제 상태를 조회하지 못했습니다. 잠시 후 다시 확인해주세요.");
        } catch (RestClientException e) {
            throw new IllegalStateException("결제 상태 조회 응답이 지연되고 있습니다.");
        }
    }

    public void cancelPayment(String paymentId, String orderId, Integer amount) {
        validateRequest(paymentId, orderId, amount);
        try {
            restTemplate.exchange(paymentUri(paymentId, "cancel"), HttpMethod.POST,
                    new HttpEntity<>(Map.of("reason", "주문 처리 실패에 따른 자동 결제 취소"), headers()), String.class);
        } catch (RestClientException e) {
            // 이미 취소된 결제(409)나 응답 유실은 아래 조회로 실제 취소 여부를 확정한다.
        }
        PaymentDetails payment = findPayment(paymentId, orderId, amount)
                .orElseThrow(() -> new IllegalStateException("결제 취소 결과를 아직 확인하지 못했습니다."));
        if (!payment.isFullyCancelled()) throw new IllegalStateException("전액 취소가 확인되지 않아 결제 복구를 계속합니다.");
    }

    /** 결제 ID는 우리가 만든 주문번호와 같아야 한다. */
    public static void validateRequest(String paymentId, String orderId, Integer amount) {
        if (paymentId == null || !paymentId.matches("[A-Za-z0-9_-]{1,64}")
                || orderId == null || !orderId.equals(paymentId) || amount == null || amount < 1) {
            throw new IllegalArgumentException("결제 승인 요청 정보가 올바르지 않습니다.");
        }
    }

    private HttpHeaders headers() {
        if (apiSecret.isBlank() || storeId.isBlank()) throw new IllegalStateException("결제 설정을 확인해주세요.");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private URI paymentUri(String paymentId, String action) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiBase).pathSegment("payments", paymentId);
        if (action != null) builder.pathSegment(action);
        return builder.queryParam("storeId", storeId).build().encode().toUri();
    }

    private PaymentDetails parse(ResponseEntity<String> response) {
        try {
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw new IllegalStateException();
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode amount = body.path("amount");
            JsonNode total = amount.path("total");
            JsonNode cancelled = amount.path("cancelled");
            if (!total.isIntegralNumber() || !total.canConvertToInt()) throw new IllegalStateException();
            int cancelledAmount = cancelled.isIntegralNumber() && cancelled.canConvertToInt() ? cancelled.intValue() : 0;
            return new PaymentDetails(text(body, "id"), text(body, "storeId"), text(body.path("channel"), "type"),
                    total.intValue(), total.intValue() - cancelledAmount, text(body, "status"), text(body, "currency"));
        } catch (Exception e) {
            throw new IllegalStateException("결제 응답을 검증하지 못했습니다. 결제 상태를 확인해주세요.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.path(field);
        return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private void verifyIdentity(PaymentDetails payment, String paymentId, String orderId, int amount) {
        if (!paymentId.equals(payment.paymentId()) || !orderId.equals(payment.paymentId())
                || !storeId.equals(payment.storeId()) || payment.totalAmount() != amount || !"KRW".equals(payment.currency())
                || payment.balanceAmount() < 0 || payment.balanceAmount() > amount) {
            throw new IllegalStateException("결제 응답이 주문 정보와 일치하지 않습니다.");
        }
    }

    public record PaymentDetails(String paymentId, String storeId, String channelType, int totalAmount, int balanceAmount,
                                 String status, String currency) {
        public boolean isPaid() { return "PAID".equals(status); }
        public boolean isFullyCancelled() { return "CANCELLED".equals(status) && balanceAmount == 0; }
        public boolean isUnpaidTerminal() { return "FAILED".equals(status); }
        public boolean canCompensate() { return "PAID".equals(status) || "PARTIAL_CANCELLED".equals(status); }
    }
}
