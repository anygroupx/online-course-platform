package com.course.platform.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.course.platform.application.service.servicecommerce.ServiceOrderStatusRefresh;
import com.course.platform.config.ServiceStatusRefreshConfig;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

class ServiceStatusRefreshWorkerTest {
    @Test
    void disabledRefreshDoesNotQueueWork() {
        var service = mock(ServiceOrderStatusRefresh.class);
        var executor = mock(Executor.class);
        new ServiceStatusRefreshWorker(service, executor).tick();
        verifyNoInteractions(executor);
        verify(service, never()).refreshDueStatuses();
    }

    @Test
    void dedicatedQueueDoesNotBlockSchedulingOrAllowOverlaps() {
        var service = mock(ServiceOrderStatusRefresh.class);
        when(service.statusRefreshActive()).thenReturn(true);
        List<Runnable> tasks = new ArrayList<>();
        var worker = new ServiceStatusRefreshWorker(service, tasks::add);
        worker.tick();
        worker.tick();
        assertEquals(1, tasks.size());
        verify(service, never()).refreshDueStatuses();
        tasks.get(0).run();
        verify(service).refreshDueStatuses();
        worker.tick();
        assertEquals(2, tasks.size());
    }

    @Test
    void asynchronousFailureReleasesGuardWithoutLoggingExceptionOrSensitiveFields() {
        var service = mock(ServiceOrderStatusRefresh.class);
        when(service.statusRefreshActive()).thenReturn(true);
        when(service.refreshDueStatuses()).thenThrow(new IllegalStateException("private-password-response"));
        List<Runnable> tasks = new ArrayList<>();
        var worker = new ServiceStatusRefreshWorker(service, tasks::add);
        Logger logger = (Logger) LoggerFactory.getLogger(ServiceStatusRefreshWorker.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            worker.tick();
            assertDoesNotThrow(tasks.get(0)::run);
            worker.tick();
            assertEquals(2, tasks.size());
            assertEquals(1, appender.list.size());
            var event = appender.list.get(0);
            assertNull(event.getThrowableProxy());
            assertFalse(event.getFormattedMessage().contains("private-password-response"));
            assertEquals("Native service status refresh deferred; a later batch will retry", event.getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void rejectedExecutionDoesNotLeaveWorkerStuck() {
        var service = mock(ServiceOrderStatusRefresh.class);
        when(service.statusRefreshActive()).thenReturn(true);
        Executor executor = mock(Executor.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        var worker = new ServiceStatusRefreshWorker(service, executor);
        assertDoesNotThrow(worker::tick);
        assertDoesNotThrow(worker::tick);
        verify(executor, times(2)).execute(any());
        verify(service, never()).refreshDueStatuses();
    }

    @Test
    void executorAndScheduleAreBoundedAndIndependent() throws Exception {
        var executor = new ServiceStatusRefreshConfig().nativeStatusRefreshExecutor();
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(1, executor.getMaxPoolSize());
        assertEquals(1, executor.getQueueCapacity());
        assertEquals("native-status-refresh-", executor.getThreadNamePrefix());
        executor.destroy();
        var schedule = ServiceStatusRefreshWorker.class.getMethod("tick").getAnnotation(Scheduled.class);
        assertEquals(60_000, schedule.fixedDelay());
        assertEquals(60_000, schedule.initialDelay());
    }
}
