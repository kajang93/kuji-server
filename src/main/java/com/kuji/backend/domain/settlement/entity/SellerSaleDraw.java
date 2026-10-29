// 일반 당첨과 무료 라스트상을 원래 구매 거래에 빠짐없이 연결합니다.
package com.kuji.backend.domain.settlement.entity;

import com.kuji.backend.domain.kuji.entity.DrawHistory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity @Immutable
@Table(name = "seller_sale_draw", uniqueConstraints = @UniqueConstraint(name = "uq_seller_sale_draw_history", columnNames = "draw_history_id"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerSaleDraw {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private SellerSale sale;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "draw_history_id", nullable = false, updatable = false)
    private DrawHistory drawHistory;
    public SellerSaleDraw(SellerSale sale, DrawHistory drawHistory) { this.sale = sale; this.drawHistory = drawHistory; }
}
