package com.kuji.backend.domain.payment.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String pguid;

    @Column(name = "merchant_uid", nullable = false, length = 100)
    private String merchantUid;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    @Builder
    public Payment(Member member, String pguid, String merchantUid, BigDecimal amount, String paymentMethod, PaymentStatus status, OffsetDateTime paidAt) {
        this.member = member;
        this.pguid = pguid;
        this.merchantUid = merchantUid;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.paidAt = paidAt;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }
}
