package com.course.platform.security;

import com.course.platform.application.service.auth.AuthService;
import com.course.platform.application.service.security.MfaService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.config.AuthCookieProperties;
import com.course.platform.controller.AuthController;
import com.course.platform.controller.MfaController;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.dto.MfaVerifyLoginRequest;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthSessionHttpContractTest {

    private static final String OLD_REFRESH = "rt_" + "a".repeat(64);
    private static final String NEW_REFRESH = "rt_" + "b".repeat(64);
    private static final String CSRF = "c".repeat(64);

    private AuthService authService;
    private TurnstileVerifier turnstileVerifier;
    private AuthCookieService cookieService;
    private LoginProtectionService loginProtectionService;
    private AuthController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        turnstileVerifier = mock(TurnstileVerifier.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getConfigValueAsInteger("refresh_token_expire_days", 7)).thenReturn(7);
        AuthCookieProperties properties = new AuthCookieProperties();
        properties.setSecure(true);
        properties.setSameSite("Strict");
        cookieService = new AuthCookieService(properties, configService);
        loginProtectionService = mock(LoginProtectionService.class);
        when(loginProtectionService.check(anyString(), anyString()))
                .thenReturn(new LoginProtectionDecision(true, false, 0, false, 0));
        controller = new AuthController(authService, turnstileVerifier, mock(UserMapper.class),
                cookieService, loginProtectionService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void loginKeepsRefreshSecretOutOfJsonAndSetsHardenedCookies() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse("access-token", OLD_REFRESH));
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("a-valid-password");
        request.setTurnstileToken("human-proof");
        MockHttpServletResponse http = new MockHttpServletResponse();

        Result<LoginResponse> result = controller.login(request, http);
        String json = objectMapper.writeValueAsString(result);
        List<String> cookies = http.getHeaders(HttpHeaders.SET_COOKIE);

        assertTrue(json.contains("access-token"));
        assertFalse(json.contains("refreshToken"));
        assertFalse(json.contains(OLD_REFRESH));
        assertEquals(2, cookies.size());
        String refreshCookie = cookie(cookies, AuthCookieService.REFRESH_COOKIE);
        assertTrue(refreshCookie.contains(OLD_REFRESH));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertTrue(refreshCookie.contains("Secure"));
        assertTrue(refreshCookie.contains("SameSite=Strict"));
        assertTrue(refreshCookie.contains("Path=/api/auth"));
        String csrfCookie = cookie(cookies, AuthCookieService.CSRF_COOKIE);
        assertFalse(csrfCookie.contains("HttpOnly"));
        assertTrue(csrfCookie.contains("Secure"));
        assertTrue(csrfCookie.contains("SameSite=Strict"));
        assertEquals("no-store, no-cache, must-revalidate", http.getHeader(HttpHeaders.CACHE_CONTROL));
        verify(turnstileVerifier).verify("human-proof", "login", false);
        verify(loginProtectionService).recordSuccess("alice");
    }

    @Test
    void invalidCredentialsIncrementLoginProtectionWithoutAccountEnumeration() {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR));
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown-or-wrong");
        request.setPassword("wrong-password");
        MockHttpServletResponse http = new MockHttpServletResponse();

        BusinessException error = assertThrows(BusinessException.class, () -> controller.login(request, http));

        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), error.getCode());
        verify(loginProtectionService).recordFailure(eq("unknown-or-wrong"), anyString());
        verify(loginProtectionService, never()).recordSuccess(anyString());
    }

    @Test
    void blockedLoginStopsBeforeTurnstileAndPasswordVerification() {
        when(loginProtectionService.check(eq("attacked"), anyString()))
                .thenReturn(new LoginProtectionDecision(false, true, 20, false, 45));
        LoginRequest request = new LoginRequest();
        request.setUsername("attacked");
        request.setPassword("not-used");

        RateLimitExceededException error = assertThrows(RateLimitExceededException.class,
                () -> controller.login(request, new MockHttpServletResponse()));

        assertEquals(45, error.getRetryAfterSeconds());
        verifyNoInteractions(authService);
        verifyNoInteractions(turnstileVerifier);
    }

    @Test
    void mfaLoginAlsoWritesRefreshOnlyToCookie() throws Exception {
        MfaService mfaService = mock(MfaService.class);
        when(mfaService.verifyLogin(any(MfaVerifyLoginRequest.class)))
                .thenReturn(loginResponse("mfa-access", OLD_REFRESH));
        MfaController mfaController = new MfaController(mfaService, cookieService);
        MfaVerifyLoginRequest request = new MfaVerifyLoginRequest();
        request.setChallengeId("d".repeat(32));
        request.setCode("123456");
        MockHttpServletResponse http = new MockHttpServletResponse();

        Result<LoginResponse> result = mfaController.verify(request, http);
        String json = objectMapper.writeValueAsString(result);

        assertTrue(json.contains("mfa-access"));
        assertFalse(json.contains("refreshToken"));
        assertFalse(json.contains(OLD_REFRESH));
        assertEquals(2, http.getHeaders(HttpHeaders.SET_COOKIE).size());
    }

    @Test
    void refreshWithoutCookieIsUnauthorizedBeforeCsrfEvaluation() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.refresh(request, new MockHttpServletResponse()));
        assertEquals(ResultCode.TOKEN_INVALID.getCode(), error.getCode());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshWithMissingOrWrongCsrfIsForbidden() {
        for (String header : new String[]{null, "d".repeat(64)}) {
            MockHttpServletRequest request = refreshRequest(OLD_REFRESH, CSRF, header);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> controller.refresh(request, new MockHttpServletResponse()));
            assertEquals(ResultCode.FORBIDDEN.getCode(), error.getCode());
        }
        verifyNoInteractions(authService);
    }

    @Test
    void refreshRotatesCookieAndNeverSerializesEitherRefreshCredential() throws Exception {
        when(authService.refresh(OLD_REFRESH)).thenReturn(loginResponse("new-access", NEW_REFRESH));
        MockHttpServletRequest request = refreshRequest(OLD_REFRESH, CSRF, CSRF);
        MockHttpServletResponse http = new MockHttpServletResponse();

        String json = objectMapper.writeValueAsString(controller.refresh(request, http));

        assertTrue(json.contains("new-access"));
        assertFalse(json.contains("refreshToken"));
        assertFalse(json.contains(OLD_REFRESH));
        assertFalse(json.contains(NEW_REFRESH));
        assertTrue(cookie(http.getHeaders(HttpHeaders.SET_COOKIE), AuthCookieService.REFRESH_COOKIE)
                .contains(NEW_REFRESH));
        verify(authService).refresh(OLD_REFRESH);
    }

    @Test
    void logoutRevokesServerSessionAndExpiresBothCookies() {
        MockHttpServletResponse http = new MockHttpServletResponse();
        var authentication = new UsernamePasswordAuthenticationToken(42L, null, List.of());

        controller.logout(authentication, http);

        verify(authService).logout(42L);
        List<String> cookies = http.getHeaders(HttpHeaders.SET_COOKIE);
        assertEquals(2, cookies.size());
        assertTrue(cookie(cookies, AuthCookieService.REFRESH_COOKIE).contains("Max-Age=0"));
        assertTrue(cookie(cookies, AuthCookieService.CSRF_COOKIE).contains("Max-Age=0"));
    }

    private LoginResponse loginResponse(String access, String refresh) {
        return LoginResponse.builder()
                .token(access)
                .refreshToken(refresh)
                .uid("550e8400-e29b-41d4-a716-446655440000")
                .username("alice")
                .role("USER")
                .mfaRequired(false)
                .build();
    }

    private MockHttpServletRequest refreshRequest(String refresh, String csrfCookie, String csrfHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie(AuthCookieService.REFRESH_COOKIE, refresh),
                new Cookie(AuthCookieService.CSRF_COOKIE, csrfCookie));
        if (csrfHeader != null) request.addHeader(AuthCookieService.CSRF_HEADER, csrfHeader);
        return request;
    }

    private String cookie(List<String> values, String name) {
        return values.stream().filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow();
    }
}
