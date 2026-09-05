package com.course.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProviderHealthCheckTaskTest {
    @Test
    @SuppressWarnings("unchecked")
    void checksOnlyEnabledProvidersAndIsolatesOneFailure() {
        ApiProviderMapper mapper = mock(ApiProviderMapper.class);
        ApiProviderService service = mock(ApiProviderService.class);
        ApiProvider first = new ApiProvider(); first.setId(1L);
        ApiProvider second = new ApiProvider(); second.setId(2L);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("must-not-log-secrets")).when(service).checkHealth(1L);
        ProviderHealthCheckTask task = new ProviderHealthCheckTask(mapper, service);
        try {
            task.checkProviders();
            verify(service).checkHealth(1L);
            verify(service).checkHealth(2L);
            verifyNoMoreInteractions(service);
            ArgumentCaptor<QueryWrapper<ApiProvider>> query = ArgumentCaptor.forClass(QueryWrapper.class);
            verify(mapper).selectList(query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("status"));
            assertTrue(query.getValue().getParamNameValuePairs().containsValue(ApiProvider.STATUS_ACTIVE));
            assertEquals("id", query.getValue().getSqlSelect());
        } finally {
            task.shutdown();
        }
    }

    @Test
    void healthChecksAreOptInAndScheduledOnAFixedDelay() throws Exception {
        ConditionalOnProperty optIn = ProviderHealthCheckTask.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(optIn);
        assertArrayEquals(new String[]{"app.provider-health.enabled"}, optIn.name());
        assertFalse(optIn.matchIfMissing());
        Scheduled schedule = ProviderHealthCheckTask.class.getMethod("scheduleChecks").getAnnotation(Scheduled.class);
        assertTrue(schedule.fixedDelayString().contains("app.provider-health.interval-millis"));
        assertTrue(schedule.initialDelayString().contains("60000"));
    }
}
