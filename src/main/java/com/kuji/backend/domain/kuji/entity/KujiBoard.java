package com.kuji.backend.domain.kuji.entity;

import com.kuji.backend.domain.kuji.enums.BoardStatus;
import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "kujiboard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KujiBoard extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "price_per_draw", nullable = false)
    private Long pricePerDraw;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private BoardStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "reward_rate", nullable = false)
    @ColumnDefault("0")
    private Integer rewardRate = 0;

    @Builder
    public KujiBoard(String title, Long pricePerDraw, BoardStatus status, Member member, Integer rewardRate) {
        this.title = title;
        this.pricePerDraw = pricePerDraw;
        this.status = status;
        this.member = member;
        this.rewardRate = (rewardRate != null) ? rewardRate : 0;
    }

    public void updateStatus(BoardStatus status) {
        this.status = status;
    }
}
