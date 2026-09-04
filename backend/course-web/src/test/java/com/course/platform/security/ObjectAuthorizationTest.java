package com.course.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.CustomerServiceMessageMapper;
import com.course.platform.infra.persistence.mapper.CustomerServiceSessionMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.service.impl.AccountLedgerServiceImpl;
import com.course.platform.service.impl.CourseOrderServiceImpl;
import com.course.platform.service.impl.CustomerServiceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ObjectAuthorizationTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(long userId, String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, granted));
    }

    private CourseOrderServiceImpl orderService(CourseOrderMapper orderMapper) {
        return new CourseOrderServiceImpl(orderMapper, mock(CoursePlatformMapper.class), mock(UserMapper.class),
                mock(OperationLogService.class), mock(PlatformDockingService.class), mock(ApiProviderMapper.class),
                mock(ApplicationEventPublisher.class), mock(AccountLedgerServiceImpl.class),
                new ResourceAuthorizationService());
    }

    @Test
    void ordinaryUserCannotReadAnotherUsersOrder() {
        authenticate(10L, SecurityAuthorities.ROLE_USER);
        CourseOrder order = new CourseOrder();
        order.setId(99L);
        order.setUserId(11L);
        CourseOrderMapper mapper = mock(CourseOrderMapper.class);
        when(mapper.selectById(99L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService(mapper).getOrderById(99L, 10L));
        assertEquals(ResultCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void ownerCanReadOwnOrder() {
        authenticate(10L, SecurityAuthorities.ROLE_USER);
        CourseOrder order = new CourseOrder();
        order.setId(99L);
        order.setUserId(10L);
        CourseOrderMapper mapper = mock(CourseOrderMapper.class);
        when(mapper.selectById(99L)).thenReturn(order);

        assertSame(order, orderService(mapper).getOrderById(99L, 10L));
    }

    @Test
    void readPermissionDoesNotGrantMutationOfAnotherUsersOrder() {
        authenticate(30L, SecurityAuthorities.ORDER_READ);
        CourseOrder order = new CourseOrder();
        order.setId(99L);
        order.setUserId(11L);
        CourseOrderMapper mapper = mock(CourseOrderMapper.class);
        when(mapper.selectById(99L)).thenReturn(order);
        CourseOrderServiceImpl service = orderService(mapper);

        assertSame(order, service.getOrderById(99L, 30L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancelOrder(99L, 30L));
        assertEquals(ResultCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any(CourseOrder.class));
    }

    @Test
    void unassignedCustomerServiceCannotReadSessionMessages() {
        authenticate(20L, SecurityAuthorities.ROLE_CUSTOMER_SERVICE,
                SecurityAuthorities.CUSTOMER_SERVICE_READ);
        CustomerServiceSession session = new CustomerServiceSession();
        session.setSessionId("session-a");
        session.setUserId(10L);
        session.setCustomerServiceId(21L);
        CustomerServiceSessionMapper sessionMapper = mock(CustomerServiceSessionMapper.class);
        CustomerServiceMessageMapper messageMapper = mock(CustomerServiceMessageMapper.class);
        when(sessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);
        CustomerServiceServiceImpl service = new CustomerServiceServiceImpl(sessionMapper, messageMapper,
                mock(UserMapper.class), new ResourceAuthorizationService());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getSessionMessages("session-a", 20L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(messageMapper, never()).selectMessagesBySessionId(any());
    }

    @Test
    void assignedCustomerServiceCanReadSessionMessages() {
        authenticate(20L, SecurityAuthorities.ROLE_CUSTOMER_SERVICE,
                SecurityAuthorities.CUSTOMER_SERVICE_READ);
        CustomerServiceSession session = new CustomerServiceSession();
        session.setSessionId("session-a");
        session.setUserId(10L);
        session.setCustomerServiceId(20L);
        CustomerServiceSessionMapper sessionMapper = mock(CustomerServiceSessionMapper.class);
        CustomerServiceMessageMapper messageMapper = mock(CustomerServiceMessageMapper.class);
        when(sessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);
        when(messageMapper.selectMessagesBySessionId("session-a")).thenReturn(List.of());
        CustomerServiceServiceImpl service = new CustomerServiceServiceImpl(sessionMapper, messageMapper,
                mock(UserMapper.class), new ResourceAuthorizationService());

        assertEquals(List.of(), service.getSessionMessages("session-a", 20L));
    }
}
