package com.course.platform.shared.util;

import com.course.platform.common.constant.Constants;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.common.util.PublicUidUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private final com.course.platform.application.service.system.SystemConfigService systemConfigService;

    public JwtUtil(com.course.platform.application.service.system.SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }



    /**
     * 生成密钥（生产/通用：密钥必须显式配置且长度足够）
     */
    private SecretKey getSecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required and must be configured");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        String lower = secret.toLowerCase();
        boolean placeholder = lower.contains("change_me")
                || "secret".equals(lower)
                || "jwt_secret".equals(lower)
                || lower.startsWith("change_me");
        if (placeholder) {
            if (!"dev".equalsIgnoreCase(activeProfile) && !"local".equalsIgnoreCase(activeProfile)) {
                throw new IllegalStateException("JWT_SECRET looks like a placeholder; refuse to start in non-dev profile");
            }
            log.warn("JWT_SECRET looks weak/placeholder; only acceptable in dev/local");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Generate an access JWT bound to an existing server-side refresh family. */
    public String generateTokenForSession(String uid, String username, String sessionId) {
        String normalizedUid = requireValidUid(uid);
        if (sessionId == null || !sessionId.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("sessionId must be a 128-bit lowercase hex value");
        }
        Date now = new Date();
        int expireMinutes = Math.max(1, Math.min(60,
                systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15)));
        Date expiryDate = new Date(now.getTime() + expireMinutes * 60 * 1000L);

        return Jwts.builder()
                .subject(normalizedUid)
                .claim(Constants.USER_UID_KEY, normalizedUid)
                .claim(Constants.USERNAME_KEY, username)
                .claim("type", "access")
                .claim("sid", sessionId)
                .id(TokenHashUtil.randomHex(16))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 从Token中获取Claims
     *
     * @param token Token字符串
     * @return Claims对象
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("解析Token失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从Token中获取对外用户 UUID
     *
     * @param token Token字符串
     * @return 对外用户 UUID
     */
    public String getUserUidFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get(Constants.USER_UID_KEY, String.class);
    }

    /**
     * 从Token中获取用户名
     *
     * @param token Token字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get(Constants.USERNAME_KEY, String.class);
    }

    public String getSessionIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims == null ? null : claims.get("sid", String.class);
    }

    /**
     * 验证Token是否有效
     *
     * @param token Token字符串
     * @return true-有效 false-无效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims == null) {
                return false;
            }
            String type = claims.get("type", String.class);
            String sessionId = claims.get("sid", String.class);
            Date expiration = claims.getExpiration();
            return "access".equals(type)
                    && hasMatchingPublicUid(claims)
                    && claims.getId() != null && !claims.getId().isBlank()
                    && sessionId != null && sessionId.matches("[a-f0-9]{32}")
                    && expiration != null
                    && !expiration.before(new Date());
        } catch (Exception e) {
            log.error("验证Token失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断Token是否过期
     *
     * @param token Token字符串
     * @return true-已过期 false-未过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims == null) {
                return true;
            }
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private String requireValidUid(String uid) {
        if (!PublicUidUtil.isValid(uid)) {
            throw new IllegalArgumentException("uid must be a valid UUID v4");
        }
        return PublicUidUtil.normalize(uid);
    }

    private boolean hasMatchingPublicUid(Claims claims) {
        String uid = claims.get(Constants.USER_UID_KEY, String.class);
        String subject = claims.getSubject();
        return PublicUidUtil.isValid(uid)
                && PublicUidUtil.isValid(subject)
                && PublicUidUtil.normalize(uid).equals(PublicUidUtil.normalize(subject));
    }
}
