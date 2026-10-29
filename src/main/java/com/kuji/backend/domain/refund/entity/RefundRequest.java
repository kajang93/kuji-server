// 고객의 환불 요청 원본과 실행 전 검토 상태를 보관합니다.
package com.kuji.backend.domain.refund.entity;

import com.kuji.backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_request", uniqueConstraints = @UniqueConstraint(name = "uq_refund_request_active_target", columnNames = "active_target_key"), indexes = {
    @Index(name = "idx_refund_request_member_created", columnList = "member_id,created_at"),
    @Index(name = "idx_refund_request_status_created", columnList = "status,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest {
    public enum TargetType { DRAW_HISTORY, POINT_CHARGE }
    public enum Status {
        REQUESTED("접수됨"), UNDER_REVIEW("검토 중"), REVIEW_APPROVED("환불 실행 대기"), REJECTED("반려됨");
        private final String label;
        Status(String label) { this.label = label; }
        public String label() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private TargetType targetType;
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;
    @Column(nullable = false, length = 300, updatable = false)
    private String title;
    @Column(name = "source_occurred_at", updatable = false)
    private LocalDateTime sourceOccurredAt;
    @Column(name = "target_amount", updatable = false)
    private Long targetAmount;
    @Column(name = "fulfillment_status", nullable = false, length = 30, updatable = false)
    private String fulfillmentStatus;
    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private Status status;
    @Column(name = "active_target_key", length = 80)
    private String activeTargetKey;
    @Column(name = "review_reason", length = 1000)
    private String reviewReason;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "reviewed_by")
    private Long reviewedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version
    private Long version;

    public RefundRequest(Member member, TargetType targetType, Long targetId, String title,
            LocalDateTime sourceOccurredAt, Long targetAmount, String fulfillmentStatus, String reason) {
        this.member = member; this.targetType = targetType; this.targetId = targetId; this.title = title;
        this.sourceOccurredAt = sourceOccurredAt; this.targetAmount = targetAmount;
        this.fulfillmentStatus = fulfillmentStatus; this.reason = reason;
        this.status = Status.REQUESTED; this.activeTargetKey = key(targetType, targetId);
        this.createdAt = LocalDateTime.now();
    }
    public static String key(TargetType type, Long targetId) { return type.name() + ":" + targetId; }
    public void review(Status next, String reviewReason, Long reviewerId) {
        boolean allowed = status == Status.REQUESTED && next == Status.UNDER_REVIEW
                || status == Status.UNDER_REVIEW && (next == Status.REVIEW_APPROVED || next == Status.REJECTED);
        if (!allowed) throw new IllegalArgumentException("현재 상태에서 선택한 검토 단계로 변경할 수 없습니다.");
        this.status = next; this.reviewReason = reviewReason; this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        if (next == Status.REJECTED) this.activeTargetKey = null;
    }
}
