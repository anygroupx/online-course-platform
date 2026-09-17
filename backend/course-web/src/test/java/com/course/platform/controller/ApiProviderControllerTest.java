package com.course.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.ProviderConnectionTestResult;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApiProviderControllerTest.Config.class)
class ApiProviderControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean ApiProviderService providerService() { return mock(ApiProviderService.class); }
        @Bean PlatformDockingService dockingService() { return mock(PlatformDockingService.class); }
        @Bean OperationLogService operationLogService() { return mock(OperationLogService.class); }
        @Bean ApiProviderController controller(ApiProviderService providers, OperationLogService logs,
                                               PlatformDockingService docking) {
            return new ApiProviderController(providers, logs, docking);
        }
    }

    @Autowired ApiProviderController controller;
    @Autowired ApiProviderService providers;
    @Autowired PlatformDockingService docking;
    @Autowired OperationLogService logs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(providers, docking, logs);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class)))
                .build();
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private UsernamePasswordAuthenticationToken authenticate(String... authorities) {
        var authentication = new UsernamePasswordAuthenticationToken(7L, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test
    void providerTypeFilterIsOptionalForExistingListClients() throws Exception {
        var auth = authenticate("api-provider:update");
        when(providers.queryApiProviders(null, null, 1, 10, null)).thenReturn(new Page<>(1, 10, 0));
        mvc.perform(get("/admin/api-providers").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(providers).queryApiProviders(null, null, 1, 10, null);
        verifyNoInteractions(docking);
    }

    @Test
    void providerTypesAreParsedBeforePaginationAndRowsRemainMasked() throws Exception {
        var auth = authenticate("api-provider:update");
        ApiProvider row = new ApiProvider();
        row.setId(9L);
        row.setName("运动服务");
        row.setProviderType("jiguang");
        row.setStatus(ApiProvider.STATUS_ACTIVE);
        row.setVerifiedAt(LocalDateTime.of(2026, 9, 13, 10, 0));
        row.setApiKey("must-not-be-returned");
        Page<ApiProvider> rows = new Page<>(2, 50, 51);
        rows.setRecords(List.of(row));
        when(providers.queryApiProviders("运动", 1, 2, 50, List.of("jiguang", "jingyu"))).thenReturn(rows);
        String body = mvc.perform(get("/admin/api-providers").principal(auth)
                        .param("keyword", "运动").param("status", "1").param("page", "2").param("pageSize", "50")
                        .param("providerTypes", "jiguang,jingyu"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(51))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.records[0].providerType").value("jiguang"))
                .andExpect(jsonPath("$.data.records[0].verifiedAt").exists())
                .andExpect(jsonPath("$.data.records[0].hasApiKey").value(true))
                .andExpect(jsonPath("$.data.records[0].apiKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("must-not-be-returned"));
        verify(providers).queryApiProviders("运动", 1, 2, 50, List.of("jiguang", "jingyu"));
        verifyNoInteractions(docking);
    }

    @Test
    void filteredProviderListsStillRequireProviderManagementAuthority() throws Exception {
        for (String authority : new String[]{"ROLE_USER", "ROLE_ADMIN", "orders:read"}) {
            var auth = authenticate(authority);
            mvc.perform(get("/admin/api-providers").principal(auth).param("providerTypes", "jiguang,jingyu"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(providers, docking);
    }

    @Test
    void savedProviderLookupIsReadOnlyAndNeverReturnsCredentials() throws Exception {
        var auth = authenticate("api-provider:update");
        ApiProvider row = new ApiProvider();
        row.setId(99L);
        row.setName("运动服务配置");
        row.setProviderType("jingyu");
        row.setStatus(ApiProvider.STATUS_ACTIVE);
        row.setVerifiedAt(LocalDateTime.of(2026, 9, 13, 10, 0));
        row.setApiKey("fixture-secret-key");
        row.setPassword("fixture-secret-password");
        row.setToken("fixture-secret-token");
        row.setCookie("fixture-secret-cookie");
        when(providers.getApiProvider(99L)).thenReturn(row);
        String body = mvc.perform(get("/admin/api-providers/99").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.providerType").value("jingyu"))
                .andExpect(jsonPath("$.data.verifiedAt").exists())
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.cookie").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("fixture-secret"));
        verify(providers).getApiProvider(99L);
        verifyNoMoreInteractions(providers);
        verifyNoInteractions(docking, logs);
    }

    @Test
    void savedProviderLookupStillRequiresExplicitManagementAuthority() throws Exception {
        for (String authority : new String[]{"ROLE_USER", "ROLE_ADMIN", "orders:read"}) {
            mvc.perform(get("/admin/api-providers/99").principal(authenticate(authority)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(providers, docking, logs);
    }

    @Test
    void userAndUnrelatedAdminCannotTestOrActivateProvider() throws Exception {
        for (String authority : new String[]{"ROLE_USER", "ROLE_ADMIN", "orders:read"}) {
            var auth = authenticate(authority);
            mvc.perform(post("/admin/api-providers/9/test-connection").principal(auth)).andExpect(status().isForbidden());
            mvc.perform(patch("/admin/api-providers/9/status").principal(auth)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(providers, docking);
    }

    @Test
    void authorizedReadOnlyTestRecordsAuthenticatedOperatorAndReturnsSafeMetadata() throws Exception {
        var auth = authenticate("api-provider:update");
        when(providers.testConnection(9L, 7L)).thenReturn(new ProviderConnectionTestResult(
                "https://provider.example", "provider.example", 8, LocalDateTime.now(), 7L, 2));
        String body = mvc.perform(post("/admin/api-providers/9/test-connection").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.verifiedBy").value(7))
                .andExpect(jsonPath("$.data.normalizedHost").value("provider.example"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("apiKey"));
        assertFalse(body.contains("password"));
        verify(logs).log(eq(7L), eq("测试API接口连接"), anyString(), isNull(), isNull());
        verifyNoInteractions(docking);
    }

    @Test
    void adminReceivesClassifiedFailureAndCorrelationIdNotUnsafeExceptionData() throws Exception {
        var auth = authenticate("api-provider:update");
        ProviderRequestException failure = new ProviderRequestException(ProviderRequestException.Reason.DNS_FAILURE);
        when(providers.testConnection(9L, 7L)).thenThrow(failure);
        mvc.perform(post("/admin/api-providers/9/test-connection").principal(auth))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.reason").value("DNS_FAILURE"))
                .andExpect(jsonPath("$.message").value("服务域名解析失败"))
                .andExpect(jsonPath("$.errorId").value(failure.getErrorId()));
    }

    @Test
    void createUsesWriteAllowlistRatherThanBindingVerificationMetadata() throws Exception {
        var auth = authenticate("api-provider:update");
        when(providers.createApiProvider(any())).thenReturn(9L);
        mvc.perform(post("/admin/api-providers").principal(auth).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Test","providerType":"Daytime","apiUrl":"https://provider.example",
                 "apiKey":"secret-key","status":1,"verifiedAt":"2026-09-05T01:00:00",
                 "verifiedBy":999,"lastCheckReason":"SUCCESS","configVersion":88,"balance":999}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(9));
        ArgumentCaptor<ApiProvider> input = ArgumentCaptor.forClass(ApiProvider.class);
        verify(providers).createApiProvider(input.capture());
        assertEquals("secret-key", input.getValue().getApiKey());
        assertNull(input.getValue().getVerifiedAt());
        assertNull(input.getValue().getVerifiedBy());
        assertNull(input.getValue().getLastCheckReason());
        assertNull(input.getValue().getConfigVersion());
        assertNull(input.getValue().getBalance());
    }

    @Test
    void statusEndpointRejectsInvalidStatesBeforeService() throws Exception {
        var auth = authenticate("api-provider:update");
        mvc.perform(patch("/admin/api-providers/9/status").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":2}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(providers);
    }
}
