package com.kuji.backend.domain.notification.repository;

import com.kuji.backend.domain.member.entity.Member;
import com.kuji.backend.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // 읽지 않은 알림 우선, 최신순 정렬
    Page<Notification> findAllByMemberOrderByReadAscCreatedAtDesc(Member member, Pageable pageable);
    
    // 전체 읽음 처리
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.member = :member AND n.read = false")
    void markAllAsRead(@Param("member") Member member);
}
