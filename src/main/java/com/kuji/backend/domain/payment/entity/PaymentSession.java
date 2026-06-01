package com.kuji.backend.domain.payment.entity;

import com.kuji.backend.domain.kuji.entity.KujiBoard;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.payment.enums.SessionStatus;
import com.kuji.backend.domain.payment.enums.SessionType;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "paymentsession")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private KujiBoard board;

    @Column
    private Integer count;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;

    @Column(length = 2000)
    private String metadata;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public PaymentSession(Member member, KujiBoard board, Integer count, Integer amount, String orderId, SessionStatus status, SessionType sessionType, String metadata, LocalDateTime expiresAt) {
        this.member = member;
        this.board = board;
        this.count = count;
        this.amount = amount;
        this.orderId = orderId;
        this.status = status;
        this.sessionType = sessionType;
        this.metadata = metadata;
        this.expiresAt = expiresAt;
    }

    public void updateStatus(SessionStatus status) {
        this.status = status;
    }
}
