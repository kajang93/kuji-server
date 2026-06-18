package com.kuji.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @Column(name = "token_value", nullable = false)
    private String tokenValue;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public RefreshToken(String tokenValue, Long memberId, LocalDateTime expiresAt) {
        this.tokenValue = tokenValue;
        this.memberId = memberId;
        this.expiresAt = expiresAt;
    }

    public void updateToken(String tokenValue, LocalDateTime expiresAt) {
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
    }
}
