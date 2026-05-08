package com.kuji.backend.domain.shipping.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.shipping.enums.ShippingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Shipping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "detail_address", length = 255) // DB 스키마에 맞춰 NULL 허용
    private String detailAddress;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Column(name = "courier_name", nullable = false, length = 20) // DB 스키마가 NOT NULL이므로 기본값 설정
    private String courierName = "-";

    @Column(name = "delivery_message", length = 255)
    private String deliveryMessage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ShippingStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Shipping(Member member, String recipientName, String phone, String zipcode, 
                    String address, String detailAddress, String deliveryMessage) {
        this.member = member;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.deliveryMessage = deliveryMessage;
        this.status = ShippingStatus.PREPARING; // 초기 상태
    }

    /**
     * 송장 번호 및 택배사 등록 (배송 시작 시 호출)
     */
    public void startShipping(String courierName, String trackingNumber) {
        this.courierName = courierName;
        this.trackingNumber = trackingNumber;
        this.status = ShippingStatus.SHIPPING;
    }

    /**
     * 배송 상태 변경
     */
    public void updateStatus(ShippingStatus status) {
        this.status = status;
    }
}
