package com.kuji.backend.domain.notification.entity;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "target_id", length = 50)
    private String targetId; // 이동할 리소스 ID (게시글 ID 등)

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Builder
    public Notification(Member member, String title, String body, NotificationType type, String targetId) {
        this.member = member;
        this.title = title;
        this.body = body;
        this.type = type;
        this.targetId = targetId;
        this.read = false;
    }

    public void markAsRead() {
        this.read = true;
    }
}
