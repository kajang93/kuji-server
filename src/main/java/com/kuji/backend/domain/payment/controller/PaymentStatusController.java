// 인증된 구매자에게 자신의 결제 처리 상태만 제공한다.
package com.kuji.backend.domain.payment.controller;

import com.kuji.backend.domain.payment.service.PaymentRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentStatusController {
    private final PaymentRecoveryService recovery;
    @GetMapping("/{orderId}/status")
    public PaymentStatusResponse status(Authentication authentication, @PathVariable String orderId) {
        return new PaymentStatusResponse(recovery.getStatus((Long) authentication.getPrincipal(), orderId));
    }
    public record PaymentStatusResponse(String status) {}
}
