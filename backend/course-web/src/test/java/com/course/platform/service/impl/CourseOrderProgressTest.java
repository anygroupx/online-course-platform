package com.course.platform.service.impl;

import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.controller.CourseOrderController;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseOrderProgressTest {
    private final CourseOrderMapper orders = mock(CourseOrderMapper.class);
    private final CoursePlatformMapper platforms = mock(CoursePlatformMapper.class);
    private final ApiProviderMapper providers = mock(ApiProviderMapper.class);
    private final PlatformDockingService docking = mock(PlatformDockingService.class);
    private final ResourceAuthorizationService authorization = mock(ResourceAuthorizationService.class);
    private final CourseOrderServiceImpl service = new CourseOrderServiceImpl(orders, platforms,
            mock(UserMapper.class), mock(OperationLogService.class), docking, providers,
            mock(ApplicationEventPublisher.class), mock(AccountLedgerServiceImpl.class), authorization);
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
