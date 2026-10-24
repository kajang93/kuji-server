// 이미 슬랙으로 전달한 쓰레드 댓글을 조회하고 저장합니다.
package com.kuji.backend.domain.threads.repository;

import com.kuji.backend.domain.threads.entity.ThreadsReplyNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThreadsReplyNotificationRepository extends JpaRepository<ThreadsReplyNotification, String> {
}
