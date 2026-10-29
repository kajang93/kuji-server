// 실제 구매대금과 거래 당시 판매 수수료를 변경 불가 원장으로 보관합니다.
package com.kuji.backend.domain.settlement.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.payment.entity.Payment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Immutable
@Table(name = "seller_sale", uniqueConstraints = {
    @UniqueConstraint(name = "uq_seller_sale_source", columnNames = "source_key"),
    @UniqueConstraint(name = "uq_seller_sale_payment", columnNames = "payment_id")
}, indexes = @Index(name = "idx_seller_sale_seller_completed", columnList = "seller_id,completed_at"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerSale {
    public enum PaymentEnvironment { TEST, LIVE, UNKNOWN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "buyer_id", nullable = false, updatable = false)
    private Member buyer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private Member seller;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "board_id", nullable = false, updatable = false)
    private KujiBoard board;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", updatable = false)
    private Payment payment;
    @Column(name = "source_key", nullable = false, length = 180, updatable = false)
    private String sourceKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_environment", nullable = false, length = 10, updatable = false)
    private PaymentEnvironment paymentEnvironment;
    @Column(name = "purchased_count", nullable = false, updatable = false)
    private Integer purchasedCount;
    @Column(name = "gross_amount", nullable = false, updatable = false)
    private Long grossAmount;
    @Column(name = "pg_amount", nullable = false, updatable = false)
    private Long pgAmount;
    @Column(name = "point_amount", nullable = false, updatable = false)
    private Long pointAmount;
    @Column(name = "applied_fee_rate", nullable = false, updatable = false)
    private Integer appliedFeeRate;
    @Column(name = "fee_amount", nullable = false, updatable = false)
    private Long feeAmount;
    @Column(name = "net_amount", nullable = false, updatable = false)
    private Long netAmount;
    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;
    @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST)
    private List<SellerSaleDraw> draws = new ArrayList<>();

    public SellerSale(Member buyer, KujiBoard board, Payment payment, String sourceKey, PaymentEnvironment paymentEnvironment, int purchasedCount,
            long grossAmount, long pgAmount, long pointAmount, int appliedFeeRate, LocalDateTime completedAt,
            List<DrawHistory> histories) {
        this.buyer = buyer; this.seller = board.getMember(); this.board = board; this.payment = payment;
        this.sourceKey = sourceKey; this.paymentEnvironment = paymentEnvironment;
        this.purchasedCount = purchasedCount; this.grossAmount = grossAmount;
        this.pgAmount = pgAmount; this.pointAmount = pointAmount; this.appliedFeeRate = appliedFeeRate;
        this.feeAmount = Math.multiplyExact(grossAmount, appliedFeeRate) / 100;
        this.netAmount = grossAmount - this.feeAmount; this.completedAt = completedAt;
        for (DrawHistory history : histories) draws.add(new SellerSaleDraw(this, history));
    }
}
