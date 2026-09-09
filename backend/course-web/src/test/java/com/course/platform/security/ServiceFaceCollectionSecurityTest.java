package com.course.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.servicecommerce.ServiceAccountSessions;
import com.course.platform.config.*;
import com.course.platform.controller.ServiceAccountSessionController;
import com.course.platform.controller.ServiceFaceCollectionController;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.URI;
import java.util.List;

@SpringJUnitWebConfig(ServiceFaceCollectionSecurityTest.Config.class)
class ServiceFaceCollectionSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import({
        SecurityConfig.class,
        ServiceFaceCollectionController.class,
        ServiceAccountSessionController.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class,
        MustChangePasswordFilter.class,
        JwtAuthenticationEntryPoint.class,
        RateLimitFilter.class
    })
    static class Config {
        @Bean
        ServiceAccountSessions sessions() {
            return mock(ServiceAccountSessions.class);
        }

        @Bean
        UserMapper users() {
            return mock(UserMapper.class);
        }

        @Bean
        RateLimitService limiter() {
            return mock(RateLimitService.class);
        }

        @Bean
        JwtUtil jwt() {
            return mock(JwtUtil.class);
        }

        @Bean
        RefreshSessionService refresh() {
            return mock(RefreshSessionService.class);
        }

        @Bean
        UserAuthorityService authorities() {
            return mock(UserAuthorityService.class);
        }

        @Bean
        SecurityAuditService audit() {
            return mock(SecurityAuditService.class);
        }

        @Bean
        RateLimitProperties limits() {
            return new RateLimitProperties();
        }

        @Bean
        ObjectMapper json() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        CorsProperties cors() {
            var p = new CorsProperties();
            p.setAllowedOrigins(List.of("https://trusted.example"));
            return p;
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired ServiceAccountSessions sessions;
    @Autowired RateLimitService limiter;
    MockMvc mvc;
    final String id = "080dd6f4-1f86-4e72-a902-6fe744f7a4ff", ticket = "b".repeat(64);

    @BeforeEach
    void setup() {
        reset(sessions, limiter);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void onlyExactPostHandoffIsAnonymousAndItReturnsNoCacheNoReferrerRedirect() throws Exception {
        when(sessions.consumeFaceLaunch(id, ticket))
                .thenReturn(URI.create("https://collect.example/face?c=official"));
        mvc.perform(
                        post("/api/service-face-collection/launch")
                                .contextPath("/api")
                                .contentType("application/x-www-form-urlencoded")
                                .content("sessionId=" + id + "&ticket=" + ticket)
                                .header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "https://collect.example/face?c=official"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        verify(sessions).consumeFaceLaunch(id, ticket);
        verify(limiter)
                .check(
                        argThat(
                                r ->
                                        "service-face-launch:ip".equals(r.dimension())
                                                && !r.keyMaterial().contains(ticket)));
        mvc.perform(get("/api/service-face-collection/launch").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"face-launch", "face-check", "send-code", "refresh-rules"})
    void obtainingOrUsingAccountAuthorityStillRequiresLogin(String action) throws Exception {
        mvc.perform(
                        post("/api/service-account-sessions/" + id + "/" + action)
                                .contextPath("/api")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions);
    }

    @Test
    void crossSiteNavigationQueriesAndUntrustedOriginsAreRejectedWithoutEchoingTickets()
            throws Exception {
        mvc.perform(
                        post("/api/service-face-collection/launch")
                                .contextPath("/api")
                                .contentType("application/x-www-form-urlencoded")
                                .content("sessionId=" + id + "&ticket=" + ticket)
                                .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(ticket))));
        mvc.perform(
                        post("/api/service-face-collection/launch?sessionId="
                                        + id
                                        + "&ticket="
                                        + ticket)
                                .contextPath("/api")
                                .contentType("application/x-www-form-urlencoded"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/service-face-collection/launch")
                                .contextPath("/api")
                                .header("Origin", "https://evil.example")
                                .contentType("application/x-www-form-urlencoded")
                                .content("sessionId=" + id + "&ticket=" + ticket))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sessions);
    }

    @Test
    void invalidTicketAndRateLimitFailureNeverExposeAPrivateLocation() throws Exception {
        when(sessions.consumeFaceLaunch(any(), any()))
                .thenThrow(
                        new com.course.platform.common.exception.BusinessException(
                                "internal-secret"));
        mvc.perform(
                        post("/api/service-face-collection/launch")
                                .contextPath("/api")
                                .contentType("application/x-www-form-urlencoded")
                                .content("sessionId=" + id + "&ticket=" + ticket))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "internal-secret"))));
        reset(sessions);
        when(limiter.check(any())).thenReturn(RateLimitDecision.denied(20));
        mvc.perform(
                        post("/api/service-face-collection/launch")
                                .contextPath("/api")
                                .contentType("application/x-www-form-urlencoded")
                                .content("sessionId=" + id + "&ticket=" + ticket))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "20"));
        verifyNoInteractions(sessions);
    }
}
