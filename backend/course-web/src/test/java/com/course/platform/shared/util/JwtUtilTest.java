package com.course.platform.shared.util;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.constant.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private static final String UID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPPERCASE_UID = "550E8400-E29B-41D4-A716-446655440000";
    private static final String SECRET = "jwt-test-secret-key-with-at-least-32-bytes";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15)).thenReturn(15);
        when(systemConfigService.getConfigValueAsInteger("refresh_token_expire_days", 7)).thenReturn(7);

        jwtUtil = new JwtUtil(systemConfigService);
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "activeProfile", "test");
    }

    @Test
    @DisplayName("Access Token 仅写入规范化 UUID，不包含旧 userId claim")
    void accessTokenUsesPublicUidOnly() {
        String token = jwtUtil.generateToken(UPPERCASE_UID, "alice");
        Claims claims = jwtUtil.getClaimsFromToken(token);

        assertNotNull(claims);
        assertEquals(UID, claims.getSubject());
        assertEquals(UID, claims.get(Constants.USER_UID_KEY, String.class));
        assertEquals("alice", claims.get(Constants.USERNAME_KEY, String.class));
        assertEquals("access", claims.get("type", String.class));
        assertFalse(claims.containsKey("userId"));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("额外 claims 不能覆盖公开 UUID 和 subject")
    void reservedIdentityClaimsCannotBeOverridden() {
        String token = jwtUtil.generateToken(UID, "alice", Map.of(
                Constants.USER_UID_KEY, "00000000-0000-4000-8000-000000000000",
                "sub", "1",
                "role", "USER"
        ));
        Claims claims = jwtUtil.getClaimsFromToken(token);

        assertNotNull(claims);
        assertEquals(UID, claims.getSubject());
        assertEquals(UID, claims.get(Constants.USER_UID_KEY, String.class));
        assertEquals("USER", claims.get("role", String.class));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("旧数字 userId Token 自动失效")
    void legacyNumericUserIdTokenIsRejected() {
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .subject("1")
                .claim("userId", 1L)
                .claim(Constants.USERNAME_KEY, "legacy-user")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertNull(jwtUtil.getUserUidFromToken(legacyToken));
        assertFalse(jwtUtil.validateToken(legacyToken));
    }

    @Test
    @DisplayName("uid 与 subject 不一致的 Token 被拒绝")
    void mismatchedSubjectIsRejected() {
        Date now = new Date();
        String token = Jwts.builder()
                .subject("00000000-0000-4000-8000-000000000000")
                .claim(Constants.USER_UID_KEY, UID)
                .claim(Constants.USERNAME_KEY, "alice")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("Refresh Token 使用并校验公开 UUID")
    void refreshTokenUsesPublicUid() {
        String token = jwtUtil.generateRefreshToken(UPPERCASE_UID, "alice");
        Claims claims = jwtUtil.getClaimsFromToken(token);

        assertNotNull(claims);
        assertEquals(UID, claims.getSubject());
        assertEquals(UID, claims.get(Constants.USER_UID_KEY, String.class));
        assertFalse(claims.containsKey("userId"));
        assertEquals("refresh", claims.get("type", String.class));
        assertTrue(jwtUtil.validateRefreshToken(token));
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("生成 Token 时拒绝非 UUID v4 uid")
    void invalidUidCannotBeSigned() {
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.generateToken("1", "legacy-user"));
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.generateRefreshToken("not-a-uuid", "legacy-user"));
    }
}
