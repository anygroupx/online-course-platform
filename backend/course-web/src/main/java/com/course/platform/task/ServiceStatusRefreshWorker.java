package com.course.platform.task;

import com.course.platform.application.service.servicecommerce.ServiceOrderStatusRefresh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ServiceStatusRefreshWorker {
    private static final Logger log = LoggerFactory.getLogger(ServiceStatusRefreshWorker.class);
    private final ServiceOrderStatusRefresh service;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    public ServiceStatusRefreshWorker(ServiceOrderStatusRefresh service,
            @Qualifier("nativeStatusRefreshExecutor") Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void tick() {
        if (!service.statusRefreshActive() || !running.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    service.refreshDueStatuses();
                } catch (RuntimeException ex) {
                    // Fixed message only: database/HTTP exceptions can contain sensitive fields.
                    log.warn("Native service status refresh deferred; a later batch will retry");
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException ex) {
            running.set(false);
        }
    }
}
