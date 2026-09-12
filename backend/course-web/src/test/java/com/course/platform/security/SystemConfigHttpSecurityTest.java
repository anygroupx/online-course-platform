package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.config.CorsProperties;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.config.SecurityConfig;
import com.course.platform.controller.ClientBootstrapController;
import com.course.platform.controller.SystemConfigController;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SystemConfigHttpSecurityTest.Config.class)
class SystemConfigHttpSecurityTest {

    @Configuration
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            ClientBootstrapController.class,
            SystemConfigController.class,
            GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class,
            MustChangePasswordFilter.class,
            JwtAuthenticationEntryPoint.class,
            RateLimitFilter.class
    })
    static class Config {
        @Bean
        SystemConfigService configs() {
            return mock(SystemConfigService.class);
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
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOrigins(List.of("https://trusted.example"));
            return properties;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SystemConfigService configs;

    @Autowired
    private RateLimitService limiter;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(configs, limiter);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor auth(String... authorities) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                7L,
                null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
        );
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    @Test
    void anonymousBootstrapReturnsOnlyTheClientWhitelist() throws Exception {
        when(configs.getConfigValue("site_name")).thenReturn("课程中心");
        when(configs.getConfigValue("site_keywords")).thenReturn("课程,学习");
        when(configs.getConfigValue("site_description")).thenReturn("课程服务");
        when(configs.getConfigValueAsBoolean("auto_refresh_token_enabled", true)).thenReturn(false);

        mvc.perform(get("/api/client/bootstrap").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branding.siteName").value("课程中心"))
                .andExpect(jsonPath("$.data.branding.siteKeywords").value("课程,学习"))
                .andExpect(jsonPath("$.data.branding.siteDescription").value("课程服务"))
                .andExpect(jsonPath("$.data.session.autoRefreshEnabled").value(false))
                .andExpect(content().string(not(containsString("configKey"))))
                .andExpect(content().string(not(containsString("userRegisterFee"))))
                .andExpect(content().string(not(containsString("tokenExpireMinutes"))));

        verify(configs, never()).getAllConfigs();
    }

    @Test
    void fullSystemConfigStillRequiresReadPermission() throws Exception {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("site_name");
        when(configs.getAllConfigs()).thenReturn(List.of(config));

        mvc.perform(get("/api/system/config").contextPath("/api"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/config").contextPath("/api")
                        .with(auth(SecurityAuthorities.ROLE_USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/system/config").contextPath("/api")
                        .with(auth(SecurityAuthorities.SYSTEM_CONFIG_UPDATE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/system/config").contextPath("/api")
                        .with(auth(SecurityAuthorities.SYSTEM_CONFIG_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].configKey").value("site_name"));
    }

    @Test
    void mutationsRequireUpdatePermission() throws Exception {
        String body = "{\"site_name\":\"新名称\"}";

        mvc.perform(put("/api/system/config").contextPath("/api")
                        .with(auth(SecurityAuthorities.SYSTEM_CONFIG_READ))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/system/config/reset/site_name").contextPath("/api")
                        .with(auth(SecurityAuthorities.SYSTEM_CONFIG_READ)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/system/config").contextPath("/api")
                        .with(auth(SecurityAuthorities.SYSTEM_CONFIG_UPDATE))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/system/config/reset/site_name").contextPath("/api")
                        .with(auth(SecurityAuthorities.SYSTEM_CONFIG_UPDATE)))
                .andExpect(status().isOk());

        verify(configs).updateConfigs(java.util.Map.of("site_name", "新名称"));
        verify(configs).resetConfig("site_name");
    }
}
