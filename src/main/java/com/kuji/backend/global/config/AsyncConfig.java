package com.kuji.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @Async 비동기 처리를 위한 스레드 풀 설정
 * - WishlistNotificationService의 대량 알림 발송에 사용됩니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);       // 기본 스레드 수
        executor.setMaxPoolSize(20);       // 최대 스레드 수
        executor.setQueueCapacity(100);    // 대기 큐 용량
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }
}
