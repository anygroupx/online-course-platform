package com.course.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validJwtLoadsRolesAndPermissionsFromTrustedDatabase() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserAuthorityService authorityService = mock(UserAuthorityService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userMapper, authorityService);

        User user = new User();
        user.setId(42L);
        user.setUid("550e8400-e29b-41d4-a716-446655440000");
        user.setStatus(1);
        when(jwtUtil.validateToken("signed-token")).thenReturn(true);
        when(jwtUtil.getUserUidFromToken("signed-token")).thenReturn(user.getUid());
        when(jwtUtil.getUsernameFromToken("signed-token")).thenReturn("alice");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(authorityService.loadAuthorities(42L)).thenReturn(List.of(
                new SimpleGrantedAuthority("ROLE_FINANCE"),
                new SimpleGrantedAuthority("payment:refund")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payment/orders");
        request.addHeader("Authorization", "Bearer signed-token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(42L, authentication.getPrincipal());
        assertEquals(List.of("ROLE_FINANCE", "payment:refund"),
                authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList());
        verify(authorityService).loadAuthorities(42L);
    }

    @Test
    void validJwtWithoutServerSideRoleIsNotAuthenticated() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserAuthorityService authorityService = mock(UserAuthorityService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userMapper, authorityService);

        User user = new User();
        user.setId(7L);
        user.setUid("550e8400-e29b-41d4-a716-446655440000");
        user.setStatus(1);
        when(jwtUtil.validateToken("signed-token")).thenReturn(true);
        when(jwtUtil.getUserUidFromToken("signed-token")).thenReturn(user.getUid());
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(authorityService.loadAuthorities(7L)).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/query");
        request.addHeader("Authorization", "Bearer signed-token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
