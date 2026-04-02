package com.kuji.backend.domain.member.entity;

import com.kuji.backend.domain.member.enums.BusinessStatus;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessInfo extends BaseTimeEntity {

    // 💡 별도의 @GeneratedValue 없이 Member의 ID를 그대로 사용합니다.
    @Id
    @Column(name = "member_id")
    private Long id;

    // 💡 @MapsId가 이 식별 관계 매핑의 핵심입니다.
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "business_number", nullable = false, length = 20)
    private String businessNumber;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "ceo_name", nullable = false, length = 50)
    private String ceoName;

    @Column(name = "license_image_url", nullable = false, length = 255)
    private String licenseImageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "biz_status")
    private BusinessStatus status;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Builder
    public BusinessInfo(Member member, String businessNumber, String companyName,
            String ceoName, String licenseImageUrl) {
        this.member = member;
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.ceoName = ceoName;
        this.licenseImageUrl = licenseImageUrl;
        this.status = BusinessStatus.PENDING; // 처음 등록 시 무조건 '대기' 상태
    }

    // 관리자가 승인/반려 처리할 때 사용할 비즈니스 메서드
    public void approve() {
        this.status = BusinessStatus.APPROVED;
    }

    public void reject(String reason) {
        this.status = BusinessStatus.REJECTED;
        this.rejectReason = reason;
    }
}