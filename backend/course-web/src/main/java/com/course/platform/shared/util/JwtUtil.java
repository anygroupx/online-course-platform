package com.course.platform.shared.util;

import com.course.platform.common.constant.Constants;
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
import java.util.Map;

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

    /**
     * 生成Token
     *
     * @param uid 对外用户 UUID
     * @param username 用户名
     * @return Token字符串
     */
    public String generateToken(String uid, String username) {
        String normalizedUid = requireValidUid(uid);
        Date now = new Date();
        // 从数据库读取 Access Token 过期时间（分钟）
        Integer expireMinutes = systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15);
        Date expiryDate = new Date(now.getTime() + expireMinutes * 60 * 1000L);

        return Jwts.builder()
                .subject(normalizedUid)
                .claim(Constants.USER_UID_KEY, normalizedUid)
                .claim(Constants.USERNAME_KEY, username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 生成Token（带额外信息）
     *
     * @param uid 对外用户 UUID
     * @param username 用户名
     * @param claims 额外信息
     * @return Token字符串
     */
    public String generateToken(String uid, String username, Map<String, Object> claims) {
        String normalizedUid = requireValidUid(uid);
        Date now = new Date();
        // 从数据库读取 Access Token 过期时间（分钟）
        Integer expireMinutes = systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15);
        Date expiryDate = new Date(now.getTime() + expireMinutes * 60 * 1000L);

        var builder = Jwts.builder();
        if (claims != null && !claims.isEmpty()) {
            builder.claims(claims);
        }
        return builder
                // 保留字段最后写入，防止额外 claims 覆盖公开 UID 或 subject。
                .subject(normalizedUid)
                .claim(Constants.USER_UID_KEY, normalizedUid)
                .claim(Constants.USERNAME_KEY, username)
                .claim("type", "access")
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
            Date expiration = claims.getExpiration();
            return "access".equals(type)
                    && hasMatchingPublicUid(claims)
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

    /**
     * 刷新Token
     *
     * @param token 旧Token
     * @return 新Token
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null || !validateToken(token)) {
            return null;
        }

        String uid = claims.get(Constants.USER_UID_KEY, String.class);
        String username = claims.get(Constants.USERNAME_KEY, String.class);

        return PublicUidUtil.isValid(uid) ? generateToken(PublicUidUtil.normalize(uid), username) : null;
    }

    /**
     * 生成Refresh Token
     *
     * @param uid 对外用户 UUID
     * @param username 用户名
     * @return Refresh Token字符串
     */
    public String generateRefreshToken(String uid, String username) {
        String normalizedUid = requireValidUid(uid);
        Date now = new Date();
        // 从数据库读取 Refresh Token 过期时间（天）
        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        Date expiryDate = new Date(now.getTime() + expireDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .subject(normalizedUid)
                .claim(Constants.USER_UID_KEY, normalizedUid)
                .claim(Constants.USERNAME_KEY, username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 验证Refresh Token是否有效
     *
     * @param refreshToken Refresh Token字符串
     * @return true-有效 false-无效
     */
    public boolean validateRefreshToken(String refreshToken) {
        try {
            Claims claims = getClaimsFromToken(refreshToken);
            if (claims == null) {
                return false;
            }
            // 验证是否是refresh token类型
            String type = claims.get("type", String.class);
            if (!"refresh".equals(type)) {
                return false;
            }
            Date expiration = claims.getExpiration();
            return hasMatchingPublicUid(claims) && expiration != null && !expiration.before(new Date());
        } catch (Exception e) {
            log.error("验证Refresh Token失败: {}", e.getMessage());
            return false;
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
