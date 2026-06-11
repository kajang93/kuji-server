package com.kuji.backend.domain.member.service;

import com.kuji.backend.domain.member.repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsLogCleanupScheduler {

    private final SmsLogRepository smsLogRepository;

    // 매일 새벽 3시에 30일이 지난 데이터 폐기
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldSmsLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        try {
            smsLogRepository.deleteByCreatedAtBefore(threshold);
            log.info("[SmsLogCleanup] 30일이 지난 SMS 로그를 삭제했습니다. (기준일시: {})", threshold);
        } catch (Exception e) {
            log.error("[SmsLogCleanup] SMS 로그 삭제 실패: {}", e.getMessage());
        }
    }
}
