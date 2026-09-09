package com.course.platform.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ServiceNotificationWorkerConfig {
    @Bean("nativeNotificationExecutor")
    public ThreadPoolTaskExecutor nativeNotificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("native-notification-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
