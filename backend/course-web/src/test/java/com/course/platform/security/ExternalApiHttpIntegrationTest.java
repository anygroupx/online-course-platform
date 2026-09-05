package com.course.platform.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.course.platform.application.service.auth.ApiKeyService;
import com.course.platform.application.service.course.CourseQueryService;
import com.course.platform.application.service.order.CourseOrderService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.*;
import com.course.platform.controller.ApiKeyController;
import com.course.platform.controller.ExternalApiController;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.service.impl.CourseOrderServiceImpl;
import com.course.platform.service.impl.AccountLedgerServiceImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production MVC + security filters, with synthetic data and no live upstream/financial writes. */
@SpringJUnitWebConfig(ExternalApiHttpIntegrationTest.Config.class)
class ExternalApiHttpIntegrationTest {
    private static final String UID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String KEY = "integration-only-" + "a".repeat(32);
    @Autowired WebApplicationContext context;
    @Autowired UserMapper users;
    @Autowired CourseOrderMapper orders;
    @Autowired CoursePlatformMapper platforms;
    @Autowired CourseOrderService orderService;
    @Autowired CourseQueryService courseService;
    @Autowired RateLimitService limiter;
    @Autowired JwtUtil jwt;
    @Autowired RefreshSessionService refresh;
    @Autowired ApiKeyService keyService;
    @Autowired SecurityAuditService audit;
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        reset(users, orders, platforms, orderService, courseService, limiter, jwt, refresh, keyService, audit);
        for (Class<?> entity : List.of(User.class, CourseOrder.class, CoursePlatform.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entity);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = new User();
        user.setId(7L); user.setUid(UID); user.setStatus(1); user.setBalance(new BigDecimal("123.45"));
        user.setApiKeyHash(TokenHashUtil.sha256(KEY)); user.setApiKeyPrefix(KEY.substring(0, 8));
        user.setApiKeyScopes("balance:read, orders:read,orders:write,platforms:read");
        user.setApiKeyExpireTime(LocalDateTime.now().plusDays(10));
        when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(users.selectById(7L)).thenReturn(user);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(100));
        CourseOrder order = new CourseOrder();
        order.setId(88L); order.setOrderNo("ORD-test"); order.setUserId(7L); order.setRetryCount(0);
        order.setStudentAccount("student"); order.setStudentPassword("never-expose-password");
        order.setCourseName("示例课程"); order.setOrderStatus(1); order.setProgress("50%");
        when(orders.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orders.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        when(orders.selectById(88L)).thenReturn(order);
        when(orderService.createOrder(any(OrderCreateRequest.class), eq(7L))).thenReturn(88L);
        when(courseService.queryCourses(any(QueryCourseRequest.class), eq(7L)))
                .thenReturn(CourseInfoResponse.builder().studentAccount("student").courses(List.of()).build());
        CoursePlatform platform = new CoursePlatform();
        platform.setId(1L); platform.setName("平台"); platform.setBasePrice(new BigDecimal("2.00"));
        when(platforms.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(platform));
    }

    private MockHttpServletRequestBuilder request(String endpoint, String alias) {
        return post("/api/external/" + endpoint).contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uid", UID).param(alias, KEY).param("platform", "1").param("school", "学校")
                .param("user", "student").param("pass", "student-password").param("username", "student")
                .param("kcname", "课程").param("kcid", "course-1").param("orderNo", "ORD-test");
    }

