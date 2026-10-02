package com.course.platform.security;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RbacDomainPermissionMigrationTest {
    @Test
    void staticRolePolicyGrantsOnlyDomainAppropriateCapabilities() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE sys_role(id BIGINT AUTO_INCREMENT PRIMARY KEY,role_code VARCHAR(64))");
        db.execute("CREATE TABLE sys_permission(id BIGINT AUTO_INCREMENT PRIMARY KEY,permission_code VARCHAR(128) UNIQUE,permission_name VARCHAR(128),enabled INT DEFAULT 1)");
        db.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT,UNIQUE(role_id,permission_id))");
        for (var role : List.of("SUPER_ADMIN", "OPERATOR", "FINANCE", "AUDITOR", "CUSTOMER_SERVICE", "USER"))
            db.update("INSERT INTO sys_role(role_code) VALUES(?)", role);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/migrations"))) root = root.getParent();
        String sql = Files.readString(root.resolve("database/migrations/036_rbac_domain_permissions.sql"));
        for (int iteration = 0; iteration < 2; iteration++) {
            for (var statement : sql.replaceAll("(?m)^--.*$", "").split(";"))
                if (!statement.isBlank()) db.execute(statement);
        }
        assertEquals(9, grants(db, "SUPER_ADMIN").size());
        assertEquals(Set.of("service-order:read", "service-order:refund", "service-order:reconcile"), grants(db, "FINANCE"));
        assertEquals(Set.of("service-product:read", "service-order:read", "service-project:read"), grants(db, "AUDITOR"));
        var operator = grants(db, "OPERATOR");
        assertEquals(7, operator.size());
        assertTrue(operator.containsAll(Set.of("service-order:fulfill", "service-order:biometric")));
        assertFalse(operator.contains("service-order:refund"));
        assertFalse(operator.contains("service-order:reconcile"));
        assertTrue(grants(db, "USER").isEmpty());
        assertTrue(grants(db, "CUSTOMER_SERVICE").isEmpty());
    }
    private Set<String> grants(JdbcTemplate db, String role) {
        return new HashSet<>(db.queryForList("SELECT p.permission_code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code=?", String.class, role));
    }
}
