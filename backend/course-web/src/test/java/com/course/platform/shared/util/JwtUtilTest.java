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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private static final String UID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UPPERCASE_UID = "550E8400-E29B-41D4-A716-446655440000";
    private static final String SID = "0123456789abcdef0123456789abcdef";
    private static final String SECRET = "jwt-test-secret-key-with-at-least-32-bytes";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        when(systemConfigService.getConfigValueAsInteger("token_expire_minutes", 15)).thenReturn(15);
        jwtUtil = new JwtUtil(systemConfigService);
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "activeProfile", "test");
    }

    @Test
    @DisplayName("Access Token 绑定服务端 sid、带唯一 jti 且只写入公开 UUID")
    void accessTokenCarriesRequiredSessionClaims() {
        String token = jwtUtil.generateTokenForSession(UPPERCASE_UID, "alice", SID);
        Claims claims = jwtUtil.getClaimsFromToken(token);

        assertNotNull(claims);
        assertEquals(UID, claims.getSubject());
        assertEquals(UID, claims.get(Constants.USER_UID_KEY, String.class));
        assertEquals("alice", claims.get(Constants.USERNAME_KEY, String.class));
        assertEquals("access", claims.get("type", String.class));
        assertEquals(SID, claims.get("sid", String.class));
        assertNotNull(claims.getId());
        assertTrue(claims.getId().matches("[a-f0-9]{32}"));
        assertFalse(claims.containsKey("userId"));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("同一服务端 session 续签 Access Token 时保留 sid 并生成新 jti")
    void sameSessionPreservesSidAndChangesJti() {
        Claims first = jwtUtil.getClaimsFromToken(jwtUtil.generateTokenForSession(UID, "alice", SID));
        Claims second = jwtUtil.getClaimsFromToken(jwtUtil.generateTokenForSession(UID, "alice", SID));

        assertEquals(SID, first.get("sid", String.class));
        assertEquals(SID, second.get("sid", String.class));
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("缺 sid 或 jti 的历史 Access Token 自动失效")
    void legacyAccessTokensWithoutSessionClaimsAreRejected() {
        assertFalse(jwtUtil.validateToken(signedAccess(UID, UID, null, "legacy-jti", "access")));
        assertFalse(jwtUtil.validateToken(signedAccess(UID, UID, SID, null, "access")));
    }

    @Test
    @DisplayName("旧数字 userId Token 自动失效")
    void legacyNumericUserIdTokenIsRejected() {
        Date now = new Date();
        String token = Jwts.builder()
                .subject("1")
                .claim("userId", 1L)
                .claim(Constants.USERNAME_KEY, "legacy-user")
                .claim("type", "access")
                .claim("sid", SID)
                .id("legacy-jti")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertNull(jwtUtil.getUserUidFromToken(token));
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("uid 与 subject 不一致的 Token 被拒绝")
    void mismatchedSubjectIsRejected() {
        assertFalse(jwtUtil.validateToken(signedAccess(
                "00000000-0000-4000-8000-000000000000", UID, SID, "jti", "access")));
    }

    @Test
    @DisplayName("JWT 类型的旧 Refresh Token 不能作为 Access Token")
    void legacyJwtRefreshTypeIsRejected() {
        assertFalse(jwtUtil.validateToken(signedAccess(UID, UID, SID, "jti", "refresh")));
    }

    @Test
    @DisplayName("生成 Token 时拒绝非法 UUID 或未规范化 session ID")
    void invalidIdentityCannotBeSigned() {
        assertThrows(IllegalArgumentException.class,
                () -> jwtUtil.generateTokenForSession("1", "legacy-user", SID));
        assertThrows(IllegalArgumentException.class,
                () -> jwtUtil.generateTokenForSession(UID, "alice", "not-a-session-id"));
        assertThrows(IllegalArgumentException.class,
                () -> jwtUtil.generateTokenForSession(UID, "alice", SID.toUpperCase()));
    }

    private String signedAccess(String subject, String uid, String sid, String jti, String type) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(subject)
                .claim(Constants.USER_UID_KEY, uid)
                .claim(Constants.USERNAME_KEY, "alice")
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L));
        if (sid != null) builder.claim("sid", sid);
        if (jti != null) builder.id(jti);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
