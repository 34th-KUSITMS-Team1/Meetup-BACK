package com.chamabosal.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 기본 @Async 실행기는 Boot 자동 구성(applicationTaskExecutor)을 그대로 쓴다.
 * 실행기 빈을 추가해도 자동 구성이 빠지지 않도록 application.yml에서 spring.task.execution.mode=force로 둔다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String DISCORD_ALERT_EXECUTOR = "discordAlertExecutor";

    private static final int DISCORD_ALERT_POOL_SIZE = 2;
    private static final int DISCORD_ALERT_QUEUE_CAPACITY = 100;
    private static final int AWAIT_TERMINATION_SECONDS = 10;

    /** 알림은 유실돼도 되므로, 대기열이 차면 요청 스레드를 막지 않도록 작업을 버린다. */
    @Bean(name = DISCORD_ALERT_EXECUTOR)
    public ThreadPoolTaskExecutor discordAlertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(DISCORD_ALERT_POOL_SIZE);
        executor.setMaxPoolSize(DISCORD_ALERT_POOL_SIZE);
        executor.setQueueCapacity(DISCORD_ALERT_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("discord-alert-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("애플리케이션 종료 중이라 Discord 알림을 버립니다.");
            } else {
                log.warn("Discord 알림 대기열이 가득 차 알림을 버립니다.");
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        return executor;
    }
}
