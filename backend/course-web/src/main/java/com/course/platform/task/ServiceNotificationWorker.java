package com.course.platform.task;

import com.course.platform.service.impl.ServiceNotificationServiceImpl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ServiceNotificationWorker {
    private final ServiceNotificationServiceImpl service;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    public ServiceNotificationWorker(
            ServiceNotificationServiceImpl service,
            @Qualifier("nativeNotificationExecutor") Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void tick() {
        if (!service.deliveryActive() || !running.compareAndSet(false, true)) return;
        try {
            executor.execute(
                    () -> {
                        try {
                            service.processBatch();
                        } finally {
                            running.set(false);
                        }
                    });
        } catch (RuntimeException e) {
            running.set(false);
        }
    }
}
