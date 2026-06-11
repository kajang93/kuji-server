package com.kuji.backend.domain.member.repository;

import com.kuji.backend.domain.member.entity.SmsLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {
    
    // 특정 번호로 특정 시간 이후에 발송된 문자 개수를 센다
    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime createdAt);

    // 오래된 로그 삭제를 위한 메서드
    void deleteByCreatedAtBefore(LocalDateTime createdAt);
}
