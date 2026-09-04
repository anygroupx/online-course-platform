package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.security.MfaService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityRoles;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.security.RefreshSessionService;
import com.course.platform.security.UserAuthorityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 认证服务核心登录链路单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String USER_UID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshSessionService refreshSessionService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private MfaService mfaService;
    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private UserAuthorityService userAuthorityService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User buildUser(Long id, String username, int status) {
        User user = new User();
        user.setId(id);
        user.setUid(USER_UID);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setNickname("昵称");
        user.setBalance(new BigDecimal("100.00"));
        user.setRate(new BigDecimal("1.00"));
        user.setStatus(status);
        user.setRole(id != null && id == 1L ? SecurityRoles.ADMIN : SecurityRoles.USER);
        lenient().when(userAuthorityService.getPrimaryRole(id))
                .thenReturn(id != null && id == 1L ? "SUPER_ADMIN" : "USER");
        return user;
    }

    @Test
    @DisplayName("正确账号密码应登录成功并返回双Token")
    void login_success() {
        User user = buildUser(1L, "admin", 1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded")).thenReturn(true);
        when(mfaService.isEnabled(user)).thenReturn(false);
        when(userMapper.updateLoginMetadata(anyLong(), any(LocalDateTime.class), anyString(), anyInt())).thenReturn(1);
        when(refreshSessionService.issue(user)).thenReturn(
                new RefreshSessionService.SessionTokens(
                        "access-token", "rt_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "0123456789abcdef0123456789abcdef", user));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        LoginResponse response = authService.login(request);

        assertEquals("access-token", response.getToken());
        assertTrue(response.getRefreshToken().matches("rt_[a-f0-9]{64}"));
        assertEquals(USER_UID, response.getUid());
        assertTrue(response.getIsAdmin());
        assertFalse(Boolean.TRUE.equals(response.getMfaRequired()));
        verify(operationLogService).log(eq(1L), eq("登录"), anyString(), isNull(), eq(user.getBalance()));
        verify(refreshSessionService).issue(user);
        verify(securityAuditService).record(eq("LOGIN_SUCCESS"), anyString(), eq(1L), eq("admin"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("管理员启用 MFA 时应返回 challenge 且不签发 Token")
    void login_adminMfaRequired() {
        User user = buildUser(1L, "admin", 1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded")).thenReturn(true);
        when(mfaService.isEnabled(user)).thenReturn(true);
        when(mfaService.createChallenge(user)).thenReturn("challenge-abc");
        when(userMapper.updateLoginMetadata(anyLong(), any(LocalDateTime.class), anyString(), anyInt())).thenReturn(1);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        LoginResponse response = authService.login(request);

        assertTrue(Boolean.TRUE.equals(response.getMfaRequired()));
        assertEquals("challenge-abc", response.getMfaChallengeId());
        assertNull(response.getToken());
        assertNull(response.getRefreshToken());
        verify(refreshSessionService, never()).issue(any(User.class));
    }

    @Test
    @DisplayName("用户不存在应返回账号或密码错误")
    void login_userNotFound() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("ghost");
        request.setPassword("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), ex.getCode());
        verify(securityAuditService).record(eq("LOGIN_FAIL"), eq("WARN"), isNull(), eq("ghost"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("密码错误应拒绝登录")
    void login_badPassword() {
        User user = buildUser(2L, "agent", 1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("agent");
        request.setPassword("wrong");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode(), ex.getCode());
        verify(refreshSessionService, never()).issue(any(User.class));
    }

    @Test
    @DisplayName("禁用账号应拒绝登录")
    void login_disabledAccount() {
        // SystemVariableCache 无缓存时 disabled 回退常量 0
        User user = buildUser(3L, "disabled_user", 0);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setUsername("disabled_user");
        request.setPassword("123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ResultCode.ACCOUNT_DISABLED.getCode(), ex.getCode());
        verify(refreshSessionService, never()).issue(any(User.class));
    }
}
