package com.course.platform.service.impl;

import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.controller.CourseOrderController;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CourseOrderProgressLog;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.security.ResourceAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseOrderProgressTest {
    private final CourseOrderMapper orders = mock(CourseOrderMapper.class);
    private final CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
    private final ApiProviderMapper providers = mock(ApiProviderMapper.class);
    private final PlatformDockingService docking = mock(PlatformDockingService.class);
    private final ResourceAuthorizationService authorization = mock(ResourceAuthorizationService.class);
    private final CourseOrderProgressLogService progressLogs = mock(CourseOrderProgressLogService.class);
    private final CourseOrderServiceImpl service = new CourseOrderServiceImpl(orders, platforms,
            mock(UserMapper.class), mock(OperationLogService.class), docking, providers,
            mock(ApplicationEventPublisher.class), mock(AccountLedgerServiceImpl.class), authorization,
            progressLogs);
    private CourseOrder order;
    private CoursePlatform platform;
    private ApiProvider provider;

    @BeforeEach
    void setUp() {
        order = new CourseOrder();
        order.setId(42L);
        order.setOrderNo("ORD-historical");
        order.setPlatformId(17L);
        order.setApiProviderId(6L);
        order.setUserId(7L);
        order.setProgress("25%");
        when(orders.selectById(42L)).thenReturn(order);
        when(orders.selectOne(any())).thenReturn(order);
        platform = new CoursePlatform();
        platform.setId(17L);
        platform.setDockApiId(6L);
        // Real legacy rows allow a null is_self_operated flag.
        when(platforms.selectById(17L)).thenReturn(platform);
        provider = new ApiProvider();
        provider.setId(6L);
        provider.setStatus(ApiProvider.STATUS_ACTIVE);
        when(providers.selectById(6L)).thenReturn(provider);
    }

    @Test
    void deletedPlatformReturnsActionableBusinessFailureFromOrderNumberEntryPoint() {
        when(platforms.selectById(17L)).thenReturn(null);
        var failure = assertThrows(BusinessException.class,
                () -> service.updateOrderProgressByOrderNo("ORD-historical", 7L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), failure.getCode());
        assertTrue(failure.getMessage().contains("课程平台已删除"));
        verifyNoInteractions(docking, providers);
        verify(orders, never()).updateById(any(CourseOrder.class));
        assertEquals(17L, order.getPlatformId());
        assertEquals("25%", order.getProgress());
    }

    @Test
    void refreshHttpEndpointReturns404RatherThanInternalErrorForDeletedPlatform() throws Exception {
        when(platforms.selectById(17L)).thenReturn(null);
        var mvc = MockMvcBuilders.standaloneSetup(new CourseOrderController(service, mock(UserMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class))).build();
        mvc.perform(post("/orders/ORD-historical/refresh")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("订单关联的课程平台已删除，无法刷新进度，请联系管理员处理"))
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(jsonPath("$.success").value(false));
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void refreshHttpEndpointReturns502AndOriginalErrorIdRatherThanFalseSuccess() throws Exception {
        var timeout = new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
        when(docking.queryOrderProgress(order, platform, provider)).thenThrow(timeout);
        var mvc = MockMvcBuilders.standaloneSetup(new CourseOrderController(service, mock(UserMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class))).build();
        mvc.perform(post("/orders/ORD-historical/refresh")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorId").value(timeout.getErrorId()))
                .andExpect(jsonPath("$.message").value(ProviderRequestException.PUBLIC_MESSAGE))
                .andExpect(jsonPath("$.success").value(false));
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void nullableSelfOperatedFlagStillAllowsSuccessfulThirdPartyRefresh() {
        LocalDateTime end = LocalDateTime.of(2026, 9, 10, 12, 0);
        when(docking.queryOrderProgress(order, platform, provider)).thenReturn(OrderProgressResult.builder()
                .progress("75%").orderStatus(1).remarks("进行中").courseEndTime(end).build());
        service.updateOrderProgress(42L, 7L);
        assertEquals("75%", order.getProgress());
        assertEquals(1, order.getOrderStatus());
        assertEquals("进行中", order.getRemarks());
        assertEquals(end, order.getCourseEndTime());
        verify(orders).updateById(order);
        verify(progressLogs).recordIfChanged(order, "25%", null, null,
                com.course.platform.domain.entity.CourseOrderProgressLog.SOURCE_MANUAL_REFRESH);
    }

    @Test
    void unchangedProgressResponseDoesNotAppendHistory() {
        order.setOrderStatus(1);
        order.setRemarks("进行中");
        when(docking.queryOrderProgress(order, platform, provider)).thenReturn(OrderProgressResult.builder()
                .progress("25%").orderStatus(1).remarks("进行中").build());

        service.updateOrderProgress(42L, 7L);

        verify(progressLogs).recordIfChanged(order, "25%", 1, "进行中",
                com.course.platform.domain.entity.CourseOrderProgressLog.SOURCE_MANUAL_REFRESH);
    }

    @Test
    void progressLogEndpointReturnsSanitizedLocalTimeline() throws Exception {
        CourseOrderProgressLog progressLog = new CourseOrderProgressLog();
        progressLog.setId(8L);
        progressLog.setOrderId(42L);
        progressLog.setThirdOrderId("remote-secret");
        progressLog.setApiProviderId(6L);
        progressLog.setProgress("75%");
        progressLog.setOrderStatus(1);
        progressLog.setRemarks("进行中");
        progressLog.setSource(CourseOrderProgressLog.SOURCE_SCHEDULED_SYNC);
        progressLog.setCreateTime(LocalDateTime.of(2026, 9, 10, 12, 30));
        when(progressLogs.listByOrderId(42L)).thenReturn(List.of(progressLog));

        var mvc = MockMvcBuilders.standaloneSetup(new CourseOrderController(service, mock(UserMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class))).build();

        mvc.perform(get("/orders/ORD-historical/progress-logs")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(8))
                .andExpect(jsonPath("$.data[0].progress").value("75%"))
                .andExpect(jsonPath("$.data[0].orderStatus").value(1))
                .andExpect(jsonPath("$.data[0].remarks").value("进行中"))
                .andExpect(jsonPath("$.data[0].source").value("scheduled_sync"))
                .andExpect(jsonPath("$.data[0].createTime").exists())
                .andExpect(jsonPath("$.data[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data[0].thirdOrderId").doesNotExist())
                .andExpect(jsonPath("$.data[0].apiProviderId").doesNotExist());

        verify(progressLogs).listByOrderId(42L);
    }

    @Test
    void selfOperatedOrderReturnsNoProgressLogsWithoutReadingLogTable() {
        order.setIsSelfOperated(1);

        assertEquals(List.of(), service.getProgressLogsByOrderNo("ORD-historical", 7L));

        verify(progressLogs, never()).listByOrderId(anyLong());
    }

    @Test
    void missingDockConfigurationIsNotReportedAsSuccessfulRefresh() {
        platform.setDockApiId(null);
        var failure = assertThrows(BusinessException.class, () -> service.updateOrderProgress(42L, 7L));
        assertTrue(failure.getMessage().contains("未配置对接接口"));
        verifyNoInteractions(docking, providers);
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 2, 99})
    void inactiveOrNullProviderStatusIsClassifiedWithoutNullPointer(Integer status) {
        provider.setStatus(status);
        var failure = assertThrows(ProviderRequestException.class, () -> service.updateOrderProgress(42L, 7L));
        assertEquals(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE, failure.getReason());
        verifyNoInteractions(docking);
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void missingProviderIsClassifiedWithoutNullPointer() {
        when(providers.selectById(6L)).thenReturn(null);
        var failure = assertThrows(ProviderRequestException.class, () -> service.updateOrderProgress(42L, 7L));
        assertEquals(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE, failure.getReason());
        verifyNoInteractions(docking);
    }

    @Test
    void timeoutRetainsCorrelationAndDoesNotOverwriteStoredProgress() {
        var timeout = new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
        when(docking.queryOrderProgress(order, platform, provider)).thenThrow(timeout);
        assertSame(timeout, assertThrows(ProviderRequestException.class,
                () -> service.updateOrderProgress(42L, 7L)));
        verify(orders, never()).updateById(any(CourseOrder.class));
        assertEquals("25%", order.getProgress());
    }

    @Test
    void invalidProgressResponseDoesNotReportSuccess() {
        var failure = assertThrows(ProviderRequestException.class, () -> service.updateOrderProgress(42L, 7L));
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, failure.getReason());
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void changedProviderNeverReceivesHistoricalOrderCredentials() {
        platform.setDockApiId(9L);
        var failure = assertThrows(BusinessException.class, () -> service.updateOrderProgress(42L, 7L));
        assertEquals(ResultCode.CONFLICT.getCode(), failure.getCode());
        verifyNoInteractions(docking, providers);
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void authorizationIsCheckedBeforeReadingPlatformOrCallingProvider() {
        doThrow(new BusinessException(ResultCode.ORDER_NOT_FOUND)).when(authorization).requireCanUpdateOrder(order);
        assertThrows(BusinessException.class, () -> service.updateOrderProgress(42L, 7L));
        verifyNoInteractions(platforms, providers, docking);
        verify(orders, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void selfOperatedOrderDoesNotCallProvider() {
        platform.setIsSelfOperated(1);
        service.updateOrderProgress(42L, 7L);
        verifyNoInteractions(providers, docking);
    }
}
