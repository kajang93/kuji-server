package com.kuji.backend.domain.notification.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "device_token",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_device_id", columnNames = {"device_id", "member_id"})
    },
    indexes = {
        @Index(name = "idx_device_token_member", columnList = "member_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 500)
    private String token;

    @Column(nullable = false, length = 20)
    private String platform; // WEB, AOS, IOS

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Builder
    public DeviceToken(Member member, String token, String platform, String deviceId) {
        this.member = member;
        this.token = token;
        this.platform = platform;
        this.deviceId = deviceId;
        this.lastUsedAt = LocalDateTime.now();
    }

    public void updateToken(String token, String platform) {
        this.token = token;
        this.platform = platform;
        this.lastUsedAt = LocalDateTime.now();
    }
}
