package com.course.platform.security;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/** Writes/reads the HttpOnly refresh cookie and enforces double-submit CSRF on rotation. */
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    public static final String REFRESH_COOKIE = "course_refresh";
    public static final String CSRF_COOKIE = "course_csrf";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final AuthCookieProperties properties;
    private final SystemConfigService systemConfigService;

    public void issue(HttpServletResponse response, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        int days = Math.max(1, Math.min(30,
                systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7)));
        Duration maxAge = Duration.ofDays(days);
        String csrf = TokenHashUtil.randomHex(32);

        ResponseCookie refresh = ResponseCookie.from(REFRESH_COOKIE, rawRefreshToken)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        ResponseCookie csrfCookie = ResponseCookie.from(CSRF_COOKIE, csrf)
                .httpOnly(false)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
        noStore(response);
    }

    public String requireRefreshToken(HttpServletRequest request) {
        String token = cookieValue(request, REFRESH_COOKIE);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        return token;
    }

    public void requireValidCsrf(HttpServletRequest request) {
        String cookie = cookieValue(request, CSRF_COOKIE);
        String header = request.getHeader(CSRF_HEADER);
        if (cookie == null || header == null || cookie.length() != 64 || header.length() != 64
                || !MessageDigest.isEqual(cookie.getBytes(StandardCharsets.US_ASCII),
                header.getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "CSRF 校验失败");
        }
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, expired(REFRESH_COOKIE, true, "/api/auth").toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expired(CSRF_COOKIE, false, "/").toString());
        noStore(response);
    }

    public void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

    private ResponseCookie expired(String name, boolean httpOnly, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
