package com.course.platform.shared.util;

import cn.hutool.core.date.DateUtil;
import com.course.platform.shared.constant.Constants;
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

    @Value("${jwt.secret}")
    private String secret;

    private final com.course.platform.service.SystemConfigService systemConfigService;

    public JwtUtil(com.course.platform.service.SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }



    /**
     * 生成密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @return Token字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        // 从数据库读取 Access Token 过期时间（分钟）
        Integer expireMinutes = systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15);
        Date expiryDate = new Date(now.getTime() + expireMinutes * 60 * 1000L);

        return Jwts.builder()
                .subject(username)
                .claim(Constants.USER_ID_KEY, userId)
                .claim(Constants.USERNAME_KEY, username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 生成Token（带额外信息）
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param claims 额外信息
     * @return Token字符串
     */
    public String generateToken(Long userId, String username, Map<String, Object> claims) {
        Date now = new Date();
        // 从数据库读取 Access Token 过期时间（分钟）
        Integer expireMinutes = systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15);
        Date expiryDate = new Date(now.getTime() + expireMinutes * 60 * 1000L);

        return Jwts.builder()
                .subject(username)
                .claim(Constants.USER_ID_KEY, userId)
                .claim(Constants.USERNAME_KEY, username)
                .claims(claims)
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
     * 从Token中获取用户ID
     * 
     * @param token Token字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get(Constants.USER_ID_KEY, Long.class);
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
            Date expiration = claims.getExpiration();
            return expiration != null && !expiration.before(new Date());
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
        if (claims == null) {
            return null;
        }

        Long userId = claims.get(Constants.USER_ID_KEY, Long.class);
        String username = claims.get(Constants.USERNAME_KEY, String.class);

        return generateToken(userId, username);
    }

    /**
     * 生成Refresh Token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @return Refresh Token字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        // 从数据库读取 Refresh Token 过期时间（天）
        Integer expireDays = systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7);
        Date expiryDate = new Date(now.getTime() + expireDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .subject(username)
                .claim(Constants.USER_ID_KEY, userId)
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
            return expiration != null && !expiration.before(new Date());
        } catch (Exception e) {
            log.error("验证Refresh Token失败: {}", e.getMessage());
            return false;
        }
    }
}

