package com.course.platform.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.infra.persistence.mapper.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Isolated SQL fixtures using the shipped schema/policy and real MyBatis mappers, never a live DB. */
final class RbacTestDatabase {
    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final SqlSessionTemplate session;
    final Path root;

    RbacTestDatabase() throws Exception {
        root = repositoryRoot();
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:rbac_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        String schema = Files.readString(root.resolve("database/schema.sql"));
        for (String name : List.of("sys_user", "sys_role", "sys_permission", "sys_user_role", "sys_role_permission"))
            createTable(schema, name);
        String security = Files.readString(root.resolve("database/migrations/008_p2_security.sql"));
        createTable(security, "mfa_challenge");
        createTable(security, "security_audit_log");
        jdbc.execute("""
                CREATE TABLE refresh_token(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,
                token VARCHAR(500),token_hash VARCHAR(128) UNIQUE,token_family_id VARCHAR(64),issued_at TIMESTAMP,
                expire_time TIMESTAMP,revoked_at TIMESTAMP,revocation_reason VARCHAR(64),replaced_by VARCHAR(128),
                last_used_ip VARCHAR(64),device_info VARCHAR(512),create_time TIMESTAMP,update_time TIMESTAMP)
                """);
        // Use the actual seed policy, not hand-written role-to-permission mocks.
        for (String statement : schema.replaceAll("(?m)^--.*$", "").split(";")) {
            String sql = statement.strip();
            if (sql.matches("(?s)INSERT(?: IGNORE)? INTO `?(sys_role|sys_permission|sys_role_permission)`?\\s.*")
                    || sql.startsWith("DELETE FROM sys_role_permission")) jdbc.execute(sql);
        }
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper : List.of(UserMapper.class, UserAuthorityMapper.class, RefreshTokenMapper.class,
                MfaChallengeMapper.class, SecurityAuditLogMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        session = new SqlSessionTemplate(factory.getObject());
    }

    private void createTable(String schema, String name) {
        var matcher = Pattern.compile("(?s)CREATE TABLE(?: IF NOT EXISTS)? `" + name + "` \\(.*?\\) ENGINE=.*?;").matcher(schema);
        if (!matcher.find()) throw new IllegalStateException("Missing table: " + name);
        jdbc.execute(matcher.group().replaceAll("(?s) ENGINE=.*", ""));
    }

    <T> T mapper(Class<T> type) { return session.getMapper(type); }

    static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("database/migrations"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Repository root not found");
        return path;
    }
}
