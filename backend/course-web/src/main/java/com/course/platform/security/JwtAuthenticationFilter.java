package com.course.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.common.constant.Constants;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器（附带角色权限）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final UserAuthorityService userAuthorityService;
    private final RefreshSessionService refreshSessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = getTokenFromRequest(request);
            if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                String uid = jwtUtil.getUserUidFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                String sessionId = jwtUtil.getSessionIdFromToken(token);
                User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getUid, uid)
                        .last("LIMIT 1"));

                if (user != null) {
                    if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    if (!refreshSessionService.isSessionActive(user.getId(), sessionId)) {
                        log.info("拒绝已撤销或过期的服务端会话，uid={}", uid);
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                    var authorities = userAuthorityService.loadAuthorities(user.getId());
                    if (authorities.isEmpty()) {
                        log.warn("拒绝没有有效 RBAC 角色的用户，uid={}", uid);
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // 内部认证主体继续使用数据库主键，避免业务层和关联表暴露或依赖公开 UUID。
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT认证成功，用户UUID: {}, 用户名: {}, authorities={}", uid, username, authorities);
                }
            }
        } catch (Exception e) {
            log.error("JWT认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(Constants.TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(Constants.TOKEN_PREFIX)) {
            return bearerToken.substring(Constants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
