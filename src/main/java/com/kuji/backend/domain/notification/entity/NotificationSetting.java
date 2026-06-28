package com.kuji.backend.domain.notification.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    // 전체 푸시 활성화 여부
    @Column(nullable = false)
    private boolean pushEnabled = true;

    // 카카오 알림톡 유형 (당첨, 배송, 문의 답변)
    @Column(nullable = false)
    private boolean kakaoWinning = true;

    @Column(nullable = false)
    private boolean kakaoDelivery = true;

    @Column(nullable = false)
    private boolean kakaoInquiry = true;

    // 사업자 전용 알림톡 유형 (주문, 취소, 고객문의)
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean kakaoBizOrder = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean kakaoBizCancel = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean kakaoBizInquiry = true;

    // 마케팅 알림 유형 (관심상품 오픈, 재입고, 이벤트/마감임박)
    @Column(nullable = false)
    private boolean marketingOpen = true;

    @Column(nullable = false)
    private boolean marketingRestock = true;

    @Column(nullable = false)
    private boolean marketingEvent = true;

    // 야간 푸시 수신 여부 (기본 비활성화)
    @Column(nullable = false)
    private boolean nightPush = false;

    @Builder
    public NotificationSetting(Member member) {
        this.member = member;
    }

    public void updateSettings(boolean pushEnabled, boolean kakaoWinning, boolean kakaoDelivery,
                               boolean kakaoInquiry, boolean kakaoBizOrder, boolean kakaoBizCancel,
                               boolean kakaoBizInquiry, boolean marketingOpen, boolean marketingRestock,
                               boolean marketingEvent, boolean nightPush) {
        this.pushEnabled = pushEnabled;
        this.kakaoWinning = kakaoWinning;
        this.kakaoDelivery = kakaoDelivery;
        this.kakaoInquiry = kakaoInquiry;
        this.kakaoBizOrder = kakaoBizOrder;
        this.kakaoBizCancel = kakaoBizCancel;
        this.kakaoBizInquiry = kakaoBizInquiry;
        this.marketingOpen = marketingOpen;
        this.marketingRestock = marketingRestock;
        this.marketingEvent = marketingEvent;
        this.nightPush = nightPush;
    }
}
