package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.shared.util.ServletUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Redis-backed application limiter for authentication and high-impact business actions. */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = normalizedPath(request);
        String ip = safeIp();
        try {
            for (Limit limit : limits(request, path, ip)) {
                RateLimitDecision decision = rateLimitService.check(new RateLimitRequest(
                        limit.dimension(), limit.keyMaterial(), limit.rule().getLimit(),
                        Duration.ofSeconds(limit.rule().getWindowSeconds()), limit.action()));
                if (!decision.allowed()) {
                    deny(request, response, limit, decision.retryAfterSeconds());
                    return;
                }
            }
        } catch (BusinessException ex) {
            if (ResultCode.RATE_LIMIT_UNAVAILABLE.getCode().equals(ex.getCode())) {
                unavailable(response);
                return;
            }
            throw ex;
        }
        filterChain.doFilter(request, response);
    }

    private List<Limit> limits(HttpServletRequest request, String path, String ip) {
        List<Limit> values = new ArrayList<>();
        String method = request.getMethod();
        String user = currentUserKey();
        if ("POST".equals(method) && "/auth/login".equals(path)) {
            values.add(new Limit("login:ip", ip, properties.getLoginIp(), "login"));
        } else if ("POST".equals(method) && "/register".equals(path)) {
            values.add(new Limit("register:ip", ip, properties.getRegisterIp(), "register"));
        } else if (path.startsWith("/register/validate-invite-code") || path.contains("invite")) {
            values.add(new Limit("invite:ip", ip, properties.getInviteIp(), "invite"));
        } else if ("POST".equals(method) && "/auth/refresh".equals(path)) {
            values.add(new Limit("refresh:ip", ip, properties.getRefreshIp(), "refresh"));
            String refresh = cookie(request, AuthCookieService.REFRESH_COOKIE);
            if (refresh != null) {
                values.add(new Limit("refresh:credential", refresh,
                        properties.getRefreshCredential(), "refresh"));
            }
        }

        if (path.startsWith("/external/")) {
            values.add(new Limit("external:ip", ip, properties.getExternalIp(), "external"));
            String apiKey = firstNonBlank(request.getParameter("key"), request.getParameter("apiKey"));
            if (apiKey != null) values.add(new Limit("external:key", apiKey, properties.getExternalKey(), "external"));
        }
        if (path.contains("password") && isWrite(method)) {
            values.add(new Limit("password:ip", ip, properties.getPasswordIp(), "password"));
            if (user != null) {
                values.add(new Limit("password:user", user, properties.getPasswordUser(), "password"));
            }
        }
        if (isWrite(method) && (path.equals("/orders") || path.startsWith("/orders/create")
                || path.startsWith("/orders/batch"))) {
            values.add(new Limit("order:" + (user == null ? "ip" : "user"),
                    user == null ? ip : user, properties.getOrderUser(), "order"));
        }
        if (path.startsWith("/payment/") && isWrite(method) && !path.equals("/payment/notify")) {
            boolean refund = path.contains("refund");
            RateLimitProperties.Rule rule = refund ? properties.getRefundUser() : properties.getPaymentUser();
            values.add(new Limit((refund ? "refund:" : "payment:") + (user == null ? "ip" : "user"),
                    user == null ? ip : user, rule, refund ? "refund" : "payment"));
        }
        if (path.contains("export")) {
            values.add(new Limit("export:" + (user == null ? "ip" : "user"),
                    user == null ? ip : user, properties.getExportUser(), "export"));
        }
        return values;
    }

    private void deny(HttpServletRequest request, HttpServletResponse response, Limit limit, long retry)
            throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retry)));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage())));
        securityAuditService.record("RATE_LIMIT_TRIGGERED", "WARN", currentUserId(), null,
                normalizedPath(request), request.getMethod(), "应用限流已触发",
                "dimension=" + limit.dimension() + ",action=" + limit.action() + ",retryAfter=" + retry);
    }

    private void unavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "5");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.RATE_LIMIT_UNAVAILABLE)));
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isBlank() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
    }

    private String currentUserKey() {
        Long id = currentUserId();
        return id == null ? null : id.toString();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second != null && !second.isBlank() ? second : null;
    }

    private String safeIp() {
        try {
            return ServletUtil.getClientIp();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private record Limit(String dimension, String keyMaterial, RateLimitProperties.Rule rule, String action) {}
}
