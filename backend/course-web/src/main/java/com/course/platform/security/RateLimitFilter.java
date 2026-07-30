package com.course.platform.security;

import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.shared.util.ServletUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录/注册/外部API/支付接口限流
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = safeIp();
        String key = null;
        int limit = 0;
        long window = 60_000L;

        if (path.endsWith("/auth/login") || path.contains("/auth/login")) {
            key = "login:" + ip;
            limit = 10;
        } else if (path.contains("/register")) {
            key = "register:" + ip;
            limit = 5;
        } else if (path.contains("/external/")) {
            key = "external:" + ip + ":" + request.getParameter("uid");
            limit = 120;
        } else if (path.contains("/payment/notify") || path.contains("/payment/create") || path.contains("/payment/sync")) {
            key = "payment:" + ip + ":" + path;
            limit = 60;
        }

        if (key != null && !rateLimiter.tryAcquire(key, limit, window)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage())));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String safeIp() {
        try {
            return ServletUtil.getClientIp();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
