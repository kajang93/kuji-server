// 쓰레드 댓글 감시를 처음 시작한 시각을 보관합니다.
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
@Table(name = "threads_reply_monitor_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadsReplyMonitorState {
    @Id
    @Column(length = 64)
    private String accountId;

    @Column(nullable = false)
    private Instant startedAt;

    public ThreadsReplyMonitorState(String accountId, Instant startedAt) {
        this.accountId = accountId;
        this.startedAt = startedAt;
    }
}
