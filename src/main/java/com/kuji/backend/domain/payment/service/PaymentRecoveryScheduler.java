// 별도 스케줄러에서 중단된 결제의 내구 기록을 주기적으로 복구한다.
package com.kuji.backend.domain.payment.service;

import com.kuji.backend.domain.payment.entity.PaymentRecoveryAttempt;
import com.kuji.backend.domain.payment.repository.PaymentRecoveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryScheduler {
    private final PaymentRecoveryAttemptRepository attempts;
    private final PaymentCompensationService compensation;

    // 기존 예약 작업은 결제 복구의 네트워크 재시도와 별도 스레드에서 실행합니다.
    @Bean(name = "taskScheduler")
    public static ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }

    @Bean(name = "paymentRecoveryTaskScheduler")
    public static ThreadPoolTaskScheduler paymentRecoveryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("payment-recovery-");
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${payment.recovery.interval-ms:60000}", initialDelayString = "60000", scheduler = "paymentRecoveryTaskScheduler")
    public void recoverPendingPayments() {
        try {
            for (String orderId : attempts.findDueOrderIds(List.of(PaymentRecoveryAttempt.State.OPEN, PaymentRecoveryAttempt.State.SUBMITTING,
                    PaymentRecoveryAttempt.State.RECOVERING), LocalDateTime.now(), PageRequest.of(0, 20))) {
                try { compensation.recover(orderId); }
                catch (RuntimeException e) { log.warn("결제 복구 작업 재시도 필요. orderId={}", orderId); }
            }
        } catch (RuntimeException e) {
            log.warn("결제 복구 기록을 조회하지 못했습니다. 데이터베이스 마이그레이션과 연결 상태를 확인해주세요.");
        }
    }
}
