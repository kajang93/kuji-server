package com.kuji.backend.domain.kuji.entity;

import com.kuji.backend.domain.kuji.enums.BoardStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "kujiitem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class KujiItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String grade;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "total_qty", nullable = false)
    private Integer totalQty;

    @Column(name = "remain_qty", nullable = false)
    private Integer remainQty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kuji_board_id", nullable = false)
    private KujiBoard kujiBoard;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public KujiItem(String grade, String name, Integer totalQty, KujiBoard kujiBoard) {
        this.grade = grade;
        this.name = name;
        this.totalQty = totalQty;
        this.remainQty = totalQty; // 초기에는 전체 수량과 동일
        this.kujiBoard = kujiBoard;
    }

    public void update(String grade, String name, Integer totalQty) {
        validateModifiable();
        this.grade = grade;
        this.name = name;
        this.totalQty = totalQty;
        this.remainQty = totalQty; // 수동 수정 시 잔여 수량도 초기화 (정책에 따라 조정 가능)
    }

    public void decreaseRemainQty() {
        if (this.remainQty <= 0) {
            throw new IllegalStateException("해당 상품의 재고가 없습니다.");
        }
        this.remainQty--;
    }

    public void validateModifiable() {
        if (this.kujiBoard.getStatus() == BoardStatus.ACTIVE) {
            throw new IllegalStateException("판매 중인 쿠지 판의 상품은 수정하거나 삭제할 수 없습니다.");
        }
    }
}
