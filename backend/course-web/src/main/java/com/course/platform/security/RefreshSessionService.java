package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.domain.entity.RefreshToken;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.persistence.mapper.RefreshTokenMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.JwtUtil;
import com.course.platform.shared.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Server-side refresh session lifecycle. Refresh credentials are opaque, one-time,
 * stored only as SHA-256 hashes, and bound to an access-token session id (sid).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private static final Pattern REFRESH_FORMAT = Pattern.compile("^rt_[a-f0-9]{64}$");
    private static final Pattern FAMILY_FORMAT = Pattern.compile("^[a-f0-9]{32}$");

    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SystemConfigService systemConfigService;
    private final SecurityAuditService securityAuditService;

    public record SessionTokens(String accessToken, String refreshToken, String familyId, User user) {}

    @Transactional(rollbackFor = Exception.class)
    public SessionTokens issue(User user) {
        if (user == null || user.getId() == null) {
            throw new SessionAuthenticationException(ResultCode.USER_NOT_FOUND);
        }
        User lockedUser = userMapper.selectByIdForUpdate(user.getId());
        if (lockedUser == null) {
            throw new SessionAuthenticationException(ResultCode.USER_NOT_FOUND);
        }
        if (SystemVariableCache.getStatusValue("user_status", "disabled") == lockedUser.getStatus()) {
            throw new SessionAuthenticationException(ResultCode.ACCOUNT_DISABLED);
        }
        String familyId = TokenHashUtil.randomHex(16);
        String rawRefresh = newRefreshToken();
        LocalDateTime now = LocalDateTime.now();
        insertRefresh(user.getId(), rawRefresh, familyId, now);
        String access = jwtUtil.generateTokenForSession(
                lockedUser.getUid(), lockedUser.getUsername(), familyId);
        return new SessionTokens(access, rawRefresh, familyId, lockedUser);
    }

    @Transactional(
            rollbackFor = Exception.class,
            noRollbackFor = {SessionAuthenticationException.class, RefreshTokenReuseException.class}
    )
    public SessionTokens rotate(String rawRefresh) {
        if (rawRefresh == null || !REFRESH_FORMAT.matcher(rawRefresh).matches()) {
            throw new SessionAuthenticationException(ResultCode.TOKEN_INVALID);
        }
        String tokenHash = TokenHashUtil.sha256(rawRefresh);
        RefreshToken current = refreshTokenMapper.selectByHash(tokenHash);
        if (current == null || current.getTokenFamilyId() == null
                || !FAMILY_FORMAT.matcher(current.getTokenFamilyId()).matches()) {
            throw new SessionAuthenticationException(ResultCode.TOKEN_INVALID);
        }
        // Global lock order is user row, then refresh rows by primary key. Password/status
        // changes use the same order, preventing a session from escaping credential revocation.
        User user = userMapper.selectByIdForUpdate(current.getUserId());
        refreshTokenMapper.lockFamily(current.getTokenFamilyId());
        current = refreshTokenMapper.selectByHash(tokenHash);
        if (current == null) {
            throw new SessionAuthenticationException(ResultCode.TOKEN_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        if (current.getRevokedAt() != null) {
            refreshTokenMapper.revokeFamily(current.getTokenFamilyId(), "REUSE_DETECTED", now);
            securityAuditService.record("REFRESH_TOKEN_REUSE", "CRITICAL", current.getUserId(), null,
                    "/auth/refresh", "POST", "检测到已使用 Refresh Token 重放",
                    "family=" + familyPrefix(current.getTokenFamilyId()));
            log.warn("Refresh token reuse detected; userId={}, family={}",
                    current.getUserId(), familyPrefix(current.getTokenFamilyId()));
            throw new RefreshTokenReuseException();
        }
        if (current.getExpireTime() == null || !current.getExpireTime().isAfter(now)) {
            refreshTokenMapper.revokeFamily(current.getTokenFamilyId(), "EXPIRED", now);
            throw new SessionAuthenticationException(ResultCode.TOKEN_EXPIRED);
        }

        if (user == null) {
            refreshTokenMapper.revokeFamily(current.getTokenFamilyId(), "USER_MISSING", now);
            throw new SessionAuthenticationException(ResultCode.USER_NOT_FOUND);
        }
        if (SystemVariableCache.getStatusValue("user_status", "disabled") == user.getStatus()) {
            refreshTokenMapper.revokeFamily(current.getTokenFamilyId(), "ACCOUNT_DISABLED", now);
            throw new SessionAuthenticationException(ResultCode.ACCOUNT_DISABLED);
        }

        String replacement = newRefreshToken();
        String replacementHash = TokenHashUtil.sha256(replacement);
        if (refreshTokenMapper.rotateIfActive(current.getId(), replacementHash, safeIp(), now) != 1) {
            // The row was locked, so failure means the session changed unexpectedly. Fail closed.
            refreshTokenMapper.revokeFamily(current.getTokenFamilyId(), "ROTATION_CONFLICT", now);
            throw new RefreshTokenReuseException();
        }
        insertRefresh(user.getId(), replacement, current.getTokenFamilyId(), now);
        String access = jwtUtil.generateTokenForSession(user.getUid(), user.getUsername(), current.getTokenFamilyId());
        return new SessionTokens(access, replacement, current.getTokenFamilyId(), user);
    }

    @Transactional(rollbackFor = Exception.class)
    public int revokeAll(Long userId, String reason) {
        if (userId == null) {
            return 0;
        }
        userMapper.selectByIdForUpdate(userId);
        refreshTokenMapper.lockUserSessions(userId);
        return refreshTokenMapper.revokeAllForUser(userId, safeReason(reason), LocalDateTime.now());
    }

    public boolean isSessionActive(Long userId, String familyId) {
        if (userId == null || familyId == null || !FAMILY_FORMAT.matcher(familyId).matches()) {
            return false;
        }
        return refreshTokenMapper.countActiveFamily(userId, familyId, LocalDateTime.now()) > 0;
    }

    private void insertRefresh(Long userId, String rawRefresh, String familyId, LocalDateTime issuedAt) {
        int expireDays = Math.max(1, Math.min(30,
                systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7)));
        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .token(null)
                .tokenHash(TokenHashUtil.sha256(rawRefresh))
                .tokenFamilyId(familyId)
                .issuedAt(issuedAt)
                .expireTime(issuedAt.plusDays(expireDays))
                .lastUsedIp(safeIp())
                .deviceInfo(safeDeviceInfo())
                .build();
        if (refreshTokenMapper.insert(entity) != 1) {
            throw new IllegalStateException("Refresh session insert failed");
        }
    }

    private String newRefreshToken() {
        return "rt_" + TokenHashUtil.randomHex(32);
    }

    private String safeIp() {
        try {
            String ip = ServletUtil.getClientIp();
            return truncate(ip, 64);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String safeDeviceInfo() {
        try {
            HttpServletRequest request = ServletUtil.getRequest();
            return request == null ? null : truncate(request.getHeader("User-Agent"), 255);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeReason(String reason) {
        String value = reason == null || reason.isBlank() ? "REVOKED" : reason;
        return truncate(value.replaceAll("[^A-Za-z0-9_-]", "_"), 64);
    }

    private String familyPrefix(String familyId) {
        return familyId == null ? "unknown" : familyId.substring(0, Math.min(8, familyId.length())) + "...";
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("[\\r\\n\\t]", " ");
        return clean.substring(0, Math.min(max, clean.length()));
    }
}
