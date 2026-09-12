package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.persistence.mapper.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.interceptor.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class BenzBatchSyncSafetyTest {
    ApiProvider provider;
    ApiProviderMapper providers;
    CourseOrderMapper orders;
    PlatformDockingStrategy gateway;
    CourseOrderProgressLogService progressLogs;
    PlatformDockingServiceImpl service;

    @BeforeEach
    void setup() {
        providers = mock(ApiProviderMapper.class);
        orders = mock(CourseOrderMapper.class);
        gateway = mock(PlatformDockingStrategy.class);
        progressLogs = mock(CourseOrderProgressLogService.class);
        var credentials = mock(ApiProviderService.class);
        var registry = mock(PlatformDockingStrategyFactory.class);
        provider = new ApiProvider();
        provider.setId(9L);
        provider.setStatus(1);
        provider.setProviderType("27");
        provider.setApiUrl("https://supplier.example");
        provider.setApiKey("private-key");
        provider.setLastSyncTime(1000L);
        when(credentials.loadDecrypted(9L)).thenReturn(provider);
        when(registry.getStrategy("27")).thenReturn(gateway);
        service =
                new PlatformDockingServiceImpl(
                        registry,
                        providers,
                        mock(CoursePlatformMapper.class),
                        orders,
                        mock(PlatformCategoryMapper.class),
                        credentials,
                        progressLogs);
    }

    OrderProgressResult row(String id) {
        return OrderProgressResult.builder()
                .studentAccount("student")
                .studentPassword("private-password")
                .courseName("示例课程")
                .thirdOrderId(id)
                .progress("50%")
                .orderStatus(1)
                .remarks("正常")
                .build();
    }

    void saveReturnsOne() {
        when(orders.updateOrderProgressByFullMatch(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(9L),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(1);
    }

    @Test
    void failedSupplierPageNeverWritesOrdersOrAdvancesWatermark() {
        when(gateway.batchQueryOrderProgress(provider, 400L, 0))
                .thenThrow(
                        new ProviderRequestException(
                                ProviderRequestException.Reason.UPSTREAM_REJECTED));
        assertThrows(
                ProviderRequestException.class, () -> service.batchSyncOrderProgress(9L, null, 0));
        verifyNoInteractions(orders, providers);
    }

    @Test
    void successfulKnownRowsUseBoundRemoteOrderIdAndOnlyPatchTheWatermark() {
        when(gateway.batchQueryOrderProgress(provider, 400L, 0))
                .thenReturn(List.of(row("remote-order-9")));
        saveReturnsOne();
        com.course.platform.domain.entity.CourseOrder previous = new com.course.platform.domain.entity.CourseOrder();
        previous.setId(42L);
        previous.setThirdOrderId("remote-order-9");
        previous.setApiProviderId(9L);
        previous.setProgress("25%");
        previous.setOrderStatus(1);
        previous.setRemarks("开始执行");
        when(orders.selectList(any())).thenReturn(List.of(previous));

        assertEquals(1, service.batchSyncOrderProgress(9L, null, 0).get("totalUpdated"));
        verify(orders)
                .updateOrderProgressByFullMatch(
                        eq("student"),
                        eq("private-password"),
                        eq("示例课程"),
                        eq("remote-order-9"),
                        eq(9L),
                        eq(1),
                        eq("50%"),
                        eq("正常"),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
        verify(progressLogs).recordIfChanged(
                argThat(current -> current.getId().equals(42L)
                        && "50%".equals(current.getProgress())
                        && "正常".equals(current.getRemarks())),
                eq("25%"), eq(1), eq("开始执行"),
                eq(com.course.platform.domain.entity.CourseOrderProgressLog.SOURCE_SCHEDULED_SYNC));
        var capture = ArgumentCaptor.forClass(ApiProvider.class);
        verify(providers).updateById(capture.capture());
        assertEquals(9L, capture.getValue().getId());
        assertNull(capture.getValue().getApiKey());
        assertNull(capture.getValue().getPassword());
        assertNotNull(capture.getValue().getLastSyncTime());
    }

    @Test
    void databaseFailureRollsBackEarlierRowsAndCannotBeReportedAsSuccessfulPartialSync() {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:benz-rollback-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE progress_probe (id INT PRIMARY KEY, progress INT)");
        jdbc.update("INSERT INTO progress_probe VALUES(1,0)");
        when(gateway.batchQueryOrderProgress(provider, 400L, 0))
                .thenReturn(List.of(row("one"), row("two")));
        var calls = new AtomicInteger();
        doAnswer(
                        a -> {
                            if (calls.incrementAndGet() == 2)
                                throw new IllegalStateException("SQL private-password");
                            return jdbc.update("UPDATE progress_probe SET progress=50 WHERE id=1");
                        })
                .when(orders)
                .updateOrderProgressByFullMatch(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        eq(9L),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
        var attr = new RuleBasedTransactionAttribute();
        attr.setRollbackRules(List.of(new RollbackRuleAttribute(Exception.class)));
        var source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("batchSyncOrderProgress", attr);
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), source));
        var wrapped = (PlatformDockingService) proxy.getProxy();
        var ex =
                assertThrows(
                        BusinessException.class, () -> wrapped.batchSyncOrderProgress(9L, null, 0));
        assertFalse(ex.getMessage().contains("private-password"));
        assertNull(ex.getCause());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT progress FROM progress_probe WHERE id=1", Integer.class));
        verifyNoInteractions(providers);
    }

    @Test
    void aRepeatedPageCannotLoopForeverOrAdvancePastUnseenUpdates() {
        when(gateway.batchQueryOrderProgress(eq(provider), eq(400L), anyInt()))
                .thenReturn(Collections.nCopies(500, row("same-id")));
        saveReturnsOne();
        assertThrows(BusinessException.class, () -> service.batchSyncOrderProgress(9L, null, 0));
        verify(gateway, times(2)).batchQueryOrderProgress(eq(provider), eq(400L), anyInt());
        verifyNoInteractions(providers);
    }

    @Test
    void neverEndingDistinctPagesStopAtTheBoundWithoutChangingWatermark() {
        when(gateway.batchQueryOrderProgress(eq(provider), eq(400L), anyInt()))
                .thenAnswer(a -> Collections.nCopies(500, row("page-" + a.getArgument(2))));
        saveReturnsOne();
        assertThrows(BusinessException.class, () -> service.batchSyncOrderProgress(9L, null, 0));
        verify(gateway, times(20)).batchQueryOrderProgress(eq(provider), eq(400L), anyInt());
        verifyNoInteractions(providers);
    }

    @Test
    void cursorIsFrozenAtStartSoUpdatesDuringSlowSupplierScanAreNotSkipped() throws Exception {
        AtomicLong firstCall = new AtomicLong();
        when(gateway.batchQueryOrderProgress(provider, 400L, 0))
                .thenAnswer(
                        a -> {
                            firstCall.set(java.time.Instant.now().getEpochSecond());
                            Thread.sleep(1100);
                            return List.of();
                        });
        service.batchSyncOrderProgress(9L, null, 0);
        var capture = ArgumentCaptor.forClass(ApiProvider.class);
        verify(providers).updateById(capture.capture());
        assertTrue(capture.getValue().getLastSyncTime() <= firstCall.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MAX_VALUE})
    void invalidOffsetsFailBeforeCallingSupplier(int offset) {
        assertThrows(
                BusinessException.class, () -> service.batchSyncOrderProgress(9L, null, offset));
        verifyNoInteractions(gateway, orders, providers);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MAX_VALUE})
    void invalidTimestampsFailBeforeCallingSupplier(long timestamp) {
        assertThrows(
                BusinessException.class, () -> service.batchSyncOrderProgress(9L, timestamp, 0));
        verifyNoInteractions(gateway, orders, providers);
    }

    @Test
    void originalOffsetStepAndOverlapArePreservedRatherThanGuessed() {
        when(gateway.batchQueryOrderProgress(provider, 400L, 0))
                .thenReturn(Collections.nCopies(500, row("page-0")));
        when(gateway.batchQueryOrderProgress(provider, 400L, 10000)).thenReturn(List.of());
        saveReturnsOne();
        assertEquals(500, service.batchSyncOrderProgress(9L, 1000L, 0).get("totalUpdated"));
        verify(gateway).batchQueryOrderProgress(provider, 400L, 10000);
    }
}
