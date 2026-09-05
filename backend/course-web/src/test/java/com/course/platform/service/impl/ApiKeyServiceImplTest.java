package com.course.platform.service.impl;

import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiKeyServiceImplTest {
    private UserMapper users;
    private OperationLogService operations;
    private AccountLedgerServiceImpl ledger;
    private SecurityAuditService audit;
    private PasswordEncoder passwords;
    private ApiKeyServiceImpl service;
    private User user;
    @BeforeEach void setUp() {
        users = mock(UserMapper.class); operations = mock(OperationLogService.class);
        ledger = mock(AccountLedgerServiceImpl.class); audit = mock(SecurityAuditService.class);
        passwords = mock(PasswordEncoder.class);
        service = new ApiKeyServiceImpl(users, operations, ledger, audit, passwords);
        ReflectionTestUtils.setField(service, "apiEnableFreeThreshold", new BigDecimal("300"));
        ReflectionTestUtils.setField(service, "apiEnableFee", new BigDecimal("10"));
        user = new User(); user.setId(7L); user.setUsername("test"); user.setStatus(1);
        user.setBalance(new BigDecimal("500")); user.setPassword("password-hash");
        user.setApiKeyHash(TokenHashUtil.sha256("old-key")); user.setApiKeyScopes("balance:read");
        when(users.selectByIdForUpdate(7L)).thenReturn(user);
        when(users.selectById(7L)).thenReturn(user);
        when(passwords.matches("correct", "password-hash")).thenReturn(true);
        when(users.updateApiKey(anyLong(), anyString(), anyString(), nullable(String.class), any())).thenReturn(1);
    }
    @Test void rotationStoresOnlyNewHashAndPreservesScopesWithoutCharging() {
        String plain = service.rotateApiKey(7L, "correct");
        assertEquals(48, plain.length());
        ArgumentCaptor<LocalDateTime> expires = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(users).updateApiKey(eq(7L), eq(TokenHashUtil.sha256(plain)), eq(plain.substring(0,8)), eq("balance:read"), expires.capture());
        assertTrue(expires.getValue().isAfter(LocalDateTime.now().plusDays(364)));
        assertNotEquals(user.getApiKeyHash(), TokenHashUtil.sha256(plain));
        verifyNoInteractions(ledger, operations);
        verify(audit).record(eq("KEY_CHANGE"), eq("WARN"), eq(7L), eq("test"), eq("/api-keys/rotate"), eq("POST"), anyString(), isNull());
    }
    @Test void rotationDoesNotRegrantRevokedScopesAndRenewsExpiredKeys() {
        user.setApiKeyScopes(""); user.setApiKeyExpireTime(LocalDateTime.now().minusYears(1));
        service.rotateApiKey(7L, "correct");
        verify(users).updateApiKey(eq(7L), anyString(), anyString(), eq(""), any());
    }
    @Test void wrongPasswordDoesNotChangeKey() {
        assertThrows(BusinessException.class, () -> service.rotateApiKey(7L, "wrong"));
        verify(users, never()).updateApiKey(anyLong(), anyString(), anyString(), any(), any());
        verifyNoInteractions(ledger, audit);
    }
    @Test void nullPasswordAndDisabledAccountCannotRotate() {
        assertThrows(BusinessException.class, () -> service.rotateApiKey(7L, null));
        user.setStatus(0);
        assertThrows(BusinessException.class, () -> service.rotateApiKey(7L, "correct"));
        verify(users, never()).updateApiKey(anyLong(), anyString(), anyString(), any(), any());
    }
    @Test void notEnabledCannotUseRotationToAvoidOpeningFee() {
        user.setApiKeyHash(null);
        assertThrows(BusinessException.class, () -> service.rotateApiKey(7L, "correct"));
        verifyNoInteractions(ledger, audit);
    }
    @Test void existingKeyCannotBeEnabledAndChargedAgain() {
        assertThrows(BusinessException.class, () -> service.enableApiKey(7L, 1, null));
        verifyNoInteractions(ledger, audit);
    }
    @Test void invalidEnableTypeIsBusinessErrorNotNullPointer() {
        assertThrows(BusinessException.class, () -> service.enableApiKey(7L, null, null));
    }
    @Test void freeOpeningStillGeneratesHashAndDefaultScopes() {
        user.setApiKeyHash(null);
        String key = service.enableApiKey(7L, 1, null);
        verify(users).updateApiKey(eq(7L), eq(TokenHashUtil.sha256(key)), eq(key.substring(0,8)), eq("balance:read,orders:read,orders:write,platforms:read"), any());
        verifyNoInteractions(ledger);
    }
    @Test void parallelOpeningChargesAndIssuesExactlyOnce() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:key_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY, balance DECIMAL(12,2), api_key_hash VARCHAR(64))");
        jdbc.update("INSERT INTO sys_user VALUES(7,100,NULL)");
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        when(users.selectByIdForUpdate(7L)).thenAnswer(call -> jdbc.queryForObject("SELECT * FROM sys_user WHERE id=7 FOR UPDATE", (rs, i) -> {
            User account = new User(); account.setId(7L); account.setUsername("test"); account.setBalance(rs.getBigDecimal("balance")); account.setApiKeyHash(rs.getString("api_key_hash")); return account;
        }));
        when(users.selectById(7L)).thenAnswer(call -> users.selectByIdForUpdate(7L));
        doAnswer(call -> { jdbc.update("UPDATE sys_user SET balance=balance-10 WHERE id=7"); return null; })
                .when(ledger).debit(eq(7L), eq(new BigDecimal("10")), anyString(), anyString(), anyString());
        when(users.updateApiKey(eq(7L), anyString(), anyString(), anyString(), any())).thenAnswer(call ->
                jdbc.update("UPDATE sys_user SET api_key_hash=? WHERE id=7", call.getArgument(1, String.class)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> tasks = new ArrayList<>();
            for (int i=0; i<2; i++) tasks.add(pool.submit(() -> { start.await(); try { return tx.execute(status -> service.enableApiKey(7L, 1, null)); } catch (BusinessException e) { return e; } }));
            start.countDown();
            List<Object> results = List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(String.class::isInstance).count());
            assertEquals(1, results.stream().filter(BusinessException.class::isInstance).count());
            assertEquals(0, new BigDecimal("90").compareTo(jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=7", BigDecimal.class)));
            verify(ledger, times(1)).debit(eq(7L), eq(new BigDecimal("10")), anyString(), anyString(), anyString());
        } finally { pool.shutdownNow(); jdbc.execute("DROP ALL OBJECTS"); }
    }
}
