package com.course.platform.security;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.domain.entity.RefreshToken;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.RefreshTokenMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.shared.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transactions, row locks and unique constraints for refresh-token rotation/reuse. */
class RefreshSessionConcurrencyTest {

    private JdbcTemplate jdbc;
    private ExecutorService executor;
    private RefreshSessionService service;
    private SecurityAuditService auditService;
    private User user;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:refresh_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbc = new JdbcTemplate(dataSource);
        executor = Executors.newFixedThreadPool(2);
        createSchema();

        user = new User();
        user.setId(42L);
        user.setUid("550e8400-e29b-41d4-a716-446655440000");
        user.setUsername("alice");
        user.setStatus(1);

        RefreshTokenMapper tokenMapper = jdbcBackedMapper();
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectByIdForUpdate(42L)).thenReturn(user);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AtomicInteger jwtSequence = new AtomicInteger();
        when(jwtUtil.generateTokenForSession(eq(user.getUid()), eq(user.getUsername()), anyString()))
                .thenAnswer(invocation -> "access-" + jwtSequence.incrementAndGet());
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getConfigValueAsInteger("refresh_token_expire_days", 7)).thenReturn(7);
        auditService = mock(SecurityAuditService.class);

        RefreshSessionService target = new RefreshSessionService(
                tokenMapper, userMapper, jwtUtil, configService, auditService);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionInterceptor transactionAdvice = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(transactionAdvice);
        service = (RefreshSessionService) proxyFactory.getProxy();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void rotationHashesSecretsAndOldTokenReuseRevokesEntireFamily() {
        RefreshSessionService.SessionTokens issued = service.issue(user);

        assertTrue(issued.refreshToken().matches("rt_[a-f0-9]{64}"));
        assertTrue(issued.familyId().matches("[a-f0-9]{32}"));
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_token WHERE token IS NOT NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM refresh_token WHERE token_hash IS NOT NULL"));

        RefreshSessionService.SessionTokens rotated = service.rotate(issued.refreshToken());
        assertEquals(issued.familyId(), rotated.familyId());
        assertNotEquals(issued.refreshToken(), rotated.refreshToken());
        assertTrue(service.isSessionActive(user.getId(), issued.familyId()));
        assertEquals(1, count("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL"));

        assertThrows(RefreshTokenReuseException.class, () -> service.rotate(issued.refreshToken()));

        assertFalse(service.isSessionActive(user.getId(), issued.familyId()));
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL"));
        assertThrows(RefreshTokenReuseException.class, () -> service.rotate(rotated.refreshToken()));
        verify(auditService, atLeast(2)).record(eq("REFRESH_TOKEN_REUSE"), eq("CRITICAL"), eq(42L),
                isNull(), eq("/auth/refresh"), eq("POST"), anyString(), contains("family="));
    }

    @Test
    void twoConcurrentRotationsYieldOneCredentialAndThenFailClosed() throws Exception {
        RefreshSessionService.SessionTokens issued = service.issue(user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return service.rotate(issued.refreshToken());
                } catch (RuntimeException error) {
                    return error;
                }
            }));
        }
        ready.await();
        start.countDown();

        List<Object> results = List.of(futures.get(0).get(), futures.get(1).get());
        assertEquals(1, results.stream().filter(RefreshSessionService.SessionTokens.class::isInstance).count());
        assertEquals(1, results.stream().filter(RefreshTokenReuseException.class::isInstance).count());
        assertEquals(2, count("SELECT COUNT(*) FROM refresh_token"));
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL"));
        assertFalse(service.isSessionActive(user.getId(), issued.familyId()));
        verify(auditService).record(eq("REFRESH_TOKEN_REUSE"), eq("CRITICAL"), eq(42L),
                isNull(), eq("/auth/refresh"), eq("POST"), anyString(), contains("family="));
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE refresh_token (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  token VARCHAR(500),
                  token_hash VARCHAR(128) NOT NULL UNIQUE,
                  token_family_id VARCHAR(64) NOT NULL,
                  issued_at TIMESTAMP NOT NULL,
                  expire_time TIMESTAMP NOT NULL,
                  revoked_at TIMESTAMP,
                  revocation_reason VARCHAR(64),
                  replaced_by VARCHAR(128),
                  last_used_ip VARCHAR(64),
                  device_info VARCHAR(255),
                  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT chk_refresh_plaintext_empty CHECK (token IS NULL)
                )
                """);
    }

    private RefreshTokenMapper jdbcBackedMapper() {
        RefreshTokenMapper mapper = mock(RefreshTokenMapper.class);
        when(mapper.insert(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            return jdbc.update("""
                    INSERT INTO refresh_token
                    (user_id,token,token_hash,token_family_id,issued_at,expire_time,last_used_ip,device_info)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, token.getUserId(), token.getToken(), token.getTokenHash(), token.getTokenFamilyId(),
                    token.getIssuedAt(), token.getExpireTime(), token.getLastUsedIp(), token.getDeviceInfo());
        });
        when(mapper.selectByHash(anyString())).thenAnswer(invocation -> {
            String tokenHash = invocation.getArgument(0, String.class);
            List<RefreshToken> rows = jdbc.query(
                    "SELECT * FROM refresh_token WHERE token_hash=? LIMIT 1 FOR UPDATE",
                    (rs, rowNum) -> mapToken(rs), tokenHash);
            return rows.isEmpty() ? null : rows.get(0);
        });
        when(mapper.lockFamily(anyString())).thenAnswer(invocation -> {
            String familyId = invocation.getArgument(0, String.class);
            return jdbc.queryForList(
                    "SELECT id FROM refresh_token WHERE token_family_id=? ORDER BY id FOR UPDATE",
                    Long.class, familyId);
        });
        when(mapper.rotateIfActive(anyLong(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> jdbc.update("""
                        UPDATE refresh_token
                        SET revoked_at=?, revocation_reason='ROTATED', replaced_by=?, last_used_ip=?, update_time=CURRENT_TIMESTAMP
                        WHERE id=? AND revoked_at IS NULL AND expire_time>?
                        """, invocation.getArgument(3), invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(0), invocation.getArgument(3)));
        when(mapper.revokeFamily(anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> jdbc.update("""
                        UPDATE refresh_token SET revoked_at=COALESCE(revoked_at,?),
                        revocation_reason=COALESCE(revocation_reason,?), update_time=CURRENT_TIMESTAMP
                        WHERE token_family_id=? AND revoked_at IS NULL
                        """, invocation.getArgument(2), invocation.getArgument(1), invocation.getArgument(0)));
        when(mapper.lockUserSessions(anyLong())).thenAnswer(invocation ->
                jdbc.queryForList("SELECT id FROM refresh_token WHERE user_id=? ORDER BY id FOR UPDATE",
                        Long.class, invocation.getArgument(0)));
        when(mapper.revokeAllForUser(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> jdbc.update("""
                        UPDATE refresh_token SET revoked_at=?,revocation_reason=?,update_time=CURRENT_TIMESTAMP
                        WHERE user_id=? AND revoked_at IS NULL
                        """, invocation.getArgument(2), invocation.getArgument(1), invocation.getArgument(0)));
        when(mapper.countActiveFamily(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> jdbc.queryForObject("""
                        SELECT COUNT(*) FROM refresh_token
                        WHERE user_id=? AND token_family_id=? AND revoked_at IS NULL AND expire_time>?
                        """, Long.class, invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        return mapper;
    }

    private RefreshToken mapToken(java.sql.ResultSet rs) throws java.sql.SQLException {
        return RefreshToken.builder()
                .id(rs.getLong("id"))
                .userId(rs.getLong("user_id"))
                .token(rs.getString("token"))
                .tokenHash(rs.getString("token_hash"))
                .tokenFamilyId(rs.getString("token_family_id"))
                .issuedAt(localDateTime(rs.getTimestamp("issued_at")))
                .expireTime(localDateTime(rs.getTimestamp("expire_time")))
                .revokedAt(localDateTime(rs.getTimestamp("revoked_at")))
                .revocationReason(rs.getString("revocation_reason"))
                .replacedBy(rs.getString("replaced_by"))
                .lastUsedIp(rs.getString("last_used_ip"))
                .deviceInfo(rs.getString("device_info"))
                .build();
    }

    private LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
