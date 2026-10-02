package com.course.platform.security;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.auth.AuthService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.servicecommerce.ServiceCommerceService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.common.security.TotpUtil;
import com.course.platform.config.*;
import com.course.platform.controller.*;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.service.impl.*;
import com.course.platform.shared.exception.GlobalExceptionHandler;
import com.course.platform.shared.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real password/refresh/MFA services, JWT, SQL authorities, cookies and production security chain. */
@SpringJUnitWebConfig(RbacAuthContractTest.Config.class)
@TestPropertySource(properties = {
        "jwt.secret=rbac-contract-8f1ab725c9494f49b480d6317d64a246",
        "app.crypto.secret=rbac-contract-crypto-8f1ab725c9494f49b480d6317d64a246"
})
class RbacAuthContractTest {
    @Configuration @EnableWebMvc @EnableTransactionManagement
    @Import({SecurityConfig.class, AuthController.class, MfaController.class, RbacAdminController.class,
            ServiceCommerceController.class, SystemVariableController.class, AuthServiceImpl.class, MfaServiceImpl.class,
            RefreshSessionService.class, UserAuthorityService.class, RbacAdministrationService.class,
            JwtUtil.class, JwtAuthenticationFilter.class, MustChangePasswordFilter.class,
            JwtAuthenticationEntryPoint.class, RateLimitFilter.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean RbacTestDatabase database() throws Exception { return new RbacTestDatabase(); }
        @Bean PlatformTransactionManager transactions(RbacTestDatabase db) { return new DataSourceTransactionManager(db.dataSource); }
        @Bean UserMapper users(RbacTestDatabase db) { return db.mapper(UserMapper.class); }
        @Bean UserAuthorityMapper grants(RbacTestDatabase db) { return db.mapper(UserAuthorityMapper.class); }
        @Bean RefreshTokenMapper tokens(RbacTestDatabase db) { return db.mapper(RefreshTokenMapper.class); }
        @Bean MfaChallengeMapper challenges(RbacTestDatabase db) { return db.mapper(MfaChallengeMapper.class); }
        @Bean SecurityAuditService audit(RbacTestDatabase db) {
            return new SecurityAuditServiceImpl(db.mapper(SecurityAuditLogMapper.class), mock(SecurityAlertNotifier.class));
        }
        @Bean SystemConfigService config() {
            var config = mock(SystemConfigService.class);
            when(config.getConfigValueAsInteger(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
            return config;
        }
        @Bean AuthCookieService cookies(SystemConfigService config) {
            var properties = new AuthCookieProperties();
            properties.setSecure(false); // Loopback test server only.
            return new AuthCookieService(properties, config);
        }
        @Bean OperationLogService logs() { return mock(OperationLogService.class); }
        @Bean com.course.platform.application.service.system.SystemVariableService variables() {
            return mock(com.course.platform.application.service.system.SystemVariableService.class);
        }
        @Bean TurnstileVerifier turnstile() { return mock(TurnstileVerifier.class); }
        @Bean LoginProtectionService loginProtection() {
            var service = mock(LoginProtectionService.class);
            when(service.check(anyString(), anyString())).thenReturn(new LoginProtectionDecision(true, false, 0, false, 0));
            return service;
        }
        @Bean ServiceCommerceService commerce() { return mock(ServiceCommerceService.class); }
        @Bean RateLimitService limiter() { var service = mock(RateLimitService.class); when(service.check(any())).thenReturn(RateLimitDecision.allowed(100)); return service; }
        @Bean RateLimitProperties limits() { return new RateLimitProperties(); }
        @Bean CorsProperties cors() { return new CorsProperties(); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
    }

    private static final String PASSWORD = "Rbac-test-password-9!";
    private static final String CRYPTO = "rbac-contract-crypto-8f1ab725c9494f49b480d6317d64a246";
    private static final Map<String, List<String>> ROLES = Map.of(
            "admin", List.of("SUPER_ADMIN"), "operator", List.of("OPERATOR"), "finance", List.of("FINANCE"),
            "support", List.of("CUSTOMER_SERVICE"), "auditor", List.of("AUDITOR"), "ordinary", List.of("USER"),
            "combined", List.of("OPERATOR", "FINANCE"));
    @Autowired WebApplicationContext context;
    @Autowired RbacTestDatabase db;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired ServiceCommerceService commerce;
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        db.jdbc.update("DELETE FROM sys_user_role");
        for (String table : List.of("refresh_token", "mfa_challenge", "security_audit_log", "sys_user")) db.jdbc.update("DELETE FROM " + table);
        String hash = encoder.encode(PASSWORD);
        for (var entry : ROLES.entrySet()) {
            db.jdbc.update("INSERT INTO sys_user(uid,username,password) VALUES(?,?,?)", UUID.randomUUID().toString(), entry.getKey(), hash);
            for (String role : entry.getValue()) db.jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u CROSS JOIN sys_role r WHERE u.username=? AND r.role_code=?", entry.getKey(), role);
        }
        reset(commerce);
        when(commerce.products(anyInt(), anyInt(), eq(true))).thenReturn(new Page<>(1, 20));
        var order = json.readValue("""
                {"id":"10000000-0000-4000-8000-000000000001","title":"权限测试订单","accountLabel":"已隐藏账号",
                "providerType":"heisha","project":"default","status":"PENDING","fulfillmentMode":"SELF_OPERATED",
                "quantity":10,"completed":0,"distance":"2.00","paidAmount":"5.00","refundedAmount":"0.00",
                "version":0,"actions":[],"verificationStatus":"VERIFIED"}
                """, com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderView.class);
        when(commerce.orders(anyInt(), anyInt(), eq(true))).thenReturn(new Page<com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderView>(1, 20, 1).setRecords(List.of(order)));
        when(commerce.fulfillmentAdmin(anyString())).thenReturn(new com.course.platform.domain.servicecommerce.ServiceCommerceTypes.FulfillmentAdminView(
                order.id(), Map.of("school_name", "测试学校"), "VERIFIED", 0L, 0L, null, null, null,
                List.of(new com.course.platform.domain.servicecommerce.ServiceCommerceTypes.FulfillmentAssetView(
                        "10000000-0000-4000-8000-000000000002", "FACE_QUALIFICATION", "image/png", 1, 1, 68, 0L, null, null))));
        when(commerce.fulfillmentAsset(anyString(), anyString())).thenReturn(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aP1sAAAAASUVORK5CYII="));
    }

    private MvcResult login(String username) throws Exception {
        return mvc.perform(post("/api/auth/login").contextPath("/api").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andReturn();
    }
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }
    private Set<String> strings(JsonNode array) { Set<String> result = new TreeSet<>(); array.forEach(n -> result.add(n.asText())); return result; }
    private String uid(String username) { return db.jdbc.queryForObject("SELECT uid FROM sys_user WHERE username=?", String.class, username); }

    @Test void realLoginPublishesAllDatabaseRolesAndThePermissionUnion() throws Exception {
        for (var entry : ROLES.entrySet()) {
            var response = login(entry.getKey());
            JsonNode profile = data(response);
            assertEquals(new TreeSet<>(entry.getValue()), strings(profile.path("roles")));
            var expected = new TreeSet<>(db.jdbc.queryForList("SELECT DISTINCT p.permission_code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_user_role ur ON ur.role_id=rp.role_id JOIN sys_user u ON u.id=ur.user_id WHERE u.username=?", String.class, entry.getKey()));
            assertEquals(expected, strings(profile.path("permissions")));
            assertEquals(entry.getKey().equals("admin"), profile.path("isAdmin").asBoolean());
            assertFalse(profile.has("refreshToken"));
            String claims = new String(Base64.getUrlDecoder().decode(profile.path("token").asText().split("\\.")[1]), StandardCharsets.UTF_8);
            assertFalse(claims.contains("permissions")); assertFalse(claims.contains("roles"));
            Cookie refresh = response.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);
            Cookie csrf = response.getResponse().getCookie(AuthCookieService.CSRF_COOKIE);
            var refreshed = mvc.perform(post("/api/auth/refresh").contextPath("/api").cookie(refresh, csrf)
                    .header("X-CSRF-Token", csrf.getValue())).andExpect(status().isOk()).andReturn();
            var refreshedProfile = data(refreshed);
            assertEquals(strings(profile.path("roles")), strings(refreshedProfile.path("roles")));
            assertEquals(expected, strings(refreshedProfile.path("permissions")));
            assertEquals(profile.path("role"), refreshedProfile.path("role"));
            assertEquals(profile.path("isAdmin"), refreshedProfile.path("isAdmin"));
        }
        var combined = data(login("combined"));
        assertEquals("FINANCE", combined.path("role").asText());
        assertTrue(strings(combined.path("permissions")).containsAll(List.of("service-product:update", "service-order:fulfill", "service-order:refund", "service-order:reconcile")));
    }

    @Test void oldJwtImmediatelyLosesAuthorityAndRefreshPublishesTheNewSnapshot() throws Exception {
        var operator = login("operator");
        String token = data(operator).path("token").asText();
        String path = "/api/admin/service-orders/10000000-0000-4000-8000-000000000001/fulfillment/assets/10000000-0000-4000-8000-000000000002";
        mvc.perform(get(path).contextPath("/api").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        String admin = data(login("admin")).path("token").asText();
        mvc.perform(put("/api/admin/rbac/users/" + uid("operator") + "/roles").contextPath("/api")
                .header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isOk());
        var audit = db.jdbc.queryForMap("SELECT * FROM security_audit_log WHERE event_type='RBAC_ROLE_CHANGED'");
        assertEquals("/api/admin/rbac/users/" + uid("operator") + "/roles", audit.get("request_path"));
        assertEquals("PUT", audit.get("http_method"));
        assertTrue(audit.get("detail").toString().contains("previousRoles=[OPERATOR]"));
        assertTrue(audit.get("detail").toString().contains("newRoles=[USER]"));
        assertNotNull(audit.get("create_time"));
        mvc.perform(get(path).contextPath("/api").header("Authorization", "Bearer " + token)
                .header("X-Permissions", "service-order:fulfill,service-order:biometric")
                .param("permissions", "service-order:biometric")).andExpect(status().isForbidden());
        verify(commerce, times(1)).fulfillmentAsset(anyString(), anyString());
        Cookie refresh = operator.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE);
        Cookie csrf = operator.getResponse().getCookie(AuthCookieService.CSRF_COOKIE);
        var refreshed = mvc.perform(post("/api/auth/refresh").contextPath("/api").cookie(refresh, csrf)
                .header("X-CSRF-Token", csrf.getValue())).andExpect(status().isOk()).andReturn();
        assertEquals(Set.of("USER"), strings(data(refreshed).path("roles")));
        assertTrue(strings(data(refreshed).path("permissions")).isEmpty());
    }

    @Test void mfaSuccessUsesTheSameDatabaseProjectionAndCookieContract() throws Exception {
        String secret = TotpUtil.generateSecret();
        db.jdbc.update("UPDATE sys_user SET mfa_enabled=1,mfa_secret=? WHERE username='admin'", SecretCrypto.encrypt(secret, CRYPTO));
        var challenge = data(login("admin"));
        assertTrue(challenge.path("mfaRequired").asBoolean());
        assertTrue(challenge.path("token").isNull());
        var verified = mvc.perform(post("/api/auth/mfa/verify").contextPath("/api").contentType("application/json")
                .content(json.writeValueAsString(Map.of("challengeId", challenge.path("mfaChallengeId").asText(), "code", TotpUtil.generateCode(secret)))))
                .andExpect(status().isOk()).andReturn();
        var profile = data(verified);
        assertEquals(Set.of("SUPER_ADMIN"), strings(profile.path("roles")));
        assertTrue(strings(profile.path("permissions")).containsAll(List.of("rbac:manage", "service-order:biometric", "service-order:refund")));
        assertNotNull(verified.getResponse().getCookie(AuthCookieService.REFRESH_COOKIE));
        assertFalse(profile.has("refreshToken"));
    }

    @Test void readOnlySystemConfigurationPermissionSurvivesTheCoarseHttpBoundary() throws Exception {
        String reader = data(login("auditor")).path("token").asText();
        mvc.perform(get("/api/admin/variables").contextPath("/api").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/variables/types").contextPath("/api").header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk());
        String body = """
                {"variableKey":"rbac_test","variableName":"测试变量","variableType":"system_config","variableValue":"fixture"}
                """;
        mvc.perform(post("/api/admin/variables").contextPath("/api").header("Authorization", "Bearer " + reader)
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/variables/1").contextPath("/api").header("Authorization", "Bearer " + reader))
                .andExpect(status().isForbidden());
        String writer = data(login("operator")).path("token").asText();
        mvc.perform(post("/api/admin/variables").contextPath("/api").header("Authorization", "Bearer " + writer)
                .contentType("application/json").content(body)).andExpect(status().isOk());
        String ordinary = data(login("ordinary")).path("token").asText();
        mvc.perform(get("/api/admin/variables").contextPath("/api").header("Authorization", "Bearer " + ordinary))
                .andExpect(status().isForbidden());
    }

    @Test void browserUsesRealLoginResponsesAndCannotForgeServerPermissions() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("rbac.browser"), "Opt-in real browser contract; enabled in CI");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            try {
                var request = request(HttpMethod.valueOf(exchange.getRequestMethod()), exchange.getRequestURI()).contextPath("/api");
                // Preserve the browser-facing host through the loopback proxy. Otherwise
                // MockMvc's default localhost:80 incorrectly treats same-origin POSTs as CORS.
                var publicUri = java.net.URI.create("http://" + exchange.getRequestHeaders().getFirst("Host"));
                request.with(servlet -> {
                    servlet.setServerName(publicUri.getHost());
                    servlet.setServerPort(publicUri.getPort() < 0 ? 80 : publicUri.getPort());
                    return servlet;
                });
                exchange.getRequestHeaders().forEach((name, values) -> request.header(name, values.toArray()));
                String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
                if (cookieHeader != null) for (String cookie : cookieHeader.split(";")) {
                    String[] parts = cookie.trim().split("=", 2);
                    if (parts.length == 2) request.cookie(new Cookie(parts[0], parts[1]));
                }
                request.content(exchange.getRequestBody().readAllBytes());
                var response = mvc.perform(request).andReturn().getResponse();
                for (String name : response.getHeaderNames()) exchange.getResponseHeaders().put(name, new ArrayList<>(response.getHeaders(name)));
                byte[] body = response.getContentAsByteArray();
                exchange.sendResponseHeaders(response.getStatus(), body.length == 0 ? -1 : body.length);
                exchange.getResponseBody().write(body);
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
            finally { exchange.close(); }
        });
        server.start();
        Process process = null;
        var browserLog = java.nio.file.Files.createTempFile("rbac-browser-", ".log");
        try {
            // Route subprocess output through Java, not Surefire's protocol stdout FD.
            var builder = new ProcessBuilder("node", "tests/rbac.browser.mjs")
                    .directory(db.root.resolve("frontend").toFile())
                    .redirectErrorStream(true).redirectOutput(browserLog.toFile());
            builder.environment().put("RBAC_AUTH_URL", "http://127.0.0.1:" + server.getAddress().getPort());
            builder.environment().put("RBAC_TEST_PASSWORD", PASSWORD);
            process = builder.start();
            assertTrue(process.waitFor(180, TimeUnit.SECONDS), "Browser contract timed out");
            assertEquals(0, process.exitValue());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            server.stop(0);
            System.out.print(java.nio.file.Files.readString(browserLog));
            java.nio.file.Files.deleteIfExists(browserLog);
        }
    }
}
