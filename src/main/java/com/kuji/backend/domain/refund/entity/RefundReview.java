// 환불 검토 단계와 담당자의 사유를 변경 이력으로 남깁니다.
package com.kuji.backend.domain.refund.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_review", indexes = @Index(name = "idx_refund_review_request", columnList = "refund_request_id,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_request_id", nullable = false, updatable = false)
    private RefundRequest request;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", nullable = false, length = 24, updatable = false)
    private RefundRequest.Status fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 24, updatable = false)
    private RefundRequest.Status toStatus;
    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private Long reviewerId;
    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    public RefundReview(RefundRequest request, RefundRequest.Status fromStatus, Long reviewerId, String reason) {
        this.request = request; this.fromStatus = fromStatus; this.toStatus = request.getStatus();
        this.reviewerId = reviewerId; this.reason = reason; this.createdAt = request.getReviewedAt();
    }
}
