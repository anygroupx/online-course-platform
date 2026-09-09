package com.course.platform.controller;

import com.course.platform.application.service.integration.PluginIntegrationService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.plugin.PluginProduct;
import com.course.platform.domain.vo.plugin.PluginSchoolPage;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PluginIntegrationControllerTest.Config.class)
class PluginIntegrationControllerTest {
    @Configuration(proxyBeanMethods = false) @EnableMethodSecurity
    static class Config {
        @Bean PluginIntegrationService service() { return mock(PluginIntegrationService.class); }
        @Bean PluginIntegrationController controller(PluginIntegrationService service) { return new PluginIntegrationController(service); }
    }
    @Autowired PluginIntegrationController controller;
    @Autowired PluginIntegrationService service;
    private MockMvc mvc;
    private static final String ROOT = "/admin/plugin-integrations";

    @BeforeEach void setUp() {
        reset(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityAuditService.class))).build();
        authenticate("api-provider:update");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority(authority))));
    }

    @Test void userAndUnrelatedAdminCannotReadAnyIntegrationEndpoint() throws Exception {
        for (String authority : List.of("ROLE_USER", "ROLE_ADMIN", "platform:update")) {
            authenticate(authority);
            for (String path : List.of("", "/P04/providers", "/P04/providers/9/catalog", "/P04/providers/9/schools")) {
                mvc.perform(get(ROOT + path)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test void quotationHasSafeShapeDecimalStringAndNoStoreHeader() throws Exception {
        when(service.fetchCatalog("P04", 9L, null)).thenReturn(List.of(new PluginProduct("1", "晨跑", new BigDecimal("0.15"), "元/公里")));
        mvc.perform(get(ROOT + "/P04/providers/9/catalog"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data[0].unitPrice").value("0.15"))
                .andExpect(jsonPath("$.data[0].id").value("1"));
        verify(service).fetchCatalog("P04", 9L, null);
    }

    @Test void schoolQueryIsValidatedAndNormalizedBeforeServiceOrNetwork() throws Exception {
        when(service.searchSchools(eq("P04"), eq(9L), any())).thenReturn(new PluginSchoolPage(List.of(), 1, 20, false));
        mvc.perform(get(ROOT + "/P04/providers/9/schools").param("keyword", " 大学 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(false));
        verify(service).searchSchools("P04", 9L, new PluginPageQuery(1, 20, "大学"));
        reset(service);
        for (String path : List.of("/P04/providers", "/P04/providers/9/schools")) {
            mvc.perform(get(ROOT + path).param("page", "0")).andExpect(status().isUnprocessableEntity());
            mvc.perform(get(ROOT + path).param("pageSize", "501")).andExpect(status().isUnprocessableEntity());
            mvc.perform(get(ROOT + path).param("keyword", "x".repeat(81))).andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(service);
    }

    @Test void providerFailuresExposeOnlySafeReasonAndErrorId() throws Exception {
        var error = new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
        when(service.fetchCatalog("P04", 9L, null)).thenThrow(error);
        mvc.perform(get(ROOT + "/P04/providers/9/catalog"))
                .andExpect(status().isBadGateway()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.reason").value("TIMEOUT"))
                .andExpect(jsonPath("$.errorId").value(error.getErrorId()));
    }
    @Test void anonymousUsersAreRejectedAndMutationRoutesDoNotExist() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        authenticate("api-provider:update");
        mvc.perform(post(ROOT + "/P04/providers/9/catalog")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

}
