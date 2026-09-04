package com.course.platform.security;

import com.course.platform.application.service.security.PaymentReconcileService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.controller.SecurityAdminController;
import com.course.platform.domain.entity.PaymentReconcileReport;
import com.course.platform.domain.entity.SecurityAuditLog;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BOLA/BFLA 抽样：管理端安全接口与配置接口的水平/垂直越权防护
 */
@ExtendWith(MockitoExtension.class)
class BolaBflaAuthorizationTest {

    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private PaymentReconcileService paymentReconcileService;

    @InjectMocks
    private SecurityAdminController securityAdminController;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void auth(Long userId, String... authorityNames) {
        var authorities = java.util.Arrays.stream(authorityNames)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(userId, "n/a", authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("BFLA: 普通用户不可查询安全审计日志")
    void auditLogs_userForbidden() {
        auth(2L, SecurityAuthorities.ROLE_USER);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityAdminController.auditLogs(null, null, 1, 20));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(securityAuditService, never()).query(any(), any(), any(), any());
    }

    @Test
    @DisplayName("BFLA: 管理员可查询安全审计日志")
    void auditLogs_adminAllowed() {
        auth(1L, SecurityAuthorities.SECURITY_EVENT_READ);
        IPage<SecurityAuditLog> page = new Page<>(1, 20);
        when(securityAuditService.query(null, null, 1, 20)).thenReturn(page);

        Result<IPage<SecurityAuditLog>> result = securityAdminController.auditLogs(null, null, 1, 20);
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(securityAuditService).query(null, null, 1, 20);
    }

    @Test
    @DisplayName("BFLA: 普通用户不可触发支付对账")
    void reconcile_userForbidden() {
        auth(9L, SecurityAuthorities.ROLE_USER);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> securityAdminController.reconcile(LocalDate.now()));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(paymentReconcileService, never()).reconcile(any());
    }

    @Test
    @DisplayName("BFLA: 管理员可触发支付对账")
    void reconcile_adminAllowed() {
        auth(1L, SecurityAuthorities.PAYMENT_RECONCILE);
        PaymentReconcileReport report = new PaymentReconcileReport();
        report.setStatus("MATCHED");
        when(paymentReconcileService.reconcile(any())).thenReturn(report);

        Result<PaymentReconcileReport> result = securityAdminController.reconcile(LocalDate.of(2026, 7, 11));
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("MATCHED", result.getData().getStatus());
    }

    @Test
    @DisplayName("SecurityUtils: 未登录 getCurrentUserId 应 401")
    void currentUser_unauthorized() {
        SecurityContextHolder.clearContext();
        BusinessException ex = assertThrows(BusinessException.class, SecurityUtils::getCurrentUserId);
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("SecurityUtils: requireAdmin 对 CS 角色拒绝（垂直越权）")
    void requireAdmin_csForbidden() {
        auth(5L, SecurityAuthorities.CUSTOMER_SERVICE_READ);
        BusinessException ex = assertThrows(BusinessException.class, SecurityUtils::requireAdmin);
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("SecurityUtils: CS 可通过 requireCustomerService")
    void requireCustomerService_csAllowed() {
        auth(5L, SecurityAuthorities.CUSTOMER_SERVICE_READ);
        assertDoesNotThrow(SecurityUtils::requireCustomerService);
        assertTrue(SecurityUtils.isCustomerService());
        assertFalse(SecurityUtils.isAdmin());
    }
}
