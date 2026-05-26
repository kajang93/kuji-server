package com.kuji.backend.domain.kuji.entity;

import com.kuji.backend.domain.kuji.enums.DrawStatus;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.shipping.entity.Shipping;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "drawhistory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DrawHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM) // PostgreSQL의 커스텀 Enum(draw_status)과 매핑
    @Column(nullable = false)
    private DrawStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kuji_board_id", nullable = false)
    private KujiBoard kujiBoard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kuji_item_id", nullable = false)
    private KujiItem kujiItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Shipping shipping;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DrawHistory(DrawStatus status, Member member, KujiBoard kujiBoard, KujiItem kujiItem) {
        this.status = (status != null) ? status : DrawStatus.DRAWN;
        this.member = member;
        this.kujiBoard = kujiBoard;
        this.kujiItem = kujiItem;
    }

    public void setShipping(Shipping shipping) {
        this.shipping = shipping;
        this.status = DrawStatus.SHIPPING_REQUESTED; // 배송 요청 상태로 변경
    }

    public void startShipping() {
        this.status = DrawStatus.SHIPPING;
    }

    public void completeDelivery() {
        this.status = DrawStatus.DELIVERED;
    }
}
