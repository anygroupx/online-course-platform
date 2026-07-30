package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.auth.AuthService;
import com.course.platform.application.service.security.MfaService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityRoles;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.entity.RefreshToken;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.persistence.mapper.RefreshTokenMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.JwtUtil;
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
    private final JwtUtil jwtUtil;
    private final RefreshTokenMapper refreshTokenMapper;
    private final SystemConfigService systemConfigService;
    private final OperationLogService operationLogService;
    private final MfaService mfaService;
    private final SecurityAuditService securityAuditService;

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
        userMapper.updateById(user);

        String role = resolveRole(user);
        boolean mustChange = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
        boolean mfaEnabled = mfaService.isEnabled(user);

        // 管理员启用 MFA 时，不直接签发 Token，返回 challenge
        if (mfaEnabled && SecurityRoles.ADMIN.equals(role)) {
            String challengeId = mfaService.createChallenge(user);
            securityAuditService.record("MFA_REQUIRED", "INFO", user.getId(), user.getUsername(),
                    "/auth/login", "POST", "管理员登录需要 MFA", "challengeId=" + challengeId);
            return LoginResponse.builder()
                    .token(null)
                    .refreshToken(null)
                    .userId(user.getId())
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

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        LocalDateTime expireTime = LocalDateTime.now().plusDays(expireDays);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(null) // 不再保存明文
                .tokenHash(TokenHashUtil.sha256(refreshToken))
                .tokenFamilyId(TokenHashUtil.randomHex(16))
                .expireTime(expireTime)
                .lastUsedIp(user.getLastLoginIp())
                .build();
        refreshTokenMapper.insert(refreshTokenEntity);

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin(SecurityRoles.ADMIN.equals(role))
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
        log.info("用户登出: userId={}", userId);
        // 撤销该用户全部 refresh token
        RefreshToken update = new RefreshToken();
        update.setRevokedAt(LocalDateTime.now());
        refreshTokenMapper.update(update, new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, userId)
                .isNull(RefreshToken::getRevokedAt));
        operationLogService.log(userId, "登出", "用户主动登出，已撤销Refresh Token", null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        String hash = TokenHashUtil.sha256(refreshToken);
        RefreshToken refreshTokenEntity = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getTokenHash, hash)
                        .isNull(RefreshToken::getRevokedAt)
                        .ge(RefreshToken::getExpireTime, LocalDateTime.now())
        );

        // 兼容旧明文 token
        if (refreshTokenEntity == null) {
            refreshTokenEntity = refreshTokenMapper.selectOne(
                    new LambdaQueryWrapper<RefreshToken>()
                            .eq(RefreshToken::getToken, refreshToken)
                            .isNull(RefreshToken::getRevokedAt)
                            .ge(RefreshToken::getExpireTime, LocalDateTime.now())
            );
        }

        if (refreshTokenEntity == null) {
            // 可能是重放：若 hash 命中已撤销记录，则撤销整个 family
            RefreshToken reused = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                    .eq(RefreshToken::getTokenHash, hash)
                    .isNotNull(RefreshToken::getRevokedAt)
                    .last("LIMIT 1"));
            if (reused != null && reused.getTokenFamilyId() != null) {
                RefreshToken revoke = new RefreshToken();
                revoke.setRevokedAt(LocalDateTime.now());
                refreshTokenMapper.update(revoke, new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getTokenFamilyId, reused.getTokenFamilyId())
                        .isNull(RefreshToken::getRevokedAt));
                log.warn("检测到 Refresh Token 重放，已撤销 family={}", reused.getTokenFamilyId());
            }
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        User user = userMapper.selectById(refreshTokenEntity.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (SystemVariableCache.getStatusValue("user_status", "disabled") == user.getStatus()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        String newToken = jwtUtil.generateToken(user.getId(), user.getUsername());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        LocalDateTime newExpireTime = LocalDateTime.now().plusDays(expireDays);

        // 滚动刷新：撤销旧 token，写入新 hash
        refreshTokenEntity.setRevokedAt(LocalDateTime.now());
        refreshTokenEntity.setReplacedBy(TokenHashUtil.sha256(newRefreshToken));
        refreshTokenMapper.updateById(refreshTokenEntity);

        RefreshToken newEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(null)
                .tokenHash(TokenHashUtil.sha256(newRefreshToken))
                .tokenFamilyId(refreshTokenEntity.getTokenFamilyId() != null
                        ? refreshTokenEntity.getTokenFamilyId()
                        : TokenHashUtil.randomHex(16))
                .expireTime(newExpireTime)
                .lastUsedIp(safeIp())
                .build();
        refreshTokenMapper.insert(newEntity);

        String role = resolveRole(user);
        return LoginResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername())
                .balance(user.getBalance())
                .rate(user.getRate())
                .isAdmin(SecurityRoles.ADMIN.equals(role))
                .role(role)
                .mustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1)
                .mfaRequired(false)
                .mfaEnabled(mfaService.isEnabled(user))
                .build();
    }

    private String resolveRole(User user) {
        if (StrUtil.isNotBlank(user.getRole())) {
            return user.getRole().trim().toUpperCase();
        }
        if (Constants.DEFAULT_ADMIN_ID.equals(user.getId())) {
            return SecurityRoles.ADMIN;
        }
        return SecurityRoles.USER;
    }

    private String safeIp() {
        try {
            return ServletUtil.getClientIp();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
