package com.kuji.backend.domain.inquiry.entity;

import com.kuji.backend.global.entity.BaseTimeEntity;
import com.kuji.backend.domain.inquiry.enums.InquiryCategory;
import com.kuji.backend.domain.inquiry.enums.InquiryStatus;
import com.kuji.backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private InquiryStatus status;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "shipping_id")
    private Long shippingId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "inquiry_type", nullable = false)
    private InquiryCategory inquiryType;

    @Builder
    public Inquiry(Member member, String title, String content, InquiryCategory inquiryType, Long shippingId) {
        this.member = member;
        this.title = title;
        this.content = content;
        this.inquiryType = inquiryType;
        this.shippingId = shippingId;
        this.status = InquiryStatus.WAITING; // 초기값: 답변대기
    }

    /**
     * 답변 등록
     */
    public void answer(String answerContent) {
        this.answerContent = answerContent;
        this.status = InquiryStatus.COMPLETED;
        this.answeredAt = LocalDateTime.now();
    }
}
