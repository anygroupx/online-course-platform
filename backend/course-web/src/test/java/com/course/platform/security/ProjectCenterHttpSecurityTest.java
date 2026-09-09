package com.course.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.course.platform.application.service.integration.PluginIntegrationService;
import com.course.platform.application.service.projectclient.ProjectClientService;
import com.course.platform.application.service.projectclient.ProjectClientTicketService;
import com.course.platform.application.service.projectclient.ProjectApiKeyService;
import com.course.platform.domain.projectclient.ProjectClientTypes;
import com.course.platform.application.service.projectcenter.ProjectCenterService;
import com.course.platform.application.service.projectcenter.ProjectTicketService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.servicecommerce.ServiceCommerceService;
import com.course.platform.application.service.servicenotification.ServiceNotificationService;
import com.course.platform.config.*;
import com.course.platform.controller.*;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.*;

/**
 * Exercise real SecurityConfig and MVC together, not a controller-only mock that misses admin
 * denyAll.
 */
@SpringJUnitWebConfig(ProjectCenterHttpSecurityTest.Config.class)
class ProjectCenterHttpSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import({
        SecurityConfig.class,
        ProjectCenterController.class,
        ProjectClientController.class,
        ProjectClientTicketController.class,
        ExternalProjectClientController.class,
        CatalogRefreshController.class,
        ProjectTicketController.class,
        ServiceCommerceController.class,
        ServiceNotificationController.class,
        PluginIntegrationController.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationFilter.class,
        MustChangePasswordFilter.class,
        JwtAuthenticationEntryPoint.class,
        RateLimitFilter.class
    })
    static class Config {
        @Bean ProjectClientTicketService clientTickets(){return mock(ProjectClientTicketService.class);}
        @Bean ProjectClientService clients(){return mock(ProjectClientService.class);}
        @Bean ProjectApiKeyService projectKeys(){return mock(ProjectApiKeyService.class);}

        @Bean com.course.platform.application.service.catalogrefresh.CatalogRefreshService catalogue(){return mock(com.course.platform.application.service.catalogrefresh.CatalogRefreshService.class);}
        @Bean
        ProjectCenterService projects() {
            return mock(ProjectCenterService.class);
        }

        @Bean
        ProjectTicketService tickets() {
            return mock(ProjectTicketService.class);
        }

        @Bean
        ServiceCommerceService commerce() {
            return mock(ServiceCommerceService.class);
        }

        @Bean ServiceNotificationService notifications(){return mock(ServiceNotificationService.class);}

        @Bean
        PluginIntegrationService plugins() {
            return mock(PluginIntegrationService.class);
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
    @Autowired ProjectCenterService projects;
    @Autowired ProjectClientService clients;
    @Autowired ProjectClientTicketService clientTickets;
    @Autowired ProjectApiKeyService projectKeys;
    @Autowired ServiceCommerceService commerce;
    @Autowired PluginIntegrationService plugins;
    @Autowired RateLimitService limiter;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(projects, commerce, plugins, limiter, clients, projectKeys, clientTickets);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    UsernamePasswordAuthenticationToken auth(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                7L, null, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void downstreamWebRoutesRequireLoginAndExternalKeyCannotReplaceJwt() throws Exception {
        for(String path:List.of("/project-client-tickets","/project-clients","/project-clients/catalog","/project-clients/stats","/project-client-operations","/project-api-keys/OWNER","/project-api-calls")) {
            mvc.perform(get("/api"+path).contextPath("/api").header("X-Project-Key","npo_"+"a".repeat(64))).andExpect(status().isUnauthorized());
            mvc.perform(get("/api"+path).contextPath("/api").with(authentication(auth("ROLE_USER")))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        }
        verify(projectKeys,never()).authenticate(anyString());
        mvc.perform(post("/api/project-api-keys/OWNER").contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json").content("{\"version\":0,\"password\":{\"secret-never-echo\":true},\"access\":\"MANAGE\",\"days\":30,\"consent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-never-echo"))));
    }

    @Test
    void downstreamExternalEndpointsRequireTheirOwnSingleHeaderAndRejectUrlCredentials() throws Exception {
        String root="/api/external/projects/v1",key="npo_"+"a".repeat(64);
        var caller=new ProjectClientTypes.Caller(7L,"cred-1",1L,null,true);
        when(projectKeys.authenticate(key)).thenReturn(caller);
        mvc.perform(get(root+"/catalog").contextPath("/api").with(authentication(auth("ROLE_ADMIN")))).andExpect(status().isUnauthorized());
        mvc.perform(get(root+"/catalog").contextPath("/api").param("api_key",key)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(root+"/catalog").contextPath("/api").header("X-Project-Key",key,key)).andExpect(status().isUnauthorized());
        mvc.perform(get(root+"/catalog").contextPath("/api").header("X-Project-Key",key)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(clients).projects(caller,1,50);
        verify(projectKeys).record(caller,"CATALOG",true);
        mvc.perform(get(root+"/self").contextPath("/api").header("X-Project-Key",key)).andExpect(status().isForbidden());
        verify(projectKeys).record(caller,"SELF",false);
        verify(projectKeys,never()).web();
    }

    @Test
    void localTicketRoutesPassOnlyTheResolvedCallerAndNeverInjectJwtRoles() throws Exception {
        String id="b038e810-6a0d-461c-8dc6-a2aa1aa061bd", key="npc_"+"a".repeat(64);
        String root="/api/external/projects/v1/tickets";
        var caller=new ProjectClientTypes.Caller(7L,"cred-support",1L,id,false);
        when(projectKeys.authenticate(key)).thenReturn(caller);
        when(clientTickets.create(eq(caller),any())).thenAnswer(invocation -> {
            assertTrue(SecurityContextHolder.getContext().getAuthentication() == null
                    || SecurityContextHolder.getContext().getAuthentication() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            return null;
        });
        mvc.perform(get(root).contextPath("/api").header("X-Project-Key",key)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(clientTickets).list(caller,null,null,null,1,20);
        mvc.perform(post(root).contextPath("/api").header("X-Project-Key",key).contentType("application/json")
                .content("{\"requestId\":\""+id+"\",\"clientId\":\""+id+"\",\"kind\":\"BUG\",\"title\":\"Local problem\",\"description\":\"Only text\",\"requestedAmount\":\"0\",\"consent\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(clientTickets).create(eq(caller),any());
        verify(projectKeys).record(caller,"TICKET_CREATE",true);
        verify(projectKeys,never()).web();
    }

    @Test
    void ticketApiDoesNotAcceptJwtDuplicateHeadersOrQueryCredentials() throws Exception {
        String root="/api/external/projects/v1/tickets", key="npc_"+"a".repeat(64);
        mvc.perform(get(root).contextPath("/api").with(authentication(auth("ROLE_ADMIN")))).andExpect(status().isUnauthorized());
        mvc.perform(get(root).contextPath("/api").header("X-Project-Key",key,key)).andExpect(status().isUnauthorized());
        mvc.perform(get(root).contextPath("/api").header("X-Project-Key",key).param("customer_api_key",key)).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(clientTickets);
    }

    @Test
    void localTicketWebUsesLoginForAllMutationsAndDoesNotEchoMalformedContent() throws Exception {
        String root="/api/project-client-tickets", id="b038e810-6a0d-461c-8dc6-a2aa1aa061bd";
        var caller=new ProjectClientTypes.Caller(7L,null,null,null,true);
        when(projectKeys.web()).thenReturn(caller);
        for(String suffix:List.of("/"+id,"/"+id+"/replies","/by-request/"+id)) {
            mvc.perform(get(root+suffix).contextPath("/api")).andExpect(status().isUnauthorized());
            mvc.perform(get(root+suffix).contextPath("/api").with(authentication(auth("ROLE_USER")))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        }
        mvc.perform(post(root+"/"+id+"/decision").contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json")
                .content("{\"requestId\":\""+id+"\",\"version\":0,\"action\":\"APPROVE\",\"note\":\"Review only\",\"consent\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(clientTickets).decide(eq(caller),eq(id),any());
        mvc.perform(post(root).contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json")
                .content("{\"title\":{\"must-not-echo-content\":true}}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("must-not-echo-content"))));
        verify(limiter,atLeastOnce()).check(argThat(r -> "project-client:user".equals(r.dimension())));
    }

    @Test
    void ticketApiPreservesServiceDenialAndRecordsOnlySafeFailedAction() throws Exception {
        String id="b038e810-6a0d-461c-8dc6-a2aa1aa061bd",key="npc_"+"a".repeat(64);
        var caller=new ProjectClientTypes.Caller(7L,"cred-support",1L,id,false);
        when(projectKeys.authenticate(key)).thenReturn(caller);
        when(clientTickets.decide(eq(caller),eq(id),any())).thenThrow(new com.course.platform.common.exception.BusinessException(com.course.platform.common.result.ResultCode.FORBIDDEN));
        mvc.perform(post("/api/external/projects/v1/tickets/"+id+"/decision").contextPath("/api").header("X-Project-Key",key).contentType("application/json")
                .content("{\"requestId\":\""+id+"\",\"version\":0,\"action\":\"APPROVE\",\"note\":\"Review only\",\"consent\":true}"))
                .andExpect(status().isForbidden());
        verify(projectKeys).record(caller,"TICKET_DECISION",false);
        verifyNoInteractions(clients);
    }

    @Test
    void customerApiResponseDoesNotDiscloseOwnerRateOrRefundBudget() throws Exception {
        String key="npc_"+"b".repeat(64);
        var c=new ProjectClientTypes.Caller(7L,"cred-2",1L,"customer-1",false);
        when(projectKeys.authenticate(key)).thenReturn(c);
        when(clients.client(c,"customer-1")).thenReturn(new ProjectClientTypes.ClientView("customer-1",1L,"local project","private owner label","ACTIVE",1L,"12.0","0.333333","12.0","4.00",null));
        mvc.perform(get("/api/external/projects/v1/self").contextPath("/api").header("X-Project-Key",key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("customer-1")).andExpect(jsonPath("$.data.balance").value("12.0"))
                .andExpect(jsonPath("$.data.unitPrice").doesNotExist()).andExpect(jsonPath("$.data.refundBudget").doesNotExist()).andExpect(jsonPath("$.data.label").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(key))));
    }

    @Test
    void catalogueRefreshUsesPlatformPermissionNotOrderOrAdminRoleAndRequiresValidForm() throws Exception {
        String path="/api/admin/platforms/price-refreshes";
        String form="{\"providerId\":9,\"scope\":\"ALL_EXISTING\",\"multiplier\":\"1.25\"}";
        mvc.perform(post(path).contextPath("/api").contentType("application/json").content(form)).andExpect(status().isUnauthorized());
        for(String permission:List.of("ROLE_ADMIN","order:update","platform:read"))
            mvc.perform(post(path).contextPath("/api").with(authentication(auth(permission))).contentType("application/json").content(form)).andExpect(status().isForbidden());
        mvc.perform(post(path).contextPath("/api").with(authentication(auth("platform:update"))).contentType("application/json").content(form)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(path+"/batch-id").contextPath("/api").with(authentication(auth("platform:update")))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post(path+"/batch-id/confirm").contextPath("/api").with(authentication(auth("platform:update"))).contentType("application/json").content("{\"consent\":true}")).andExpect(status().isOk());
        mvc.perform(post(path).contextPath("/api").with(authentication(auth("platform:update"))).contentType("application/json").content("{\"providerId\":9,\"scope\":\"ALL_EXISTING\",\"multiplier\":\"1e999\"}")).andExpect(status().isUnprocessableEntity());
        verify(limiter, atLeastOnce()).check(argThat(r -> "catalog-refresh:preview:user".equals(r.dimension())));
    }

    @Test
    void notificationEndpointsRequireLoginAndDoNotCacheOrReplaySecretBodies() throws Exception {
        for(String path:List.of("/service-orders/order/notifications","/service-orders/order/notifications/deliveries","/service-notification-deliveries/id")){
            mvc.perform(get("/api"+path).contextPath("/api")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api"+path).contextPath("/api").with(authentication(auth("ROLE_USER")))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        }
        mvc.perform(put("/api/service-orders/order/notifications").contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json")
                .content("{\"token\":\"privateShowdocKey0123456789abcdef\",\"version\":0,\"consent\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(put("/api/service-orders/order/notifications").contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json")
                .content("{\"token\":\"https://evil.example/private-key\",\"version\":0,\"consent\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/service-orders/order/notifications/verify").contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json")
                .content("{\"code\":{\"private-secret\":true},\"version\":1,\"consent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret"))));
        mvc.perform(delete("/api/service-orders/order/notifications").contextPath("/api").with(authentication(auth("ROLE_USER"))).contentType("application/json")
                .content("{\"version\":1,\"consent\":true}")).andExpect(status().isOk());
        verify(limiter,atLeastOnce()).check(argThat(r->"order:user".equals(r.dimension())));
    }

    @Test
    void ticketRoutesRespectAuthenticationNoStoreAndDualReviewPermissions() throws Exception {
        for (String path :
                List.of(
                        "/project-tickets",
                        "/project-ticket-operations/op",
                        "/admin/project-tickets")) {
            mvc.perform(get("/api" + path).contextPath("/api"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(
                        get("/api/project-tickets")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        get("/api/admin/project-tickets")
                                .contextPath("/api")
                                .with(authentication(auth("api-provider:update"))))
                .andExpect(status().isOk());
        for (String permission :
                List.of("ROLE_USER", "ROLE_ADMIN", "api-provider:update", "payment:reconcile")) {
            mvc.perform(
                            post("/api/admin/project-ticket-operations/op/confirm")
                                    .contextPath("/api")
                                    .with(authentication(auth(permission))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(
                        post("/api/admin/project-tickets/id/review-quotes")
                                .contextPath("/api")
                                .with(
                                        authentication(
                                                auth("api-provider:update", "payment:reconcile")))
                                .contentType("application/json")
                                .content(
                                        "{\"result\":\"approved\",\"note\":\"已经逐项核实的工单审核记录\",\"version\":1,\"upstreamChecked\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        post("/api/project-accounts/id/tickets")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER")))
                                .contentType("application/json")
                                .content(
                                        "{\"type\":\"bug\",\"title\":\"服务问题\",\"description\":\"请核实本人执行记录\",\"compensationAmount\":0,\"confirmedPolicy\":true}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/project-accounts/id/tickets")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER")))
                                .contentType("application/json")
                                .content(
                                        "{\"type\":\"bug\",\"title\":\"服务问题\",\"description\":\"描述\",\"compensationAmount\":-1,\"confirmedPolicy\":true}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ordinaryUsersCanListPreviewConfirmAndReadOnlyTheirServiceScope() throws Exception {
        mvc.perform(
                        get("/api/service-projects")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        post("/api/service-projects/1/quotes")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER")))
                                .contentType("application/json")
                                .content("{\"action\":\"PROVISION\",\"confirmedPolicy\":true}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/project-operations/op/confirm")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER"))))
                .andExpect(status().isOk());
        verify(projects).projects(1, 20, false);
        verify(projects).quote(eq(1L), any());
        verify(projects).confirm("op");
        verify(limiter, atLeastOnce())
                .check(
                        argThat(
                                r ->
                                        "order:user".equals(r.dimension())
                                                && "7".equals(r.keyMaterial())));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/service-projects",
                "/project-operations/op",
                "/admin/service-projects",
                "/admin/project-operations"
            })
    void anonymousRequestsCannotReachNativeProjectServices(String path) throws Exception {
        mvc.perform(get("/api" + path).contextPath("/api")).andExpect(status().isUnauthorized());
        verifyNoInteractions(projects);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN", "payment:reconcile"})
    void adminLookingRoleOrFinancePermissionDoesNotGrantProjectManagement(String permission)
            throws Exception {
        mvc.perform(
                        get("/api/admin/service-projects")
                                .contextPath("/api")
                                .with(authentication(auth(permission))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(projects);
    }

    @Test
    void actualProviderPermissionCanReachEveryNativeAdminControllerInsteadOfGlobalDenyAll()
            throws Exception {
        for (String path :
                List.of(
                        "/admin/service-projects",
                        "/admin/service-products",
                        "/admin/service-orders",
                        "/admin/plugin-integrations"))
            mvc.perform(
                            get("/api" + path)
                                    .contextPath("/api")
                                    .with(authentication(auth("api-provider:update"))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(
                        get("/api/admin/service-project-catalog")
                                .contextPath("/api")
                                .param("providerId", "9")
                                .with(authentication(auth("api-provider:update"))))
                .andExpect(status().isOk());
        verify(projects).catalog(9L);
        verify(commerce).products(1, 20, true);
        verify(commerce).orders(1, 20, true);
        verify(plugins).listIntegrations();
    }

    @Test
    void bothPermissionsAreRequiredForFinancialReadsAndSettlement() throws Exception {
        for (String permission :
                List.of("api-provider:update", "payment:reconcile", "ROLE_ADMIN")) {
            mvc.perform(
                            get("/api/admin/project-operations")
                                    .contextPath("/api")
                                    .with(authentication(auth(permission))))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/admin/project-operations/id/resolve")
                                    .contextPath("/api")
                                    .with(authentication(auth(permission)))
                                    .contentType("application/json")
                                    .content(
                                            "{\"outcome\":\"NOT_ACCEPTED\",\"evidence\":\"已与上游完整核实确实未受理该操作\",\"upstreamChecked\":true}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(projects);
        mvc.perform(
                        get("/api/admin/project-operations")
                                .contextPath("/api")
                                .with(
                                        authentication(
                                                auth("api-provider:update", "payment:reconcile"))))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/admin/project-operations/id/resolve")
                                .contextPath("/api")
                                .with(
                                        authentication(
                                                auth("api-provider:update", "payment:reconcile")))
                                .contentType("application/json")
                                .content(
                                        "{\"outcome\":\"NOT_ACCEPTED\",\"evidence\":\"已与上游完整核实确实未受理该操作\",\"upstreamChecked\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(projects).resolve(eq("id"), any());
    }

    @Test
    void nativeServiceFinancialPermissionsAreStillEnforcedAfterAllowingItsAdminRoute()
            throws Exception {
        mvc.perform(
                        get("/api/admin/service-order-operations/id")
                                .contextPath("/api")
                                .with(authentication(auth("api-provider:update"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/admin/service-order-operations/id")
                                .contextPath("/api")
                                .with(
                                        authentication(
                                                auth("api-provider:update", "payment:reconcile"))))
                .andExpect(status().isOk());
        verify(commerce).adminOperation("id");
        mvc.perform(
                        get("/api/admin/unknown-native-route")
                                .contextPath("/api")
                                .with(
                                        authentication(
                                                auth("api-provider:update", "payment:reconcile"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedOrInvalidMoneyNeverReachesTheService() throws Exception {
        mvc.perform(
                        post("/api/service-projects/1/quotes")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER")))
                                .contentType("application/json")
                                .content("{\"action\":\"TOP_UP\",\"units\":{\"private-value\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
        mvc.perform(
                        post("/api/service-projects/1/quotes")
                                .contextPath("/api")
                                .with(authentication(auth("ROLE_USER")))
                                .contentType("application/json")
                                .content("{\"action\":\"TOP_UP\",\"units\":\"-1\"}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(projects);
    }
}
