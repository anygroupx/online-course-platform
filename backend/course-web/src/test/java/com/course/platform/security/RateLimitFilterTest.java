package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniedRequestReturns429RetryAfterAndAuditEvent() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.check(any())).thenReturn(RateLimitDecision.denied(17));
        SecurityAuditService audit = mock(SecurityAuditService.class);
        RateLimitFilter filter = new RateLimitFilter(service, new RateLimitProperties(), audit, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/register");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("17", response.getHeader("Retry-After"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getContentAsString().contains(String.valueOf(ResultCode.RATE_LIMITED.getCode())));
        verify(audit).record(eq("RATE_LIMIT_TRIGGERED"), eq("WARN"), isNull(), isNull(),
                eq("/register"), eq("POST"), anyString(), contains("dimension=register:ip"));
    }

    @Test
    void authenticatedOrderLimitUsesTrustedPrincipalNotClientUserId() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.check(any())).thenReturn(RateLimitDecision.allowed(10));
        RateLimitFilter filter = new RateLimitFilter(service, new RateLimitProperties(),
                mock(SecurityAuditService.class), new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/create");
        request.setContextPath("/api");
        request.addParameter("userId", "999");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<RateLimitRequest> captor = ArgumentCaptor.forClass(RateLimitRequest.class);
        verify(service).check(captor.capture());
        assertEquals("order:user", captor.getValue().dimension());
        assertEquals("42", captor.getValue().keyMaterial());
    }

    @Test
    void redisFailureFailsClosedWith503() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.check(any())).thenThrow(new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE));
        RateLimitFilter filter = new RateLimitFilter(service, new RateLimitProperties(),
                mock(SecurityAuditService.class), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains(String.valueOf(ResultCode.RATE_LIMIT_UNAVAILABLE.getCode())));
    }
}
