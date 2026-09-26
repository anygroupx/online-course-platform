package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HeishaFulfillmentMigrationTest {
    @Test
    void migrationPreservesHistoryAndGrantsOnlyOperationalRoles() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE service_product(id BIGINT PRIMARY KEY, provider_type VARCHAR(30), enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE service_order(id VARCHAR(36) PRIMARY KEY, status VARCHAR(24), update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE service_order_operation(id VARCHAR(36) PRIMARY KEY, resolution_note VARCHAR(1000))");
        jdbc.execute("CREATE TABLE sys_permission(id BIGINT AUTO_INCREMENT PRIMARY KEY, permission_code VARCHAR(100) UNIQUE, permission_name VARCHAR(100), enabled INT DEFAULT 1)");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY, role_code VARCHAR(50))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT, permission_id BIGINT, PRIMARY KEY(role_id,permission_id))");
        jdbc.update("INSERT INTO service_product VALUES(1,'heisha',true)");
        jdbc.update("INSERT INTO service_order VALUES('historical','ACTIVE',CURRENT_TIMESTAMP)");
        var roles = List.of("SUPER_ADMIN", "OPERATOR", "FINANCE", "AUDITOR", "USER");
        for (int i = 0; i < roles.size(); i++) jdbc.update("INSERT INTO sys_role VALUES(?,?)", i + 1, roles.get(i));
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/migrations"))) root = root.getParent();
        String migration = Files.readString(root.resolve("database/migrations/034_heisha_self_operated_fulfillment.sql"))
                .replaceAll("(?m)^--.*$", "").replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String statement : migration.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        assertEquals("UPSTREAM", jdbc.queryForObject("SELECT fulfillment_mode FROM service_product", String.class));
        assertEquals("UPSTREAM", jdbc.queryForObject("SELECT fulfillment_mode FROM service_order", String.class));
        assertEquals(List.of("OPERATOR", "SUPER_ADMIN"), jdbc.queryForList(
                "SELECT r.role_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id ORDER BY r.role_code", String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE service_product SET fulfillment_mode='INVALID'"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO service_order_fulfillment VALUES('missing','encrypted',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
    }
}
