// 슬랙으로 전달한 쓰레드 댓글 식별자를 보관합니다.
package com.kuji.backend.domain.threads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "threads_reply_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadsReplyNotification {
    @Id
    @Column(length = 64)
    private String replyId;

    @Column(nullable = false)
    private Instant notifiedAt;

    public ThreadsReplyNotification(String replyId, Instant notifiedAt) {
        this.replyId = replyId;
        this.notifiedAt = notifiedAt;
    }
}
