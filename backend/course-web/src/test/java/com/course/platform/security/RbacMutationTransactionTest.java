package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.service.impl.SecurityAuditServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RbacMutationTransactionTest {
    private RbacTestDatabase db;
    private RbacAdministrationService service;
    private static final String FIRST = "10000000-0000-4000-8000-000000000001";
    private static final String SECOND = "10000000-0000-4000-8000-000000000002";

    @BeforeEach void setup() throws Exception {
        db = new RbacTestDatabase();
        for (String uid : List.of(FIRST, SECOND)) {
            db.jdbc.update("INSERT INTO sys_user(uid,username,password) VALUES(?,?,?)", uid, uid, "unused");
        }
        db.jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u CROSS JOIN sys_role r WHERE r.role_code='SUPER_ADMIN'");
        var audit = new SecurityAuditServiceImpl(db.mapper(SecurityAuditLogMapper.class), mock(SecurityAlertNotifier.class));
        var target = new RbacAdministrationService(db.mapper(UserMapper.class), db.mapper(UserAuthorityMapper.class), audit);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(db.dataSource), new AnnotationTransactionAttributeSource()));
        service = (RbacAdministrationService) proxy.getProxy();
        authenticate();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L, null,
                List.of(new SimpleGrantedAuthority("rbac:manage"))));
    }

    @Test void successfulMutationCommitsCompleteAuditAndCompatibilityMirror() {
        service.replaceUserRoles(SECOND, List.of("OPERATOR", "FINANCE"));
        assertEquals(List.of("FINANCE", "OPERATOR"), service.getUserRoles(SECOND));
        assertEquals("FINANCE", db.jdbc.queryForObject("SELECT role FROM sys_user WHERE uid=?", String.class, SECOND));
        String detail = db.jdbc.queryForObject("SELECT detail FROM security_audit_log WHERE event_type='RBAC_ROLE_CHANGED'", String.class);
        assertTrue(detail.contains("previousRoles=[SUPER_ADMIN]"));
        assertTrue(detail.contains("newRoles=[OPERATOR, FINANCE]"));
        assertTrue(detail.contains(SECOND));
    }

    @Test void auditInsertFailureRollsBackRolesAndLegacyMirror() {
        db.jdbc.execute("ALTER TABLE security_audit_log ADD CONSTRAINT reject_rbac CHECK(event_type <> 'RBAC_ROLE_CHANGED')");
        assertThrows(BusinessException.class, () -> service.replaceUserRoles(SECOND, List.of("USER")));
        assertEquals(List.of("SUPER_ADMIN"), service.getUserRoles(SECOND));
        assertEquals("USER", db.jdbc.queryForObject("SELECT role FROM sys_user WHERE uid=?", String.class, SECOND));
        assertEquals(0, db.jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_log", Integer.class));
    }

    @Test void concurrentDemotionsCannotRemoveTheLastSuperAdmin() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var futures = List.of(FIRST, SECOND).stream().map(uid -> executor.submit(() -> {
                authenticate();
                start.await();
                try { service.replaceUserRoles(uid, List.of("USER")); return true; }
                catch (BusinessException expected) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            })).toList();
            start.countDown();
            int successful = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) successful++;
            assertEquals(1, successful);
            assertEquals(1, db.mapper(UserAuthorityMapper.class).countUsersWithRole("SUPER_ADMIN"));
            assertEquals(1, db.jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_log", Integer.class));
        } finally { executor.shutdownNow(); }
    }

    @Test void concurrentReplacementsAreWholeSetsWithAnUnbrokenAuditChain() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(List.of("OPERATOR", "FINANCE"), List.of("AUDITOR", "USER")).stream()
                    .map(roles -> executor.submit(() -> {
                        authenticate();
                        try { return service.replaceUserRoles(SECOND, roles); }
                        finally { SecurityContextHolder.clearContext(); }
                    })).toList();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
            var roles = service.getUserRoles(SECOND);
            assertTrue(roles.equals(List.of("FINANCE", "OPERATOR")) || roles.equals(List.of("AUDITOR", "USER")));
            var logs = db.jdbc.queryForList("SELECT detail FROM security_audit_log ORDER BY id", String.class);
            assertEquals(2, logs.size());
            assertTrue(logs.get(0).contains("previousRoles=[SUPER_ADMIN]"));
            assertFalse(logs.get(1).contains("previousRoles=[SUPER_ADMIN]"));
        } finally { executor.shutdownNow(); }
    }
}
