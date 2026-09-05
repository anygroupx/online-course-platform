package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.http.*;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real MyBatis SQL, persisted ciphertext and concurrent requests, not a mocked CAS return value. */
class ProviderVerificationConcurrencyTest {
    private ApiProviderMapper mapper;
    private ApiProviderServiceImpl service;
    private PlatformDockingStrategy strategy;
    private ExecutorService worker;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:provider_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        jdbc = new JdbcTemplate(ds);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        String schema = Files.readString(root.resolve("database/schema.sql"));
        int start = schema.indexOf("CREATE TABLE `api_provider`");
        int end = schema.indexOf(") ENGINE=", start);
        jdbc.execute(schema.substring(start, end) + ")");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ApiProviderMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ApiProviderMapper.class);
        strategy = mock(PlatformDockingStrategy.class);
        PlatformDockingStrategyFactory strategies = mock(PlatformDockingStrategyFactory.class);
        when(strategies.getStrategy("Daytime")).thenReturn(strategy);
        var normalizer = new ProviderUrlNormalizer();
        service = new ApiProviderServiceImpl(mapper, mock(SsrfGuard.class),
                new ProviderOutboundPolicyFactory(new OutboundSecurityProperties(), normalizer), normalizer, strategies);
        ReflectionTestUtils.setField(service, "cryptoSecret", "test-master-key");
        worker = Executors.newSingleThreadExecutor();
        ApiProvider provider = new ApiProvider();
        provider.setName("Daytime test");
        provider.setProviderType("Daytime");
        provider.setApiUrl("https://old.example/api.php/");
        provider.setUsername("test-user");
        provider.setApiKey("runtime-secret-key");
        assertEquals(1L, service.createApiProvider(provider));
    }

    @AfterEach
    void cleanup() {
        worker.shutdownNow();
    }

    @Test
    void successfulTestThenEnablePersistsOnlySafeMetadataAndCiphertext() {
        assertEquals(ApiProvider.STATUS_PENDING, mapper.selectById(1L).getStatus());
        assertThrows(BusinessException.class, () -> service.updateStatus(1L, ApiProvider.STATUS_ACTIVE));
        service.testConnection(1L, 7L);
        ApiProvider verified = mapper.selectById(1L);
        assertEquals(ApiProvider.STATUS_PENDING, verified.getStatus());
        assertEquals("https://old.example", verified.getApiUrl());
        assertNotNull(verified.getVerifiedAt());
        assertEquals(7L, verified.getVerifiedBy());
        assertEquals("SUCCESS", verified.getLastCheckReason());
        assertTrue(SecretCrypto.isEncrypted(verified.getApiKey()));
        service.updateStatus(1L, ApiProvider.STATUS_ACTIVE);
        assertEquals(ApiProvider.STATUS_ACTIVE, mapper.selectById(1L).getStatus());
        assertEquals("runtime-secret-key", service.loadDecrypted(1L).getApiKey());
    }

    @Test
    void editingDestinationWhileProbeRunsCannotVerifyOrEnableNewDestination() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            ApiProvider tested = call.getArgument(0);
            assertEquals("https://old.example", tested.getApiUrl());
            assertEquals("runtime-secret-key", tested.getApiKey());
            started.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return null;
        }).when(strategy).testConnection(any());
        Future<?> test = worker.submit(() -> service.testConnection(1L, 7L));
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            ApiProvider edit = new ApiProvider();
            edit.setId(1L);
            edit.setApiUrl("https://new.example/openapi");
            edit.setStatus(ApiProvider.STATUS_ACTIVE);
            service.updateApiProvider(edit);
        } finally {
            release.countDown();
        }
        ExecutionException failure = assertThrows(ExecutionException.class, () -> test.get(5, TimeUnit.SECONDS));
        assertInstanceOf(BusinessException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("配置已变化"));
        ApiProvider stored = mapper.selectById(1L);
        assertEquals("https://new.example/openapi", stored.getApiUrl());
        assertEquals(ApiProvider.STATUS_PENDING, stored.getStatus());
        assertEquals(1L, stored.getConfigVersion());
        assertNull(stored.getVerifiedAt());
        assertNull(stored.getVerifiedBy());
        assertNull(stored.getLastCheckReason());
        assertTrue(SecretCrypto.isEncrypted(stored.getApiKey()));
        assertThrows(BusinessException.class, () -> service.updateStatus(1L, ApiProvider.STATUS_ACTIVE));
    }

    @Test
    void disablingWhileProbeRunsCannotBeUndoneByItsCompletion() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return null;
        }).when(strategy).testConnection(any());
        Future<?> test = worker.submit(() -> service.testConnection(1L, 7L));
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            service.updateStatus(1L, ApiProvider.STATUS_DISABLED);
        } finally {
            release.countDown();
        }
        assertThrows(ExecutionException.class, () -> test.get(5, TimeUnit.SECONDS));
        assertEquals(ApiProvider.STATUS_DISABLED, mapper.selectById(1L).getStatus());
        assertNull(mapper.selectById(1L).getVerifiedAt());
    }

    @Test
    void nameOnlyEditDoesNotClearVerificationButChangedCredentialsDo() {
        service.testConnection(1L, 7L);
        service.updateStatus(1L, ApiProvider.STATUS_ACTIVE);
        ApiProvider rename = new ApiProvider();
        rename.setId(1L);
        rename.setName("Renamed");
        service.updateApiProvider(rename);
        LocalDateTime verifiedAt = mapper.selectById(1L).getVerifiedAt();
        assertNotNull(verifiedAt);
        assertEquals(ApiProvider.STATUS_ACTIVE, mapper.selectById(1L).getStatus());

        ApiProvider changeCredentials = new ApiProvider();
        changeCredentials.setId(1L);
        changeCredentials.setApiKey("different-key");
        changeCredentials.setStatus(ApiProvider.STATUS_ACTIVE);
        service.updateApiProvider(changeCredentials);
        ApiProvider stored = mapper.selectById(1L);
        assertNull(stored.getVerifiedAt());
        assertNull(stored.getVerifiedBy());
        assertEquals(ApiProvider.STATUS_PENDING, stored.getStatus());
        assertEquals("different-key", service.loadDecrypted(1L).getApiKey());
        assertTrue(SecretCrypto.isEncrypted(stored.getApiKey()));
    }

    @Test
    void legacyActiveRecordsAreNotSilentlyApprovedOrDisabledByMigrationCompatibleReads() {
        jdbc.update("UPDATE api_provider SET status=1, verified_at=NULL, verified_by=NULL WHERE id=1");
        assertEquals(ApiProvider.STATUS_ACTIVE, mapper.selectById(1L).getStatus());
        ApiProvider rename = new ApiProvider();
        rename.setId(1L);
        rename.setName("Legacy renamed");
        service.updateApiProvider(rename);
        assertEquals(ApiProvider.STATUS_ACTIVE, mapper.selectById(1L).getStatus());
        assertNull(mapper.selectById(1L).getVerifiedAt());
    }
}
