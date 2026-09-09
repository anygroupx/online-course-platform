package com.course.platform.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.course.platform.config.ServiceNotificationWorkerConfig;
import com.course.platform.service.impl.ServiceNotificationServiceImpl;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Executor;

class ServiceNotificationWorkerTest {
    @Test
    void disabledDeliveryDoesNotSubmitWorkToExecutor() {
        var service = mock(ServiceNotificationServiceImpl.class);
        var executor = mock(Executor.class);
        new ServiceNotificationWorker(service, executor).tick();
        verifyNoInteractions(executor);
    }

    @Test
    void dedicatedQueueNeverBlocksSchedulerOrCreatesUnboundedOverlaps() {
        var service = mock(ServiceNotificationServiceImpl.class);
        when(service.deliveryActive()).thenReturn(true);
        List<Runnable> tasks = new ArrayList<>();
        var worker = new ServiceNotificationWorker(service, tasks::add);
        worker.tick();
        worker.tick();
        assertEquals(1, tasks.size());
        verify(service, never()).processBatch();
        tasks.get(0).run();
        verify(service).processBatch();
        worker.tick();
        assertEquals(2, tasks.size());
    }

    @Test
    void failedOrRejectedWorkReleasesSingleFlightGuard() {
        var service = mock(ServiceNotificationServiceImpl.class);
        when(service.deliveryActive()).thenReturn(true);
        List<Runnable> tasks = new ArrayList<>();
        var worker = new ServiceNotificationWorker(service, tasks::add);
        worker.tick();
        doThrow(new RuntimeException("failure")).when(service).processBatch();
        assertThrows(RuntimeException.class, tasks.get(0)::run);
        worker.tick();
        assertEquals(2, tasks.size());
        Executor rejected = mock(Executor.class);
        doThrow(new java.util.concurrent.RejectedExecutionException())
                .when(rejected)
                .execute(any());
        var other = new ServiceNotificationWorker(service, rejected);
        other.tick();
        other.tick();
        verify(rejected, times(2)).execute(any());
    }

    @Test
    void executorIsBoundedAndSeparateFromDefaultScheduling() {
        var executor = new ServiceNotificationWorkerConfig().nativeNotificationExecutor();
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(1, executor.getMaxPoolSize());
        assertEquals(1, executor.getQueueCapacity());
        assertEquals("native-notification-", executor.getThreadNamePrefix());
        executor.destroy();
    }
}
