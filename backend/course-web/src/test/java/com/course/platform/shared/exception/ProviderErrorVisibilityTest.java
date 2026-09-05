package com.course.platform.shared.exception;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.domain.exception.ProviderRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProviderErrorVisibilityTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(SecurityAuditService.class));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authorize(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority(authority))));
    }

    @ParameterizedTest
    @EnumSource(ProviderRequestException.Reason.class)
    void eachCategoryIsVisibleOnlyOnAuthorizedAdminRoutes(ProviderRequestException.Reason reason) {
        var failure = new ProviderRequestException(reason);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/api-providers/7/test-connection");
        request.setContextPath("/api");
        authorize("api-provider:update");
        var response = handler.handleProviderRequestException(failure, request).getBody();
        assertNotNull(response);
        assertEquals(reason.getAdminMessage(), response.getMessage());
        assertEquals(Map.of("reason", reason.name()), response.getData());
        assertEquals(failure.getErrorId(), response.getErrorId());
    }

    @Test
    void ordinaryUsersAndNonAdminRoutesAlwaysKeepTheGenericMessage() {
        var failure = new ProviderRequestException(ProviderRequestException.Reason.PRIVATE_ADDRESS);
        for (String authority : List.of("ROLE_USER", "api-provider:update")) {
            authorize(authority);
            var result = handler.handleProviderRequestException(failure,
                    new MockHttpServletRequest("POST", "/orders/query-courses")).getBody();
            assertNotNull(result);
            assertEquals(ProviderRequestException.PUBLIC_MESSAGE, result.getMessage());
            assertNull(result.getData());
        }
        authorize("ROLE_USER");
        var result = handler.handleProviderRequestException(failure,
                new MockHttpServletRequest("POST", "/admin/api-providers/1/test-connection")).getBody();
        assertEquals(ProviderRequestException.PUBLIC_MESSAGE, result.getMessage());
        assertNull(result.getData());
    }
}
