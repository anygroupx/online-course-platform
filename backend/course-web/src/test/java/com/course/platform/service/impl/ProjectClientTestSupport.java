package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

abstract class ProjectClientTestSupport {
    JdbcTemplate jdbc;
    ProjectClientServiceImpl service;
    ProjectApiKeyServiceImpl keys;
    UserMapper users;
    ProjectClientMapper clients;
    ProjectClientOperationMapper operations;
    ProjectApiCredentialMapper credentials;
    RateLimitService limiter;
    ValidatorFactory validation;
    ExecutorService threads;
    SqlSessionTemplate sql;
    static final String PASSWORD = "fixture-current-password";

    @BeforeEach
    void setupClients() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:project_clients_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        String base = Files.readString(root.resolve("database/schema.sql"));
        for (String table : List.of("sys_user", "api_provider")) {
            var m =
                    Pattern.compile("CREATE TABLE `" + table + "` \\(.*?;", Pattern.DOTALL)
                            .matcher(base);
            assertTrue(m.find());
            jdbc.execute(
                    m.group()
                            .replace("`idx_status`", "`" + table + "_idx_status`")
                            .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4.*?;", ";"));
        }
        String ledger =
                Files.readString(root.resolve("database/migrations/007_security_hardening.sql"));
        var m =
                Pattern.compile(
                                "CREATE TABLE IF NOT EXISTS `account_ledger` \\(.*?;",
                                Pattern.DOTALL)
                        .matcher(ledger);
        assertTrue(m.find());
        jdbc.execute(m.group().replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4.*?;", ";"));
        for (String file :
                List.of("021_native_project_wallets.sql", "025_native_project_clients.sql", "026_native_project_client_tickets.sql")) {
            String ddl =
                    Files.readString(root.resolve("database/migrations/" + file))
                            .replaceAll("(?m)^--.*$", "")
                            .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            for (String stmt : ddl.split(";")) if (!stmt.isBlank()) jdbc.execute(stmt);
        }
        var passwords = new BCryptPasswordEncoder(4);
        String encoded = passwords.encode(PASSWORD);
        jdbc.update(
                "INSERT INTO"
                    + " sys_user(id,uid,username,password,balance,total_recharge,status,must_change_password)"
                    + " VALUES(7,?,'fixture-7',?,100,100,1,0),(8,?,'fixture-8',?,100,100,1,0)",
                UUID.randomUUID().toString(),
                encoded,
                UUID.randomUUID().toString(),
                encoded);
        jdbc.update(
                "INSERT INTO api_provider(id,provider_type,name,status,config_version)"
                        + " VALUES(9,'syyv5','unused supplier',1,2)");
        jdbc.update(
                "INSERT INTO"
                    + " service_project(id,provider_id,remote_project_id,title,base_price,unit_price,unit_cost,valid_until,price_evidence,reviewed_by,reviewed_at,provider_identity,enabled,version,create_time,update_time)"
                    + " VALUES(1,9,'5','fixture project',1,0.25,0.10,'2099-12-31','fixture only"
                    + " reviewed"
                    + " cost',7,CURRENT_TIMESTAMP,?,TRUE,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "a".repeat(64));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (var type :
                List.of(
                        UserMapper.class,
                        AccountLedgerMapper.class,
                        ServiceProjectMapper.class,
                        ProjectClientMapper.class,
                        ProjectClientOperationMapper.class,
                        ProjectApiCredentialMapper.class,
                        ProjectClientTicketMapper.class,
                        ProjectClientTicketReplyMapper.class,
                        ProjectClientTicketCommandMapper.class,
                        ProjectApiCallMapper.class)) config.addMapper(type);
        var paging = new MybatisPlusInterceptor();
        paging.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(paging);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        sql = new SqlSessionTemplate(factory.getObject());
        users = sql.getMapper(UserMapper.class);
        clients = sql.getMapper(ProjectClientMapper.class);
        operations = sql.getMapper(ProjectClientOperationMapper.class);
        credentials = sql.getMapper(ProjectApiCredentialMapper.class);
        validation = Validation.buildDefaultValidatorFactory();
        limiter = mock(RateLimitService.class);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        var limits = new RateLimitProperties();
        var tx = new DataSourceTransactionManager(ds);
        keys =
                new ProjectApiKeyServiceImpl(
                        mock(SecurityAuditService.class),
                        users,
                        clients,
                        credentials,
                        sql.getMapper(ProjectApiCallMapper.class),
                        validation.getValidator(),
                        passwords,
                        limiter,
                        limits,
                        tx);
        service =
                new ProjectClientServiceImpl(
                        clients,
                        operations,
                        credentials,
                        sql.getMapper(ServiceProjectMapper.class),
                        users,
                        new AccountLedgerServiceImpl(
                                users, sql.getMapper(AccountLedgerMapper.class)),
                        keys,
                        validation.getValidator(),
                        limiter,
                        limits,
                        tx);
        ReflectionTestUtils.setField(keys, "enabled", true);
        ReflectionTestUtils.setField(service, "enabled", true);
        threads = Executors.newFixedThreadPool(2);
        auth(7);
    }

    @AfterEach
    void cleanupClients() {
        SecurityContextHolder.clearContext();
        if (validation != null) validation.close();
        if (threads != null) threads.shutdownNow();
    }

    void auth(long uid) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                uid, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    QuoteForm opening(String amount) {
        return new QuoteForm(
                UUID.randomUUID().toString(),
                "OPEN",
                1L,
                null,
                "Demo customer",
                new BigDecimal(amount),
                true);
    }

    QuoteForm adjust(String id, String action, String amount) {
        return new QuoteForm(
                UUID.randomUUID().toString(), action, 1L, id, null, new BigDecimal(amount), true);
    }

    OperationView quote(QuoteForm form) {
        return service.quote(keys.web(), form);
    }

    OperationView confirm(String id) {
        return service.confirm(keys.web(), id, new ConfirmForm(true));
    }

    ClientView open(String amount) {
        var op = quote(opening(amount));
        confirm(op.id());
        return service.client(keys.web(), op.clientId());
    }

    void wallet(String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(users.selectById(7L).getBalance()));
    }

    long ledgerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Long.class);
    }

    IssuedKey issue(String subject, String access) {
        var state = keys.settings(subject);
        return keys.issue(subject, new KeyForm(state.version(), PASSWORD, access, 30, true));
    }
}
