package com.course.platform.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.servicecommerce.ServiceAccountSessions;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServiceAccountSessionControllerTest.Config.class)
class ServiceAccountSessionControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean
        ServiceAccountSessions sessions() {
            return mock(ServiceAccountSessions.class);
        }

        @Bean
        ServiceAccountSessionController controller(ServiceAccountSessions sessions) {
            return new ServiceAccountSessionController(sessions);
        }
    }

    @Autowired ServiceAccountSessionController controller;
    @Autowired ServiceAccountSessions sessions;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(sessions);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                7L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(
                                new MappingJackson2HttpMessageConverter(
                                        new ObjectMapper().findAndRegisterModules()))
                        .setControllerAdvice(
                                new GlobalExceptionHandler(mock(SecurityAuditService.class)))
                        .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sessionWritesAndReadsUseSafeBodiesAndNeverCacheCredentials() throws Exception {
        mvc.perform(
                        post("/services/1/account-sessions")
                                .contentType("application/json")
                                .content(
                                        "{\"mode\":\"SMS\",\"account\":\"13800138000\",\"authorizedAccount\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(sessions).start(eq(1L), any());
        mvc.perform(
                        post("/service-account-sessions/session-id/verify")
                                .contentType("application/json")
                                .content("{\"secret\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(sessions).verify(eq("session-id"), any());
        mvc.perform(get("/service-account-sessions/session-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void malformedAndInvalidCredentialsNeverReachServiceOrEchoTheSecret() throws Exception {
        mvc.perform(
                        post("/service-account-sessions/id/verify")
                                .contentType("application/json")
                                .content("{\"secret\":{\"private-value\":123}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
        mvc.perform(
                        post("/services/1/account-sessions")
                                .contentType("application/json")
                                .content("{\"mode\":\"override\",\"account\":\"private\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(sessions);
    }

    @Test
    void anonymousUsersCannotCreateSendVerifyRefreshOrReadSessions() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new AnonymousAuthenticationToken(
                                "test",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        for (String action : List.of("send-code", "refresh-rules", "face-check", "face-launch"))
            mvc.perform(post("/service-account-sessions/id/" + action))
                    .andExpect(status().isForbidden());
        mvc.perform(
                        post("/service-account-sessions/id/face-collection")
                                .contentType("application/json")
                                .content("{\"authorizedFace\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/service-account-sessions/id")).andExpect(status().isForbidden());
        mvc.perform(delete("/service-account-sessions/id")).andExpect(status().isForbidden());
        verifyNoInteractions(sessions);
    }
}
