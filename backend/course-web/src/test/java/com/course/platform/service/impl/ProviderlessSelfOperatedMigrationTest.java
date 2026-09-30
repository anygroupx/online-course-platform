package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ProviderlessSelfOperatedMigrationTest {
    @Test
    void migrationEnforcesProviderModesAssetsVerificationAndBiometricRoles() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        schema(jdbc);
        jdbc.update("INSERT INTO api_provider VALUES(9)");
        jdbc.update("INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(1,9,'heisha','default','1','UPSTREAM')");
        jdbc.update("INSERT INTO service_order(id,product_id,provider_id,provider_version,provider_identity,provider_type,fulfillment_mode) VALUES('history',1,9,3,REPEAT('a',64),'heisha','UPSTREAM')");
        jdbc.update("INSERT INTO service_order_fulfillment VALUES('history','encrypted',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int i = 0; i < 5; i++) jdbc.update("INSERT INTO sys_role VALUES(?,?)", i + 1,
                List.of("SUPER_ADMIN", "OPERATOR", "FINANCE", "AUDITOR", "USER").get(i));
        jdbc.update("INSERT INTO sys_permission(id,permission_code,permission_name) VALUES(1,'service-order:biometric','old')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) VALUES(3,1),(4,1),(5,1)");

        executeMigration(jdbc);

        assertEquals("UPSTREAM", jdbc.queryForObject(
                "SELECT fulfillment_mode FROM service_product WHERE id=1", String.class));
        assertEquals(9L, jdbc.queryForObject(
                "SELECT provider_id FROM service_order WHERE id='history'", Long.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(2,NULL,'heisha','default','2','UPSTREAM')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(3,9,'heisha','default','2','SELF_OPERATED')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(4,NULL,'flash','default','2','SELF_OPERATED')"));
        for (int sku = 1; sku <= 4; sku++) jdbc.update(
                "INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(?,NULL,'heisha','default',?,'SELF_OPERATED')",
                10 + sku, Integer.toString(sku));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(20,NULL,'heisha','default','1','SELF_OPERATED')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO service_order_fulfillment_asset(id,order_id,asset_type,mime_type,width,height,byte_size,sha256,version,create_time) VALUES('asset','missing','FACE_QUALIFICATION','image/png',1,1,1,REPEAT('b',64),0,CURRENT_TIMESTAMP)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE service_order_fulfillment SET verification_status='INVALID' WHERE order_id='history'"));
        assertEquals(List.of("OPERATOR", "SUPER_ADMIN"), jdbc.queryForList(
                "SELECT r.role_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='service-order:biometric' ORDER BY r.role_code",
                String.class));
    }

    @Test
    void migrationDetachesLegacyLocalBindingsButPreservesHistoricalUpstreamSnapshots() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        schema(jdbc);
        jdbc.update("INSERT INTO api_provider VALUES(9)");
        jdbc.update("INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode,version) VALUES(1,9,'heisha','default','1','SELF_OPERATED',4),(2,9,'heisha','default','2','UPSTREAM',7)");
        // The order snapshot, not the product's current mode, determines migration semantics.
        jdbc.update("INSERT INTO service_order(id,product_id,provider_id,provider_version,provider_identity,provider_type,fulfillment_mode) VALUES('remote-history',1,9,3,REPEAT('a',64),'heisha','UPSTREAM'),('local-history',2,9,5,REPEAT('b',64),'heisha','SELF_OPERATED')");
        jdbc.update("INSERT INTO service_order_operation(id,order_id,provider_version,action) VALUES('remote-op','remote-history',3,'CREATE'),('local-op','local-history',5,'LOCAL_START')");
        executeMigration(jdbc);
        assertNull(jdbc.queryForObject("SELECT provider_id FROM service_product WHERE id=1", Long.class));
        assertEquals(5L, jdbc.queryForObject("SELECT version FROM service_product WHERE id=1", Long.class));
        assertEquals(9L, jdbc.queryForObject("SELECT provider_id FROM service_product WHERE id=2", Long.class));
        assertEquals(7L, jdbc.queryForObject("SELECT version FROM service_product WHERE id=2", Long.class));
        assertEquals(9L, jdbc.queryForObject("SELECT provider_id FROM service_order WHERE id='remote-history'", Long.class));
        assertEquals(3L, jdbc.queryForObject("SELECT provider_version FROM service_order WHERE id='remote-history'", Long.class));
        assertEquals("a".repeat(64), jdbc.queryForObject("SELECT provider_identity FROM service_order WHERE id='remote-history'", String.class));
        for (String column : List.of("provider_id", "provider_version", "provider_identity"))
            assertNull(jdbc.queryForObject("SELECT " + column + " FROM service_order WHERE id='local-history'", Object.class));
        assertEquals(3L, jdbc.queryForObject("SELECT provider_version FROM service_order_operation WHERE id='remote-op'", Long.class));
        assertNull(jdbc.queryForObject("SELECT provider_version FROM service_order_operation WHERE id='local-op'", Long.class));
    }

    @Test
    void duplicateLegacyLocalSkusFailBeforeAnyBindingIsDetached() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        schema(jdbc);
        jdbc.update("INSERT INTO api_provider VALUES(9),(10)");
        jdbc.update("INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,fulfillment_mode) VALUES(1,9,'heisha','default','1','SELF_OPERATED'),(2,10,'heisha','default','1','SELF_OPERATED')");
        assertThrows(DataIntegrityViolationException.class, () -> executeMigration(jdbc));
        assertEquals(List.of(9L, 10L), jdbc.queryForList("SELECT provider_id FROM service_product ORDER BY id", Long.class));
    }

    private static void schema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE api_provider(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE service_product(id BIGINT PRIMARY KEY,provider_id BIGINT NOT NULL,provider_type VARCHAR(20) NOT NULL,project VARCHAR(16) NOT NULL,remote_product_id VARCHAR(64) NOT NULL,fulfillment_mode VARCHAR(20) NOT NULL,version BIGINT NOT NULL DEFAULT 0,CONSTRAINT fk_service_product_provider FOREIGN KEY(provider_id) REFERENCES api_provider(id),CONSTRAINT uk_service_product_binding UNIQUE(provider_id,project,remote_product_id))");
        jdbc.execute("CREATE TABLE service_order(id VARCHAR(36) PRIMARY KEY,product_id BIGINT NOT NULL,provider_id BIGINT NOT NULL,provider_version BIGINT NOT NULL,provider_identity CHAR(64) NOT NULL,provider_type VARCHAR(20) NOT NULL,fulfillment_mode VARCHAR(20) NOT NULL,CONSTRAINT fk_service_order_product FOREIGN KEY(product_id) REFERENCES service_product(id))");
        jdbc.execute("CREATE TABLE service_order_operation(id VARCHAR(36) PRIMARY KEY,order_id VARCHAR(36),provider_version BIGINT NOT NULL,action VARCHAR(20) NOT NULL,completed_snapshot INT)");
        jdbc.execute("CREATE TABLE service_order_fulfillment(order_id VARCHAR(36) PRIMARY KEY,payload_encrypted CLOB NOT NULL,version BIGINT NOT NULL DEFAULT 0,create_time TIMESTAMP NOT NULL,update_time TIMESTAMP NOT NULL,CONSTRAINT fk_service_fulfillment_order FOREIGN KEY(order_id) REFERENCES service_order(id))");
        jdbc.execute("CREATE TABLE sys_permission(id BIGINT AUTO_INCREMENT PRIMARY KEY,permission_code VARCHAR(100) UNIQUE,permission_name VARCHAR(100),enabled INT DEFAULT 1)");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(50))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT,PRIMARY KEY(role_id,permission_id))");
    }

    static void executeMigration(JdbcTemplate jdbc) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/migrations"))) root = root.getParent();
        String sql = Files.readString(root.resolve("database/migrations/035_providerless_self_operated_checkout.sql"))
                .replaceAll("(?m)^--.*$", "")
                .replace("DROP FOREIGN KEY", "DROP CONSTRAINT")
                .replace("MODIFY COLUMN provider_id BIGINT NULL", "ALTER COLUMN provider_id BIGINT NULL")
                .replace("MODIFY COLUMN provider_version BIGINT NULL", "ALTER COLUMN provider_version BIGINT NULL")
                .replace("MODIFY COLUMN provider_identity CHAR(64) NULL", "ALTER COLUMN provider_identity CHAR(64) NULL")
                .replace(
                        "ADD CONSTRAINT fk_service_product_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id),\n"
                                + "  ADD CONSTRAINT chk_service_product_provider_mode",
                        "ADD CONSTRAINT fk_service_product_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id);\n"
                                + "ALTER TABLE service_product ADD CONSTRAINT chk_service_product_provider_mode")
                .replace(
                        "ALTER TABLE service_order_operation\n"
                                + "  ADD COLUMN previous_verification_status VARCHAR(20) NULL AFTER completed_snapshot,\n"
                                + "  ADD COLUMN resulting_verification_status VARCHAR(20) NULL AFTER previous_verification_status,\n"
                                + "  ADD CONSTRAINT chk_service_operation_verification",
                        "ALTER TABLE service_order_operation ADD COLUMN previous_verification_status VARCHAR(20) NULL;\n"
                                + "ALTER TABLE service_order_operation ADD COLUMN resulting_verification_status VARCHAR(20) NULL;\n"
                                + "ALTER TABLE service_order_operation ADD CONSTRAINT chk_service_operation_verification")
                .replace(
                        "ALTER TABLE service_order_fulfillment\n"
                                + "  ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER payload_encrypted,\n"
                                + "  ADD COLUMN verified_by BIGINT NULL AFTER verification_status,\n"
                                + "  ADD COLUMN verified_at DATETIME NULL AFTER verified_by,\n"
                                + "  ADD COLUMN verification_note VARCHAR(1000) NULL AFTER verified_at,\n"
                                + "  ADD COLUMN material_version BIGINT NOT NULL DEFAULT 0 AFTER verification_note,\n"
                                + "  ADD CONSTRAINT chk_service_fulfillment_verification",
                        "ALTER TABLE service_order_fulfillment ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';\n"
                                + "ALTER TABLE service_order_fulfillment ADD COLUMN verified_by BIGINT NULL;\n"
                                + "ALTER TABLE service_order_fulfillment ADD COLUMN verified_at DATETIME NULL;\n"
                                + "ALTER TABLE service_order_fulfillment ADD COLUMN verification_note VARCHAR(1000) NULL;\n"
                                + "ALTER TABLE service_order_fulfillment ADD COLUMN material_version BIGINT NOT NULL DEFAULT 0;\n"
                                + "ALTER TABLE service_order_fulfillment ADD CONSTRAINT chk_service_fulfillment_verification")
                .replace(" GENERATED ALWAYS AS ", " AS ")
                .replace(") STORED", ")")
                .replace("MEDIUMTEXT", "CLOB")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "")
                .replace("KEY idx_service_material_draft_expiry (state, expires_at),", "")
                .replace("KEY idx_service_material_draft_owner (user_id, product_id, state)", "")
                .replace("KEY idx_service_fulfillment_asset_purge (purged_at, create_time)", "")
                .replaceAll(",\\s*\\)", "\n)")
                .replace("INSERT IGNORE", "INSERT")
                .replace("ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1", "");
        for (String statement : sql.split(";")) {
            if (statement.isBlank()) continue;
            if (statement.stripLeading().startsWith("INSERT INTO sys_permission")) continue;
            jdbc.execute(statement);
        }
    }
}
