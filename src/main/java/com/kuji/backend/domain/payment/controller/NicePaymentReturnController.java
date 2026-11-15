// 나이스 결제창은 카드 인증이 끝나면 returnUrl 로 결과를 POST 한다.
// 여기서는 인증 결과와 위변조 서명만 확인하고 앱 화면으로 돌려보낸다. 실제 승인은 로그인한 사용자의 확인 요청에서
// 준비된 주문(금액·회원·상품)과 대조한 뒤 서버가 나이스 승인 API 로 처리한다.
package com.kuji.backend.domain.payment.controller;

import com.kuji.backend.global.infra.nice.NicePaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/nice")
public class NicePaymentReturnController {

    private final NicePaymentClient nice;

    @PostMapping(value = "/return", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleReturn(
            @RequestParam(value = "authResultCode", required = false) String authResultCode,
            @RequestParam(value = "tid", required = false) String tid,
            @RequestParam(value = "clientId", required = false) String clientId,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "amount", required = false) String amount,
            @RequestParam(value = "mallReserved", required = false) String mallReserved,
            @RequestParam(value = "authToken", required = false) String authToken,
            @RequestParam(value = "signature", required = false) String signature) {
        boolean points = "points".equals(mallReserved);
        UriComponentsBuilder target = UriComponentsBuilder.fromPath(points ? "/" : "/payment/return");
        String flag = points ? "pointCharge" : "payment";
        String safeOrderId = orderId != null && orderId.matches("[A-Za-z0-9_-]{1,64}") ? orderId : null;
        if (safeOrderId != null) target.queryParam("orderId", safeOrderId);

        boolean authenticated = "0000".equals(authResultCode) && tid != null && amount != null && amount.matches("[1-9][0-9]{0,11}")
                && safeOrderId != null && nice.verifyAuthSignature(authToken, clientId, amount, signature);
        if (authenticated) {
            try {
                NicePaymentClient.validateRequest(tid, safeOrderId, Integer.valueOf(amount));
                target.queryParam(flag, "success").queryParam("paymentKey", tid).queryParam("amount", amount);
            } catch (RuntimeException e) {
                authenticated = false;
            }
        }
        if (!authenticated) {
            // 사용자가 결제창을 닫았거나 인증에 실패한 경우. 원문 메시지는 남기지 않는다.
            if ("0000".equals(authResultCode)) log.warn("나이스 결제 인증 서명 불일치. orderId={}", safeOrderId);
            target.queryParam(flag, "fail");
        }
        // 상대 경로로 돌려보내 웹(kujishop.shop)과 개발 프록시 모두 같은 화면으로 돌아오게 한다.
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, target.build().encode().toUriString())
                .build();
    }
}
