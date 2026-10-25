// 결제 승인 전 내구 기록과 중단된 주문의 보상 취소 상태를 보관한다.
package com.kuji.backend.domain.payment.entity;

import com.kuji.backend.domain.payment.enums.SessionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_recovery_attempt")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PaymentRecoveryAttempt {
    public enum State { OPEN, SUBMITTING, RECOVERING, COMPLETED, CANCELLED, FAILED }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "payment_key", nullable = false, unique = true, length = 200)
    private String paymentKey;
    @Column(nullable = false)
    private Integer amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;
    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;
    @Column(name = "retry_at", nullable = false)
    private LocalDateTime retryAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentRecoveryAttempt(String orderId, Long memberId, String paymentKey, Integer amount, SessionType type) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.sessionType = type;
        this.state = State.OPEN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
        this.retryAt = createdAt.plusMinutes(2);
    }

    public void startSubmission() { state = State.SUBMITTING; updatedAt = LocalDateTime.now(); }
    public void startRecovery() { state = State.RECOVERING; updatedAt = LocalDateTime.now(); }
    public void retryLater() { retryAt = LocalDateTime.now().plusMinutes(1); updatedAt = LocalDateTime.now(); }
    public void complete(String resultJson) { this.resultJson = resultJson; state = State.COMPLETED; updatedAt = LocalDateTime.now(); }
    public void cancel() { state = State.CANCELLED; updatedAt = LocalDateTime.now(); }
    public void fail() { state = State.FAILED; updatedAt = LocalDateTime.now(); }
}
