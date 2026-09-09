package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.plugin.PluginProduct;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.integration.PluginResearchCatalog;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PluginIntegrationServiceImplTest {
    private PluginReadOnlyConnector connector;
    private ApiProviderMapper mapper;
    private ApiProviderService providers;
    private PluginIntegrationServiceImpl service;

    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "plugin-test"), ApiProvider.class);
        connector = mock(PluginReadOnlyConnector.class);
        when(connector.getProviderType()).thenReturn("jiguang");
        when(connector.supportsSchools()).thenReturn(true);
        var registry = new PluginConnectorRegistry(List.of(connector));
        mapper = mock(ApiProviderMapper.class);
        providers = mock(ApiProviderService.class);
        service = new PluginIntegrationServiceImpl(new PluginResearchCatalog(registry), registry, mapper, providers);
        authenticate("api-provider:update");
        clearInvocations(connector);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority(authority))));
    }
    private ApiProvider configured(int status, boolean verified) {
        ApiProvider provider = new ApiProvider();
        provider.setId(9L); provider.setProviderType("jiguang"); provider.setName("极光配置");
        provider.setStatus(status); provider.setApiKey("private-runtime-key");
        if (verified) provider.setVerifiedAt(LocalDateTime.now());
        when(providers.loadDecrypted(9L)).thenReturn(provider);
        return provider;
    }

    @Test void authorityIsRequiredEvenWhenCalledWithoutAControllerProxy() {
        for (String authority : List.of("ROLE_USER", "ROLE_ADMIN", "platform:update")) {
            authenticate(authority);
            assertEquals(ResultCode.FORBIDDEN.getCode(), assertThrows(BusinessException.class, service::listIntegrations).getCode());
            assertThrows(BusinessException.class, () -> service.listProviders("P04", new PluginPageQuery(1, 20, "")));
            assertThrows(BusinessException.class, () -> service.fetchCatalog("P04", 9L, null));
            assertThrows(BusinessException.class, () -> service.searchSchools("P04", 9L, new PluginPageQuery(1, 20, "")));
        }
        verifyNoInteractions(mapper, providers, connector);
    }

    @Test void allUnknownAndOpaquePluginsFailBeforeLoadingCredentialsOrCallingNetwork() {
        for (String id : List.of("P02", "P05", "P06", "P07", "P08", "P09", "P10", "P11", "P12", "unknown")) {
            assertThrows(BusinessException.class, () -> service.fetchCatalog(id, 9L, null));
        }
        verifyNoInteractions(providers, mapper, connector);
    }

    @Test void inactiveOrNeverVerifiedConfigurationCannotRead() {
        for (int status : new int[]{0, 2, 99}) {
            configured(status, true);
            assertEquals(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE,
                    assertThrows(ProviderRequestException.class, () -> service.fetchCatalog("P04", 9L, null)).getReason());
        }
        configured(1, false);
        assertThrows(ProviderRequestException.class, () -> service.searchSchools("P04", 9L, new PluginPageQuery(1, 20, "")));
        verify(connector, never()).fetchCatalog(any(), any());
        verify(connector, never()).searchSchools(any(), any());
        verifyNoInteractions(mapper);
    }

    @Test void providerTypeAndIdentifierCannotBeForged() {
        configured(1, true).setProviderType("heisha");
        assertThrows(BusinessException.class, () -> service.fetchCatalog("P04", 9L, null));
        assertThrows(BusinessException.class, () -> service.fetchCatalog("P04", -1L, null));
        assertThrows(BusinessException.class, () -> service.fetchCatalog("P04", 999L, null));
        assertThrows(BusinessException.class, () -> service.fetchCatalog("P04", 9L, "refund"));
        verify(connector, never()).fetchCatalog(any(), any());
    }

    @Test void normalReadUsesDecryptedRuntimeConfigurationWithoutWritingAnything() {
        ApiProvider provider = configured(1, true);
        // A transient later health failure does not silently revoke explicit activation.
        provider.setLastCheckReason("TIMEOUT");
        var result = List.of(new PluginProduct("1", "晨跑", new BigDecimal("0.15"), "元/公里"));
        when(connector.fetchCatalog(provider, null)).thenReturn(result);
        assertSame(result, service.fetchCatalog("P04", 9L, null));
        verify(providers).loadDecrypted(9L);
        verifyNoMoreInteractions(providers);
        verifyNoInteractions(mapper);
        assertEquals(1, provider.getStatus());
    }

    @Test void untrustedUnexpectedExceptionsAreSanitizedWithoutCause() {
        ApiProvider provider = configured(1, true);
        when(connector.fetchCatalog(provider, null)).thenThrow(new IllegalStateException("unsafe-key-in-upstream-body"));
        var error = assertThrows(ProviderRequestException.class, () -> service.fetchCatalog("P04", 9L, null));
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, error.getReason());
        assertNull(error.getCause()); assertFalse(error.toString().contains("unsafe-key"));
    }

    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void providerOptionsAreTypeScopedPaginatedAndNeverDecryptOrSelectSecrets() throws Exception {
        ApiProvider row = new ApiProvider(); row.setId(9L); row.setName("配置"); row.setProviderType("jiguang");
        row.setApiKey("unsafe-secret"); row.setApiUrl("https://private-provider.example"); row.setStatus(2);
        Page<ApiProvider> rows = new Page<>(2, 20, 35); rows.setRecords(List.of(row));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(rows);
        var result = service.listProviders("P04", new PluginPageQuery(2, 20, " 测试 "));
        assertEquals(35, result.getTotal()); assertFalse(result.getRecords().get(0).verified());
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("unsafe-secret")); assertFalse(json.contains("private-provider"));
        ArgumentCaptor<LambdaQueryWrapper<ApiProvider>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        var filter = captor.getValue();
        assertTrue(filter.getSqlSegment().contains("provider_type"));
        assertTrue(filter.getParamNameValuePairs().containsValue("jiguang"));
        assertTrue(filter.getParamNameValuePairs().containsValue("%测试%"));
        assertFalse(filter.getSqlSelect().contains("api_key")); assertFalse(filter.getSqlSelect().contains("api_url"));
        verifyNoInteractions(providers);
    }
}
