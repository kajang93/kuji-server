package com.kuji.backend.domain.member.entity;

import com.kuji.backend.domain.kuji.entity.DrawHistory;
import com.kuji.backend.domain.member.enums.PointType;
import com.kuji.backend.domain.payment.entity.Payment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "pointhistory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draw_history_id")
    private DrawHistory drawHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointType type;

    @Column(length = 255)
    private String description;

    @Column(name = "applied_reward_rate")
    private Integer appliedRewardRate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PointHistory(Member member, DrawHistory drawHistory, Payment payment, Integer amount, PointType type, String description, Integer appliedRewardRate) {
        this.member = member;
        this.drawHistory = drawHistory;
        this.payment = payment;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.appliedRewardRate = appliedRewardRate;
    }
}
