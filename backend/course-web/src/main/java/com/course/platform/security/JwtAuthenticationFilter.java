package com.course.platform.security;

import com.course.platform.common.constant.Constants;
import com.course.platform.common.security.SecurityRoles;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT认证过滤器（附带角色权限）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = getTokenFromRequest(request);
            if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                String role = SecurityRoles.USER;
                if (userId != null) {
                    User user = userMapper.selectById(userId);
                    if (user != null) {
                        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        role = resolveRole(user);
                    } else if (Constants.DEFAULT_ADMIN_ID.equals(userId)) {
                        role = SecurityRoles.ADMIN;
                    }
                }
                authorities.add(new SimpleGrantedAuthority(SecurityRoles.toSpringRole(role)));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT认证成功，用户ID: {}, 用户名: {}, 角色: {}", userId, username, role);
            }
        } catch (Exception e) {
            log.error("JWT认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveRole(User user) {
        if (StringUtils.hasText(user.getRole())) {
            return user.getRole().trim().toUpperCase();
        }
        // 兼容历史数据：ID=1 视为管理员
        if (Constants.DEFAULT_ADMIN_ID.equals(user.getId())) {
            return SecurityRoles.ADMIN;
        }
        return SecurityRoles.USER;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(Constants.TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(Constants.TOKEN_PREFIX)) {
            return bearerToken.substring(Constants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