    @ParameterizedTest
    @CsvSource({"getmoney,key", "getmoney,api_key", "get-platforms,key", "get-platforms,api_key",
            "query-courses,key", "query-courses,api_key", "add,key", "add,api_key", "chadan,key", "chadan,api_key",
            "query-progress,key", "query-progress,api_key", "budan,key", "budan,api_key"})
    void everyRouteWorksWithoutJwtWithBothCredentialNames(String endpoint, String alias) throws Exception {
        String body = mvc.perform(request(endpoint, alias)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").exists()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(KEY)); assertFalse(body.contains("never-expose-password"));
        verifyNoInteractions(jwt, refresh);
        ArgumentCaptor<RateLimitRequest> limits = ArgumentCaptor.forClass(RateLimitRequest.class);
        verify(limiter, times(2)).check(limits.capture());
        assertEquals(List.of("external:ip", "external:key"), limits.getAllValues().stream().map(RateLimitRequest::dimension).toList());
        assertEquals(KEY, limits.getAllValues().get(1).keyMaterial());
    }

    @Test void unrelatedBearerDoesNotBlockApiKey() throws Exception {
        mvc.perform(request("getmoney", "api_key").header("Authorization", "Bearer expired-browser-session"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.money").value(123.45));
        verifyNoInteractions(jwt, refresh);
    }
    @Test void migratedOriginalKeyStillWorks() throws Exception {
        String legacy = "old-key-before-hash-migration";
        user.setApiKeyHash(TokenHashUtil.sha256(legacy));
        mvc.perform(post("/api/external/getmoney").contextPath("/api").param("uid", UID).param("key", legacy)).andExpect(status().isOk());
    }
    @ParameterizedTest
    @CsvSource({"getmoney,balance:read", "get-platforms,platforms:read", "query-courses,platforms:read",
            "add,orders:write", "chadan,orders:read", "query-progress,orders:read", "budan,orders:write"})
    void exactScopeStillRequired(String endpoint, String scope) throws Exception {
        user.setApiKeyScopes("not-" + scope);
        mvc.perform(request(endpoint, "api_key")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(-101));
        verifyNoInteractions(orderService, courseService, orders, platforms);
    }
    @Test void aliasConflictRejectedAndIdenticalAliasesAllowed() throws Exception {
        mvc.perform(request("getmoney", "key").param("api_key", "different"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(-2));
        verifyNoInteractions(users);
        mvc.perform(request("getmoney", "key").param("api_key", KEY)).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"wrong", "expired", "unknown-user", "hash-missing"})
    void invalidCredentialsHaveUniformResponse(String mode) throws Exception {
        if (mode.equals("expired")) user.setApiKeyExpireTime(LocalDateTime.now().minusMinutes(1));
        if (mode.equals("unknown-user")) when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        if (mode.equals("hash-missing")) { user.setApiKeyHash(null); user.setApiKey(KEY); }
        if (mode.equals("wrong")) user.setApiKeyHash(TokenHashUtil.sha256("other"));
        mvc.perform(request("getmoney", "api_key")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-205)).andExpect(jsonPath("$.message").value("API密钥无效"));
    }
    @Test void disabledAccountRemainsBlocked() throws Exception {
        user.setStatus(0);
        mvc.perform(request("getmoney", "api_key")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(-105));
    }
    @Test void missingMalformedAndJsonParametersAreClientErrorsNot500() throws Exception {
        mvc.perform(post("/api/external/getmoney").contextPath("/api").param("api_key", KEY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(-2));
        mvc.perform(post("/api/external/getmoney").contextPath("/api").param("uid", UID))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(-2));
        mvc.perform(post("/api/external/getmoney").contextPath("/api").param("uid", "7").param("key", KEY))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(-2));
        mvc.perform(post("/api/external/query-courses").contextPath("/api").param("uid", UID).param("key", KEY)
                        .param("platform", "not-a-number").param("user", "s").param("pass", "p"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(-2));
        mvc.perform(post("/api/external/getmoney").contextPath("/api").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(-2));
    }
    @ParameterizedTest @ValueSource(strings = {"query-progress", "budan", "chadan"})
    void orderSqlIsBoundToKeyOwner(String endpoint) throws Exception {
        mvc.perform(request(endpoint, "api_key")).andExpect(status().isOk());
        ArgumentCaptor<LambdaQueryWrapper<CourseOrder>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        if (endpoint.equals("chadan")) verify(orders).selectList(query.capture()); else verify(orders).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(endpoint.equals("chadan") ? "student" : "ORD-test"));
    }
    @ParameterizedTest @ValueSource(strings = {"query-progress", "budan"})
    void otherUsersOrderCannotBeReadOrRetried(String endpoint) throws Exception {
        when(orders.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        mvc.perform(request(endpoint, "api_key")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(-207));
        verifyNoInteractions(orderService);
    }
    @Test void servicesReceiveOnlyKeyOwnersIdentity() throws Exception {
        mvc.perform(request("add", "api_key").param("userId", "999")).andExpect(status().isOk());
        verify(orderService).createOrder(any(), eq(7L));
        mvc.perform(request("query-courses", "api_key").param("userId", "999")).andExpect(status().isOk());
        verify(courseService).queryCourses(any(), eq(7L));
    }
    @ParameterizedTest @ValueSource(strings = {"key", "api_key"})
    void realRetryServiceAcceptsKeyOwnerWithoutGrantingAdminRights(String alias) throws Exception {
        CourseOrder owned = orders.selectById(88L);
        owned.setPlatformId(1L);
        CoursePlatform platform = new CoursePlatform(); platform.setId(1L); platform.setDockApiId(9L);
        when(platforms.selectById(1L)).thenReturn(platform);
        ApiProviderMapper providers = mock(ApiProviderMapper.class);
        ApiProvider provider = new ApiProvider(); provider.setId(9L); provider.setStatus(1);
        when(providers.selectById(9L)).thenReturn(provider);
        PlatformDockingService docking = mock(PlatformDockingService.class);
        when(docking.retryOrder(owned, platform, provider)).thenReturn(DockResult.success("成功", "upstream-test"));
        ResourceAuthorizationService policy = new ResourceAuthorizationService();
        CourseOrderServiceImpl real = new CourseOrderServiceImpl(orders, platforms, users, mock(OperationLogService.class),
                docking, providers, mock(ApplicationEventPublisher.class), mock(AccountLedgerServiceImpl.class), policy);
        doAnswer(call -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertEquals(7L, auth.getPrincipal()); assertNull(auth.getCredentials());
            assertEquals(List.of("api:orders:write"), auth.getAuthorities().stream().map(a -> a.getAuthority()).toList());
            assertFalse(policy.canReadAllOrders(auth));
            real.retryOrder(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(orderService).retryOrder(88L, 7L);
        mvc.perform(request("budan", alias)).andExpect(status().isOk()).andExpect(jsonPath("$.data.orderNo").value("ORD-test"));
        verify(docking).retryOrder(owned, platform, provider);
        assertEquals(1, owned.getRetryCount());
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "API identity must not outlive its request");
        // Even if the object read changes after the controller lookup, the service must reject it.
        owned.setUserId(99L);
        mvc.perform(request("budan", alias)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(-207));
        verifyNoMoreInteractions(docking);
    }

    @Test void rateLimitsAndRedisFailClosedStillEnforced() throws Exception {
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(5), RateLimitDecision.denied(11));
        mvc.perform(request("getmoney", "api_key")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "11")).andExpect(jsonPath("$.code").value(-109));
        verifyNoInteractions(users);
        when(limiter.check(any())).thenThrow(new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE));
        mvc.perform(request("getmoney", "key")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5")).andExpect(jsonPath("$.code").value(-118));
    }
    @Test void apiKeyDoesNotAuthenticateInternalRoutes() throws Exception {
        for (String route : List.of("/user/info", "/admin/api-providers", "/auth/current"))
            mvc.perform(get("/api" + route).contextPath("/api").param("uid", UID).param("api_key", KEY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/api-keys/rotate").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"not-enough\"}")).andExpect(status().isUnauthorized());
    }
    @Test void unsupportedMethodIs405AndTrustedCorsPreflightWorksWithoutJwt() throws Exception {
        mvc.perform(get("/api/external/getmoney").contextPath("/api"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"));
        mvc.perform(options("/api/external/getmoney").contextPath("/api").header("Origin", "https://trusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://trusted.example"));
        mvc.perform(request("getmoney", "api_key").header("Origin", "https://trusted.example"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
        mvc.perform(options("/api/external/getmoney").contextPath("/api").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    @Test void rotationIsAuthenticatedAndNoStore() throws Exception {
        when(keyService.rotateApiKey(7L, "correct-password")).thenReturn("one-time-key");
        mvc.perform(post("/api/api-keys/rotate").contextPath("/api")
                        .with(authentication(new UsernamePasswordAuthenticationToken(7L, null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"correct-password\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data").value("one-time-key"));
        verify(keyService).rotateApiKey(7L, "correct-password");
    }

    @Configuration @EnableWebMvc
    @Import({SecurityConfig.class, ExternalApiController.class, ApiKeyController.class, GlobalExceptionHandler.class,
            JwtAuthenticationFilter.class, MustChangePasswordFilter.class, JwtAuthenticationEntryPoint.class, RateLimitFilter.class})
    static class Config {
        @Bean UserMapper users() { return mock(UserMapper.class); }
        @Bean CourseOrderMapper orders() { return mock(CourseOrderMapper.class); }
        @Bean CoursePlatformMapper platforms() { return mock(CoursePlatformMapper.class); }
        @Bean CourseOrderService orderService() { return mock(CourseOrderService.class); }
        @Bean CourseQueryService courseService() { return mock(CourseQueryService.class); }
        @Bean RateLimitService limiter() { return mock(RateLimitService.class); }
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean RefreshSessionService refresh() { return mock(RefreshSessionService.class); }
        @Bean UserAuthorityService authority() { return mock(UserAuthorityService.class); }
        @Bean SecurityAuditService audit() { return mock(SecurityAuditService.class); }
        @Bean ApiKeyService keyService() { return mock(ApiKeyService.class); }
        @Bean RateLimitProperties limits() { return new RateLimitProperties(); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean CorsProperties cors() { CorsProperties config = new CorsProperties(); config.setAllowedOrigins(List.of("https://trusted.example")); return config; }
    }
}
