package com.course.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in read-only probes. A bounded, serial worker cannot delay order/payment jobs. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.provider-health.enabled", havingValue = "true")
public class ProviderHealthCheckTask {
    private final ApiProviderMapper providerMapper;
    private final ApiProviderService providerService;

    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "provider-health");
        thread.setDaemon(true);
        return thread;
    });

    @Scheduled(fixedDelayString = "${app.provider-health.interval-millis:1800000}",
            initialDelayString = "${app.provider-health.initial-delay-millis:60000}")
    public void scheduleChecks() {
        if (!running.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try {
                    checkProviders();
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }

    public void checkProviders() {
        long afterId = 0;
        while (!Thread.currentThread().isInterrupted()) {
            List<ApiProvider> providers = providerMapper.selectList(new QueryWrapper<ApiProvider>()
                    .select("id").eq("status", ApiProvider.STATUS_ACTIVE).gt("id", afterId)
                    .orderByAsc("id").last("LIMIT 100"));
            if (providers.isEmpty()) return;
            for (ApiProvider provider : providers) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    providerService.checkHealth(provider.getId());
                } catch (RuntimeException ex) {
                    // Do not log causes, response bodies or decrypted configuration.
                    log.warn("Provider health check could not finish: providerId={}, reason=INTERNAL_ERROR", provider.getId());
                }
                afterId = provider.getId();
            }
            if (providers.size() < 100) return;
        }
    }
}
