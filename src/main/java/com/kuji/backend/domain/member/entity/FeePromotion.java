package com.kuji.backend.domain.member.entity;

import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "fee_promotion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeePromotion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "start_at", nullable = false)
    private ZonedDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private ZonedDateTime endAt;

    @Column(name = "max_limit", nullable = false)
    private Integer maxLimit;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount = 0;

    @Column(name = "free_months", nullable = false)
    private Integer freeMonths;

    @Builder
    public FeePromotion(String title, ZonedDateTime startAt, ZonedDateTime endAt, Integer maxLimit, Integer freeMonths) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxLimit = maxLimit;
        this.freeMonths = freeMonths;
    }

    public void incrementCount() {
        if (this.currentCount < this.maxLimit) {
            this.currentCount++;
        } else {
            throw new IllegalStateException("프로모션 선착순 인원이 마감되었습니다.");
        }
    }

    public boolean isActive() {
        ZonedDateTime now = ZonedDateTime.now();
        return now.isAfter(startAt) && now.isBefore(endAt) && currentCount < maxLimit;
    }
}
