package com.course.platform.security;

import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 强制首次/重置后修改密码：除改密与登出外拦截业务接口
 */
@Component
@RequiredArgsConstructor
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isAllowedWhenMustChange(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        try {
            Object principal = authentication.getPrincipal();
            userId = principal instanceof Long id ? id : Long.parseLong(principal.toString());
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userMapper.selectById(userId);
        if (user != null && user.getMustChangePassword() != null && user.getMustChangePassword() == 1) {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(ResultCode.FORBIDDEN.getCode(), "必须先修改默认/重置密码后才能继续操作")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedWhenMustChange(String path) {
        return path.contains("/users/change-password")
                || path.contains("/auth/logout")
                || path.contains("/auth/refresh")
                || path.contains("/auth/login")
                || path.contains("/auth/mfa")
                || path.contains("/health")
                || path.contains("/ping");
    }
}
