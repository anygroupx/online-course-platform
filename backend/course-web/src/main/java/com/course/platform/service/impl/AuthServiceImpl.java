package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.auth.AuthService;
import com.course.platform.application.service.security.MfaService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.security.UserAuthorityService;
import com.course.platform.security.RefreshSessionService;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.ServletUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现类（含 Refresh Token 哈希与强制改密）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;
    private final OperationLogService operationLogService;
    private final MfaService mfaService;
    private final SecurityAuditService securityAuditService;
    private final UserAuthorityService userAuthorityService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            securityAuditService.record("LOGIN_FAIL", "WARN", null, request.getUsername(),
                    "/auth/login", "POST", "用户名不存在或密码错误", null);
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            securityAuditService.record("LOGIN_FAIL", "WARN", user.getId(), user.getUsername(),
                    "/auth/login", "POST", "密码错误", null);
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (SystemVariableCache.getStatusValue("user_status", "disabled") == user.getStatus()) {
            securityAuditService.record("LOGIN_FAIL", "WARN", user.getId(), user.getUsername(),
                    "/auth/login", "POST", "账号已禁用", null);
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 禁用默认弱密码继续使用：admin/123456 或任何匹配默认哈希的首次登录
        boolean defaultPassword = passwordEncoder.matches("123456", user.getPassword());
        if (defaultPassword) {
            user.setMustChangePassword(1);
        }

        user.setLastLoginTime(LocalDateTime.now());
        try {
            user.setLastLoginIp(ServletUtil.getClientIp());
        } catch (Exception e) {
            user.setLastLoginIp("127.0.0.1");
        }
        if (userMapper.updateLoginMetadata(user.getId(), user.getLastLoginTime(), user.getLastLoginIp(),
                defaultPassword ? 1 : 0) != 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        String role = resolveRole(user);
        boolean mustChange = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
        boolean mfaEnabled = mfaService.isEnabled(user);

        // 管理员启用 MFA 时，不直接签发 Token，返回 challenge
        if (mfaEnabled && "SUPER_ADMIN".equals(role)) {
            String challengeId = mfaService.createChallenge(user);
            securityAuditService.record("MFA_REQUIRED", "INFO", user.getId(), user.getUsername(),
                    "/auth/login", "POST", "管理员登录需要 MFA", "challengeId=" + challengeId);
            return LoginResponse.builder()
                    .token(null)
                    .refreshToken(null)
                    .uid(user.getUid())
                    .username(user.getUsername())
                    .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                    .balance(null)
                    .rate(null)
                    .isAdmin(true)
                    .role(role)
                    .mustChangePassword(mustChange)
                    .mfaRequired(true)
                    .mfaEnabled(true)
                    .mfaChallengeId(challengeId)
                    .build();
        }

        RefreshSessionService.SessionTokens session = refreshSessionService.issue(user);

        LoginResponse response = LoginResponse.builder()
                .token(session.accessToken())
                .refreshToken(session.refreshToken())
                .uid(user.getUid())
                .username(user.getUsername())
                .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin("SUPER_ADMIN".equals(role))
                .role(role)
                .mustChangePassword(mustChange)
                .mfaRequired(false)
                .mfaEnabled(mfaEnabled)
                .build();

        operationLogService.log(user.getId(), "登录",
                "用户登录成功，IP: " + user.getLastLoginIp(),
                null, user.getBalance());
        securityAuditService.record("LOGIN_SUCCESS", "INFO", user.getId(), user.getUsername(),
                "/auth/login", "POST", "用户登录成功", "ip=" + user.getLastLoginIp());

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId) {
        int revoked = refreshSessionService.revokeAll(userId, "LOGOUT");
        log.info("用户登出并撤销服务端会话: userId={}, revoked={}", userId, revoked);
        operationLogService.log(userId, "登出", "用户主动登出，已撤销服务端会话", null, null);
        securityAuditService.record("LOGOUT", "INFO", userId, null,
                "/auth/logout", "POST", "用户主动登出", "revokedSessions=" + revoked);
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        RefreshSessionService.SessionTokens session = refreshSessionService.rotate(refreshToken);
        User user = session.user();
        String role = resolveRole(user);
        securityAuditService.record("REFRESH_SUCCESS", "INFO", user.getId(), user.getUsername(),
                "/auth/refresh", "POST", "Refresh Token 轮换成功", null);
        return LoginResponse.builder()
                .token(session.accessToken())
                .refreshToken(session.refreshToken())
                .uid(user.getUid())
                .username(user.getUsername())
                .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin("SUPER_ADMIN".equals(role))
                .role(role)
                .mustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1)
                .mfaRequired(false)
                .mfaEnabled(mfaService.isEnabled(user))
                .build();
    }

    private String resolveRole(User user) {
        String role = userAuthorityService.getPrimaryRole(user.getId());
        if (StrUtil.isBlank(role)) {
            securityAuditService.record("ACCESS_DENIED", "WARN", user.getId(), user.getUsername(),
                    "/auth/login", "POST", "账号没有有效的 RBAC 角色", null);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return role;
    }
}
