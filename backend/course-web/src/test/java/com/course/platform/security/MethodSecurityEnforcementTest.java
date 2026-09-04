package com.course.platform.security;

import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.application.service.payment.AlipayService;
import com.course.platform.application.service.payment.PaymentOrderService;
import com.course.platform.infra.persistence.mapper.OperationLogMapper;
import com.course.platform.infra.persistence.mapper.PaymentOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.controller.PaymentController;
import com.course.platform.controller.RbacAdminController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MethodSecurityEnforcementTest.Config.class)
class MethodSecurityEnforcementTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean RbacAdministrationService rbacService() { return mock(RbacAdministrationService.class); }
        @Bean RbacAdminController rbacAdminController(RbacAdministrationService service) {
            return new RbacAdminController(service);
        }
        @Bean AlipayService alipayService() { return mock(AlipayService.class); }
        @Bean PaymentOrderService paymentOrderService() { return mock(PaymentOrderService.class); }
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
        @Bean PaymentOrderMapper paymentOrderMapper() { return mock(PaymentOrderMapper.class); }
        @Bean OperationLogMapper operationLogMapper() { return mock(OperationLogMapper.class); }
        @Bean PaymentController paymentController() { return new PaymentController(); }
    }

    @Autowired RbacAdminController rbacController;
    @Autowired RbacAdministrationService rbacService;
    @Autowired PaymentController paymentController;

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private UsernamePasswordAuthenticationToken authenticate(String... names) {
        var authorities = java.util.Arrays.stream(names).map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(7L, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test
    void ordinaryUserCallingAdminApiIsDenied() {
        authenticate(SecurityAuthorities.ROLE_USER);
        assertThrows(AccessDeniedException.class, () -> rbacController.roles());
    }

    @Test
    void explicitRbacPermissionAllowsAdminApi() {
        authenticate(SecurityAuthorities.RBAC_MANAGE);
        when(rbacService.listEnabledRoles()).thenReturn(List.of("USER"));
        assertEquals(List.of("USER"), rbacController.roles().getData());
    }

    @Test
    void callerWithoutPaymentRefundPermissionIsDeniedBeforeBusinessCode() {
        var authentication = authenticate(SecurityAuthorities.PAYMENT_READ);
        assertThrows(AccessDeniedException.class,
                () -> paymentController.refund("ORDER-1", "reason", authentication));
    }
}
