// 쓰레드 댓글 감시 시작 시각과 계정별 전달 잠금을 관리합니다.
package com.kuji.backend.domain.threads.repository;

import com.kuji.backend.domain.threads.entity.ThreadsReplyMonitorState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ThreadsReplyMonitorStateRepository extends JpaRepository<ThreadsReplyMonitorState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ThreadsReplyMonitorState state where state.accountId = :accountId")
    Optional<ThreadsReplyMonitorState> findByAccountIdWithLock(@Param("accountId") String accountId);
}
