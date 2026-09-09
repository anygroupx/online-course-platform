package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.projectcenter.ProjectCenterGateway;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.projectcenter.*;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;

import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

abstract class ProjectCenterTestSupport {
    ProjectCenterServiceImpl service;
    ProjectCenterGateway gateway;
    ApiProvider provider;
    ServiceProjectMapper projects;
    ServiceProjectAccountMapper accounts;
    ServiceProjectOperationMapper operations;
    JdbcTemplate jdbc;
    DriverManagerDataSource ds;
    SqlSessionTemplate sql;
    RateLimitService limiter;
    ExecutorService threads;
    Long projectId;
    BigDecimal upstream = BigDecimal.ZERO;
    AtomicInteger createCalls;

    @BeforeEach
    void setup() throws Exception {
        ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:project_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE api_provider(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO api_provider VALUES(9)");
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        String migration =
                Files.readString(root.resolve("database/migrations/021_native_project_wallets.sql"))
                        .replaceAll("(?m)^--.*$", "")
                        .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String statement : migration.split(";"))
            if (!statement.isBlank()) jdbc.execute(statement);
        String ticketMigration =
                Files.readString(root.resolve("database/migrations/022_native_project_tickets.sql"))
                        .replaceAll("(?m)^--.*$", "")
                        .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String statement : ticketMigration.split(";"))
            if (!statement.isBlank()) jdbc.execute(statement);
        jdbc.execute(
                "CREATE TABLE sys_user(id BIGINT PRIMARY KEY,balance DECIMAL(14,2),total_recharge"
                        + " DECIMAL(14,2),update_time TIMESTAMP)");
        jdbc.update(
                "INSERT INTO sys_user"
                        + " VALUES(7,100,0,CURRENT_TIMESTAMP),(8,100,0,CURRENT_TIMESTAMP)");
        jdbc.execute(
                "CREATE TABLE account_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id"
                        + " BIGINT,biz_type VARCHAR(32),biz_no VARCHAR(64),direction INT,amount"
                        + " DECIMAL(14,2),balance_before DECIMAL(14,2),balance_after"
                        + " DECIMAL(14,2),remark VARCHAR(255),create_time"
                        + " TIMESTAMP,UNIQUE(user_id,biz_type,biz_no,direction))");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper :
                List.of(
                        ServiceProjectMapper.class,
                        ServiceProjectAccountMapper.class,
                        ServiceProjectOperationMapper.class,
                        ServiceProjectTicketMapper.class,
                        ServiceProjectTicketOperationMapper.class,
                        UserMapper.class,
                        AccountLedgerMapper.class)) config.addMapper(mapper);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(interceptor);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        sql = new SqlSessionTemplate(factory.getObject());
        projects = sql.getMapper(ServiceProjectMapper.class);
        accounts = sql.getMapper(ServiceProjectAccountMapper.class);
        operations = sql.getMapper(ServiceProjectOperationMapper.class);
        var providers = mock(ApiProviderService.class);
        provider = new ApiProvider();
        provider.setId(9L);
        provider.setProviderType("syyv5");
        provider.setStatus(1);
        provider.setVerifiedAt(ServiceTime.now());
        provider.setConfigVersion(2L);
        provider.setApiUrl("https://supplier.example/openapi.php");
        provider.setApiKey("private-main-key");
        when(providers.loadDecrypted(9L)).thenReturn(provider);
        gateway = mock(ProjectCenterGateway.class);
        when(gateway.projects(any()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            return List.of(new CatalogItem("5", "项目甲", new BigDecimal("0.10")));
                        });
        createCalls = new AtomicInteger();
        when(gateway.provision(any(), anyString()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            createCalls.incrementAndGet();
                            assertEquals(
                                    "BUSY",
                                    jdbc.queryForObject(
                                            "SELECT state FROM service_project_account WHERE"
                                                    + " user_id=7",
                                            String.class));
                            return receipt("11");
                        });
        when(gateway.customer(any(), anyString(), anyString()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            return receipt(a.getArgument(2));
                        });
        when(gateway.adjust(any(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(
                        a -> {
                            noTransaction();
                            upstream = upstream.add(a.getArgument(3));
                            return new AdjustmentReceipt(upstream, new BigDecimal("0.10"));
                        });
        limiter = mock(RateLimitService.class);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        service =
                new ProjectCenterServiceImpl(
                        projects,
                        accounts,
                        operations,
                        providers,
                        gateway,
                        new AccountLedgerServiceImpl(
                                sql.getMapper(UserMapper.class),
                                sql.getMapper(AccountLedgerMapper.class)),
                        limiter,
                        new RateLimitProperties(),
                        new DataSourceTransactionManager(ds));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "cryptoSecret", "test-project-master-key");
        auth(7, "api-provider:update", "payment:reconcile");
        projectId = service.save(null, form("0.25", true, null)).id();
        auth(7, "ROLE_USER");
        threads = Executors.newFixedThreadPool(2);
        clearInvocations(gateway);
    }

    @AfterEach
    void cleanup() {
        threads.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    void auth(long id, String... authorities) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                id,
                                null,
                                Arrays.stream(authorities)
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()));
    }

    Long aliasProject() {
        jdbc.update("INSERT INTO api_provider VALUES(10)");
        var alias = new ApiProvider();
        org.springframework.beans.BeanUtils.copyProperties(provider, alias);
        alias.setId(10L);
        var providerService =
                (ApiProviderService) ReflectionTestUtils.getField(service, "providers");
        when(providerService.loadDecrypted(10L)).thenReturn(alias);
        auth(7, "api-provider:update");
        var original = form("0.25", true, null);
        return service.save(
                        null,
                        new ProjectForm(
                                10L,
                                "5",
                                "同一来源的另一接口配置",
                                "隔离验证",
                                original.unitPrice(),
                                original.unitCost(),
                                original.validUntil(),
                                original.evidence(),
                                true,
                                true,
                                null))
                .id();
    }

    void noTransaction() {
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive(),
                "Supplier calls must not hold database locks");
    }

    CustomerReceipt receipt(String id) {
        return new CustomerReceipt(id, "5", "private-customer-key", upstream, true);
    }

    ProjectForm form(String rate, boolean enabled, Long version) {
        return new ProjectForm(
                9L,
                "5",
                "项目甲",
                "服务说明",
                new BigDecimal(rate),
                new BigDecimal("0.10"),
                LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(20),
                "测试合同确认：已逐项核实单价",
                true,
                enabled,
                version);
    }

    OperationView quote(String action, String units) {
        return service.quote(
                projectId,
                new QuoteForm(action, units == null ? null : new BigDecimal(units), true));
    }

    String open() {
        var op = service.confirm(quote("PROVISION", null).id());
        assertEquals("SUCCEEDED", op.state());
        return op.accountId();
    }

    OperationView topup(String units) {
        return service.confirm(quote("TOP_UP", units).id());
    }

    void money(String expected) {
        assertEquals(
                0,
                new BigDecimal(expected)
                        .compareTo(
                                jdbc.queryForObject(
                                        "SELECT balance FROM sys_user WHERE id=7",
                                        BigDecimal.class)));
    }
}
