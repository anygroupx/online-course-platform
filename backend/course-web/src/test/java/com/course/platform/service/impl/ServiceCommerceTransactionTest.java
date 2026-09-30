package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.servicecommerce.NativeServiceGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.config.MyMetaObjectHandler;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.vo.plugin.PluginProduct;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.persistence.mapper.*;

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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

class ServiceCommerceTransactionTest {
    JdbcTemplate jdbc;
    ServiceCommerceServiceImpl service;
    NativeServiceGateway gateway;
    PluginReadOnlyConnector catalog;
    ServiceProductMapper products;
    ServiceOrderMapper orders;
    ServiceOperationMapper operations;
    ServiceAccountSessionMapper accountSessionMapper;
    ApiProvider provider;
    ApiProviderService providerService;
    ApiProviderMapper providerMapper;
    com.course.platform.application.service.servicecommerce.ServiceAccountSessions commerceAccounts;
    ExecutorService threads;

    @BeforeEach
    void setup() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:services_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                        "sa",
                        "");
        ds.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE api_provider(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO api_provider VALUES (9)");
        jdbc.execute("CREATE TABLE sys_permission(id BIGINT AUTO_INCREMENT PRIMARY KEY,permission_code VARCHAR(100) UNIQUE,permission_name VARCHAR(200),enabled INT DEFAULT 1)");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(50))");
        jdbc.execute("CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT,PRIMARY KEY(role_id,permission_id))");
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        for (String name :
                List.of(
                        "018_native_service_orders.sql",
                        "019_internship_service_plans.sql",
                        "020_service_account_sessions.sql",
                        "029_native_service_price_precision.sql",
                        "030_native_service_status_refresh.sql",
                        "034_heisha_self_operated_fulfillment.sql",
                        "035_providerless_self_operated_checkout.sql")) {
            String migration =
                    Files.readString(root.resolve("database/migrations/" + name))
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
                            .replace("KEY idx_service_material_draft_expiry (state, expires_at),", "")
                            .replace("KEY idx_service_material_draft_owner (user_id, product_id, state)", "")
                            .replace("KEY idx_service_fulfillment_asset_purge (purged_at, create_time)", "")
                            .replaceAll(",\\s*\\)", "\n)")
                            .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            for (String statement : migration.split(";"))
                if (!statement.isBlank() && !statement.stripLeading().startsWith("INSERT")) jdbc.execute(statement);
        }
        jdbc.execute(
                "CREATE TABLE sys_user(id BIGINT PRIMARY KEY,balance DECIMAL(14,2),total_recharge"
                        + " DECIMAL(14,2),status INT NOT NULL DEFAULT 1,update_time TIMESTAMP)");
        jdbc.update("INSERT INTO sys_user(id,balance,total_recharge) VALUES (7,100,0),(8,100,0)");
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
                        ServiceProductMapper.class,
                        ServiceOrderMapper.class,
                        ServiceOperationMapper.class,
                        ServiceOrderFulfillmentMapper.class,
                        ServiceFulfillmentMaterialDraftMapper.class,
                        ServiceOrderFulfillmentAssetMapper.class,
                        ServiceAccountSessionMapper.class,
                        UserMapper.class,
                        AccountLedgerMapper.class)) config.addMapper(mapper);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(plugin);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MyMetaObjectHandler()));
        var session = new SqlSessionTemplate(factory.getObject());
        products = session.getMapper(ServiceProductMapper.class);
        orders = session.getMapper(ServiceOrderMapper.class);
        operations = session.getMapper(ServiceOperationMapper.class);
        accountSessionMapper = session.getMapper(ServiceAccountSessionMapper.class);
        providerService = mock(ApiProviderService.class);
        providerMapper = mock(ApiProviderMapper.class);
        provider = new ApiProvider();
        provider.setId(9L);
        provider.setProviderType("jiguang");
        provider.setStatus(1);
        provider.setVerifiedAt(ServiceTime.now());
        provider.setConfigVersion(2L);
        provider.setUsername("account");
        provider.setApiKey("upstream-secret");
        provider.setApiUrl("https://authorized.example");
        when(providerService.loadDecrypted(9L)).thenReturn(provider);
        when(providerMapper.selectById(9L)).thenReturn(provider);
        catalog = mock(PluginReadOnlyConnector.class);
        when(catalog.getProviderType()).thenReturn("jiguang");
        when(catalog.fetchCatalog(any(), any()))
                .thenReturn(List.of(new PluginProduct("1", "晨跑", new BigDecimal("0.10"), "元/公里")));
        gateway = mock(NativeServiceGateway.class);
        when(gateway.prepare(any(), any(), any()))
                .thenAnswer(
                        a -> {
                            OrderForm f = a.getArgument(2);
                            return new PreparedOrder(
                                    Map.of(
                                            "student_account",
                                            "sensitive-student",
                                            "secret",
                                            "sensitive-password"),
                                    f.quantity(),
                                    f.distance(),
                                    f.distance(),
                                    "st***nt");
                        });
        when(gateway.execute(any(), any(), any(), eq("CREATE"), any()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "remote request must not hold DB transaction/locks");
                            return new RemoteResult("UP-" + UUID.randomUUID(), "ACTIVE", 0, null);
                        });
        var ledger =
                new AccountLedgerServiceImpl(
                        session.getMapper(UserMapper.class),
                        session.getMapper(AccountLedgerMapper.class));
        service =
                new ServiceCommerceServiceImpl(
                        products,
                        orders,
                        operations,
                        session.getMapper(ServiceOrderFulfillmentMapper.class),
                        session.getMapper(ServiceFulfillmentMaterialDraftMapper.class),
                        session.getMapper(ServiceOrderFulfillmentAssetMapper.class),
                        providerMapper,
                        providerService,
                        new PluginConnectorRegistry(List.of(catalog)),
                        gateway,
                        commerceAccounts = mock(
                                com.course.platform.application.service.servicecommerce
                                        .ServiceAccountSessions.class),
                        new SelfOperatedCheckoutPreparer(),
                        new com.course.platform.infra.projectclient.SafeRasterCodec(),
                        ledger,
                        new DataSourceTransactionManager(ds),
                        mock(com.course.platform.application.service.security.SecurityAuditService.class));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "cryptoSecret", "test-service-master-key");
        auth(7, "api-provider:update", "payment:reconcile");
        service.saveProduct(
                null,
                new ProductCommand(
                        9L, "default", "1", "极光晨跑", "服务说明", new BigDecimal("0.25"), true, null));
        auth(7, "ROLE_USER");
        threads = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        try {
            if (threads != null) {
                threads.shutdownNow();
                try {
                    if (!threads.awaitTermination(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("Fixture workers did not terminate");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while closing fixture workers", interrupted);
                }
            }
        } finally {
            // DB_CLOSE_DELAY=-1 keeps every per-test database alive until explicitly shut down.
            // Releasing it prevents later large-payload tests from exhausting the shared JVM heap.
            if (jdbc != null) jdbc.execute("SHUTDOWN");
        }
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

    OrderForm form() {
        return new OrderForm(
                10,
                new BigDecimal("2"),
                Map.of("studentAccount", "account", "studentName", "姓名", "schoolName", "大学"),
                List.of(),
                true);
    }

    OrderForm localForm() {
        return new OrderForm(
                10,
                new BigDecimal("2"),
                Map.of(
                        "phone", "13800138000",
                        "password", "local-password-secret",
                        "run_time", "08:00",
                        "school_name", "测试大学"),
                List.of(),
                true,
                null,
                null,
                false,
                null);
    }

    String pngDataUrl() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(image, "png", bytes));
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    QuoteView quote() {
        return service.quote(1L, form());
    }

    QuoteView create() {
        return service.confirm(quote().id());
    }

    BigDecimal balance() {
        return jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=7", BigDecimal.class);
    }

    void money(String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(balance()));
    }

    @Test
    void productBindingIsImmutable() {
        auth(7, "api-provider:update");
        assertThrows(
                BusinessException.class,
                () ->
                        service.saveProduct(
                                1L,
                                new ProductCommand(
                                        9L,
                                        "default",
                                        "2",
                                        "修改",
                                        "",
                                        new BigDecimal("0.25"),
                                        true,
                                        0L)));
        assertEquals("1", products.selectById(1).getRemoteProductId());
    }

    @Test
    void previewEncryptsParametersAndDoesNotDebitUntilExplicitConfirmation() {
        var q = quote();
        money("100");
        assertEquals("5.00", q.amount());
        assertNull(q.orderId());
        var op = operations.selectById(q.id());
        assertTrue(op.getPayloadEncrypted().startsWith("ENC:v1:"));
        assertFalse(op.toString().contains("sensitive"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        var result = service.confirm(q.id());
        assertEquals("SUCCEEDED", result.state());
        money("95");
        assertNull(operations.selectById(q.id()).getPayloadEncrypted());
        assertEquals("ACTIVE", service.order(result.orderId()).status());
        service.confirm(q.id());
        money("95");
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), any());
    }

    @Test
    void concurrencyDispatchesExactlyOnceAndCommitsDebitBeforeHttp() throws Exception {
        var q = quote();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(gateway.execute(any(), any(), any(), eq("CREATE"), any()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            money("95");
                            entered.countDown();
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            return new RemoteResult("up-1", "ACTIVE", 0, null);
                        });
        Future<QuoteView> first =
                threads.submit(
                        () -> {
                            auth(7, "ROLE_USER");
                            return service.confirm(q.id());
                        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        Future<QuoteView> second =
                threads.submit(
                        () -> {
                            auth(7, "ROLE_USER");
                            return service.confirm(q.id());
                        });
        assertEquals("DISPATCHING", second.get(5, TimeUnit.SECONDS).state());
        release.countDown();
        assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).state());
        money("95");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, times(1)).execute(any(), any(), any(), any(), any());
    }

    @Test
    void insufficientFundsRollBackOrderReservationAndDoNotDispatch() {
        jdbc.update("UPDATE sys_user SET balance=1 WHERE id=7");
        var q = quote();
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        money("1");
        assertEquals("READY", operations.selectById(q.id()).getState());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void ledgerFailureRollsBackBothBalanceAndOrder() {
        jdbc.execute("ALTER TABLE account_ledger ADD CONSTRAINT reject_entries CHECK(amount < 1)");
        var q = quote();
        assertThrows(RuntimeException.class, () -> service.confirm(q.id()));
        money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void expiredOrChangedQuoteNeverDispatches() {
        var expired = quote();
        jdbc.update(
                "UPDATE service_order_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusMinutes(1),
                expired.id());
        assertThrows(BusinessException.class, () -> service.confirm(expired.id()));
        var changed = quote();
        jdbc.update("UPDATE service_product SET version=version+1 WHERE id=1");
        assertThrows(BusinessException.class, () -> service.confirm(changed.id()));
        var providerChanged = quote();
        provider.setConfigVersion(3L);
        assertThrows(ProviderRequestException.class, () -> service.confirm(providerChanged.id()));
        money("100");
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void timeoutsAndNegativeBridgeRepliesAreNotAutomaticallyRetriedOrRefunded() {
        for (var reason :
                List.of(
                        ProviderRequestException.Reason.TIMEOUT,
                        ProviderRequestException.Reason.UPSTREAM_REJECTED)) {
            when(gateway.execute(any(), any(), any(), eq("CREATE"), any()))
                    .thenThrow(new ProviderRequestException(reason));
            var q = quote();
            var r = service.confirm(q.id());
            assertEquals("UNKNOWN", r.state());
            service.confirm(q.id());
            assertEquals("CONFIRMING", service.order(r.orderId()).status());
            assertTrue(service.order(r.orderId()).actions().isEmpty());
        }
        money("90");
        verify(gateway, times(2)).execute(any(), any(), any(), any(), any());
    }

    @Test
    void unknownOperationRequiresPrivilegedAuditedReconciliationAndCreditsOnce() {
        when(gateway.execute(any(), any(), any(), eq("CREATE"), any()))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        var r = create();
        var command = new ResolveForm("NOT_ACCEPTED", null, null, "已在上游订单中心及账务流水核实未受理", true);
        assertThrows(BusinessException.class, () -> service.resolve(r.id(), command));
        money("95");
        auth(7, "api-provider:update");
        assertThrows(BusinessException.class, () -> service.resolve(r.id(), command));
        auth(7, "api-provider:update", "payment:reconcile");
        assertEquals("NOT_ACCEPTED", service.resolve(r.id(), command).state());
        money("100");
        assertThrows(BusinessException.class, () -> service.resolve(r.id(), command));
        money("100");
        assertEquals(7L, operations.selectById(r.id()).getResolvedBy());
    }

    @Test
    void addTimesAndRefundUseFrozenRetailPriceWithExactlyOnceLedgerEntries() {
        var created = create();
        String id = created.orderId();
        when(gateway.execute(any(), any(), any(), eq("ADD_TIMES"), any()))
                .thenReturn(new RemoteResult("remote", "ACTIVE", null, null));
        var add = service.quoteAction(id, new ActionForm("ADD_TIMES", 4));
        assertEquals("2.00", add.amount());
        service.confirm(add.id());
        money("93");
        assertEquals(14, service.order(id).quantity());
        when(gateway.refundRemaining(any(), any())).thenReturn(10);
        when(gateway.execute(any(), any(), any(), eq("REFUND"), any()))
                .thenReturn(new RemoteResult("remote", "REFUNDED", null, 8));
        var refund = service.quoteAction(id, new ActionForm("REFUND", 0));
        assertEquals("5.00", refund.amount());
        var result = service.confirm(refund.id());
        assertEquals("4.00", result.amount());
        money("97");
        service.confirm(refund.id());
        money("97");
        assertEquals("REFUNDED", service.order(id).status());
        assertEquals("4.00", service.order(id).refundedAmount());
    }

    @Test
    void statusSyncDoesNotRefundMoneyAndCannotCrossOwnerBoundary() {
        var c = create();
        when(gateway.sync(any(), any()))
                .thenAnswer(
                        a ->
                                new RemoteResult(
                                        ((ServiceOrder) a.getArgument(1)).getExternalOrderNo(),
                                        "REFUND_REVIEW",
                                        4,
                                        null));
        assertEquals("REFUND_REVIEW", service.sync(c.orderId()).status());
        money("95");
        auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.order(c.orderId()));
        assertThrows(BusinessException.class, () -> service.sync(c.orderId()));
        assertThrows(BusinessException.class, () -> service.events(c.orderId()));
        assertThrows(BusinessException.class, () -> service.operation(c.id()));
        assertThrows(BusinessException.class, () -> service.confirm(c.id()));
        assertEquals(0, service.orders(1, 20, false).getTotal());
    }

    @Test
    void addressIdentityChangeCannotRedirectExistingOrdersButCredentialRotationCan() {
        var c = create();
        provider.setApiUrl("https://other.example");
        assertThrows(BusinessException.class, () -> service.sync(c.orderId()));
        provider.setApiUrl("https://authorized.example");
        provider.setApiKey("rotated");
        provider.setConfigVersion(4L);
        when(gateway.sync(any(), any()))
                .thenAnswer(
                        a ->
                                new RemoteResult(
                                        ((ServiceOrder) a.getArgument(1)).getExternalOrderNo(),
                                        "ACTIVE",
                                        2,
                                        null));
        assertEquals(2, service.sync(c.orderId()).completed());
    }

    @Test
    void featureIsClosedByDefaultAndUserCannotManageCatalog() {
        assertThrows(BusinessException.class, () -> service.products(1, 20, true));
        assertThrows(BusinessException.class, () -> service.orders(1, 20, true));
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThrows(BusinessException.class, () -> service.products(1, 20, false));
        assertThrows(BusinessException.class, this::quote);
    }

    @Test
    void expiredSensitivePayloadsAreErasedLocallyWithoutSupplierTraffic() {
        var q = quote();
        jdbc.update(
                "UPDATE service_order_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusHours(1),
                q.id());
        service.expireQuotes();
        assertNull(operations.selectById(q.id()).getPayloadEncrypted());
        assertEquals("EXPIRED", service.operation(q.id()).state());
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    String refundReview() {
        String id = create().orderId();
        when(gateway.sync(any(), any()))
                .thenAnswer(
                        a ->
                                new RemoteResult(
                                        ((ServiceOrder) a.getArgument(1)).getExternalOrderNo(),
                                        "REFUND_REVIEW",
                                        4,
                                        null));
        service.sync(id);
        return id;
    }

    RefundSettlementForm settlement(String id, int units) {
        return new RefundSettlementForm(
                orders.selectById(id).getVersion(), units, "已核实上游订单退款完成并查验资金流水", true);
    }

    @Test
    void unsolicitedUpstreamRefundHasTwoStepLocalSettlementWithoutAnotherSupplierCall() {
        String id = refundReview();
        auth(8, "api-provider:update", "payment:reconcile");
        provider.setStatus(0); // Settlement must still work after the supplier becomes unavailable.
        clearInvocations(gateway, catalog);
        var quote = service.quoteRefundSettlement(id, settlement(id, 6));
        assertEquals("READY", quote.state());
        assertEquals("3.00", quote.amount());
        money("95");
        assertEquals("REFUND_REVIEW", orders.selectById(id).getStatus());
        assertEquals("SUCCEEDED", service.confirmRefundSettlement(quote.id()).state());
        money("98");
        service.confirmRefundSettlement(quote.id());
        money("98");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals(8L, operations.selectById(quote.id()).getResolvedBy());
        assertFalse(operations.selectById(quote.id()).toString().contains("资金流水"));
        verifyNoInteractions(gateway, catalog);
        auth(7, "ROLE_USER");
        assertEquals("REFUNDED", service.order(id).status());
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        assertThrows(BusinessException.class, () -> service.confirmRefundSettlement(quote.id()));
    }

    @Test
    void independentRefundPreviewsCannotCreditTheSameOrderTwiceUnderConcurrency() throws Exception {
        String id = refundReview();
        auth(8, "api-provider:update", "payment:reconcile");
        var first = service.quoteRefundSettlement(id, settlement(id, 6));
        var second = service.quoteRefundSettlement(id, settlement(id, 6));
        List<Future<Boolean>> results = new ArrayList<>();
        for (var quote : List.of(first, second))
            results.add(
                    threads.submit(
                            () -> {
                                auth(8, "api-provider:update", "payment:reconcile");
                                try {
                                    service.confirmRefundSettlement(quote.id());
                                    return true;
                                } catch (BusinessException expected) {
                                    return false;
                                }
                            }));
        int accepted = 0;
        for (var result : results) if (result.get(5, TimeUnit.SECONDS)) accepted++;
        assertEquals(1, accepted);
        money("98");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account_ledger WHERE biz_type='REFUND'",
                        Integer.class));
    }

    @Test
    void refundSettlementLedgerFailureRollsBackOrderAndConfirmationTogether() {
        String id = refundReview();
        auth(8, "api-provider:update", "payment:reconcile");
        var quote = service.quoteRefundSettlement(id, settlement(id, 6));
        jdbc.execute(
                "ALTER TABLE account_ledger ADD CONSTRAINT reject_refund CHECK(biz_type <>"
                        + " 'REFUND')");
        assertThrows(RuntimeException.class, () -> service.confirmRefundSettlement(quote.id()));
        money("95");
        assertEquals("READY", operations.selectById(quote.id()).getState());
        assertEquals("REFUND_REVIEW", orders.selectById(id).getStatus());
        assertEquals(new BigDecimal("0.00"), orders.selectById(id).getRefundedAmount());
    }

    @Test
    void settlementRequiresAttestationFreshVersionRefundStateAndBothPermissions() {
        String id = refundReview();
        assertThrows(
                BusinessException.class,
                () -> service.quoteRefundSettlement(id, settlement(id, 6)));
        auth(8, "api-provider:update");
        assertThrows(
                BusinessException.class,
                () -> service.quoteRefundSettlement(id, settlement(id, 6)));
        auth(8, "payment:reconcile");
        assertThrows(
                BusinessException.class,
                () -> service.quoteRefundSettlement(id, settlement(id, 6)));
        auth(8, "api-provider:update", "payment:reconcile");
        assertThrows(
                BusinessException.class,
                () -> service.quoteRefundSettlement(id, settlement(id, 7)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quoteRefundSettlement(
                                id, new RefundSettlementForm(-1L, 6, "已核实上游订单退款并查验资金流水", true)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quoteRefundSettlement(
                                id,
                                new RefundSettlementForm(
                                        orders.selectById(id).getVersion(),
                                        6,
                                        "已核实上游订单退款并查验资金流水",
                                        false)));
        var quote = service.quoteRefundSettlement(id, settlement(id, 6));
        jdbc.update("UPDATE service_order SET version=version+1 WHERE id=?", id);
        assertThrows(BusinessException.class, () -> service.confirmRefundSettlement(quote.id()));
        money("95");
    }

    @Test
    void unpublishingWorksWithoutAnActiveSupplierOrCatalogRequest() {
        clearInvocations(catalog, gateway);
        provider.setStatus(0);
        auth(8, "api-provider:update");
        var result =
                service.saveProduct(
                        1L,
                        new ProductCommand(
                                9L, "default", "1", "极光晨跑", "", new BigDecimal("0.25"), false, 0L));
        assertFalse(result.enabled());
        assertEquals(1L, result.version());
        verifyNoInteractions(catalog, gateway);
    }

    @Test
    void remoteStatusCannotSettleMoneyOrSwitchTheExactRemoteOrderReference() {
        String id = create().orderId();
        when(gateway.sync(any(), any())).thenReturn(new RemoteResult("OTHER", "ACTIVE", 4, null));
        assertThrows(BusinessException.class, () -> service.sync(id));
        when(gateway.sync(any(), any()))
                .thenAnswer(
                        a ->
                                new RemoteResult(
                                        ((ServiceOrder) a.getArgument(1)).getExternalOrderNo(),
                                        "REFUNDED",
                                        4,
                                        null));
        assertThrows(BusinessException.class, () -> service.sync(id));
        money("95");
        assertEquals("ACTIVE", orders.selectById(id).getStatus());
    }

    @Test
    void sensitiveOrderValidationAlsoAppliesToNonControllerCallers() {
        assertThrows(
                BusinessException.class,
                () ->
                        service.quote(
                                1L,
                                new OrderForm(0, new BigDecimal("2"), Map.of(), List.of(), true)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quote(
                                1L,
                                new OrderForm(
                                        10, new BigDecimal("2"), Map.of(), List.of(), false)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quote(
                                1L,
                                new OrderForm(
                                        10, new BigDecimal("-2"), Map.of(), List.of(), true)));
        money("100");
        verify(gateway, never()).prepare(any(), any(), any());
    }

    @Test
    void nativeTimestampsAndRecoveryRemainInBeijingTimeOnAUtcHost() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var quote = quote();
            assertTrue(
                    operations
                            .selectById(quote.id())
                            .getExpiresAt()
                            .isAfter(ServiceTime.now().plusMinutes(4)));
            service.confirm(quote.id());
            assertTrue(
                    operations
                            .selectById(quote.id())
                            .getUpdateTime()
                            .isAfter(ServiceTime.now().minusMinutes(1)));
            assertTrue(
                    orders.selectById(operations.selectById(quote.id()).getOrderId())
                            .getUpdateTime()
                            .isAfter(ServiceTime.now().minusMinutes(1)));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void auditExposesOnlyPrivilegedReconciliationDataAndUserHistoryStaysMasked() {
        String id = refundReview();
        assertThrows(BusinessException.class, () -> service.audit(id));
        auth(8, "api-provider:update", "payment:reconcile");
        var quote = service.quoteRefundSettlement(id, settlement(id, 6));
        service.confirmRefundSettlement(quote.id());
        var audit = service.audit(id);
        assertEquals(7L, audit.userId());
        assertEquals(9L, audit.providerId());
        assertEquals(orders.selectById(id).getExternalOrderNo(), audit.externalOrderNo());
        assertTrue(
                audit.events().stream()
                        .anyMatch(
                                e ->
                                        Long.valueOf(8L).equals(e.resolvedBy())
                                                && e.evidence().contains("资金流水")));
        assertFalse(audit.toString().contains("sensitive-password"));
        auth(7, "ROLE_USER");
        assertFalse(service.events(id).toString().contains("资金流水"));
    }

    @Test
    void serviceCategoriesAreFilteredBeforePagination() {
        assertEquals(1, service.products(1, 20, false, "jiguang").getTotal());
        assertEquals(0, service.products(1, 20, false, "wuxin").getTotal());
        assertThrows(BusinessException.class, () -> service.products(1, 20, false, "unknown"));
    }

    Long flashProduct() {
        provider.setProviderType("flash");
        var flash = mock(PluginReadOnlyConnector.class);
        when(flash.getProviderType()).thenReturn("flash");
        when(flash.fetchCatalog(any(), any()))
                .thenReturn(
                        List.of(new PluginProduct("sdxy", "闪动校园", new BigDecimal("0.10"), "元/次")));
        ReflectionTestUtils.setField(
                service, "catalogs", new PluginConnectorRegistry(List.of(flash)));
        auth(7, "api-provider:update");
        Long id =
                service.saveProduct(
                                null,
                                new ProductCommand(
                                        9L,
                                        "sdxy",
                                        "sdxy",
                                        "闪电",
                                        "",
                                        new BigDecimal("0.25"),
                                        true,
                                        null))
                        .id();
        auth(7, "ROLE_USER");
        return id;
    }

    @Test
    void flashQuoteExpiresBeforeItsFirstConcreteTaskAndExpiredPayloadsDisappearOnRead() {
        Long id = flashProduct();
        var due = ServiceTime.now().plusMinutes(2).withNano(0);
        var form =
                new OrderForm(
                        1,
                        new BigDecimal("2"),
                        Map.of(),
                        List.of(
                                due.format(
                                        java.time.format.DateTimeFormatter.ofPattern(
                                                "uuuu-MM-dd HH:mm:ss"))),
                        true);
        var quote = service.quote(id, form);
        assertEquals(due, quote.expiresAt());
        jdbc.update(
                "UPDATE service_order_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                quote.id());
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        assertEquals("EXPIRED", service.operation(quote.id()).state());
        assertNull(
                service.operation(quote.id()).orderId(),
                "an expired preview is not a created business order");
        assertNull(operations.selectById(quote.id()).getPayloadEncrypted());
        money("100");
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void flashProgressWithoutAnUpstreamCountIsUnknownNotZeroPercent() {
        Long id = flashProduct();
        var form =
                new OrderForm(
                        1,
                        new BigDecimal("2"),
                        Map.of(),
                        List.of(
                                ServiceTime.now()
                                        .plusDays(1)
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "uuuu-MM-dd HH:mm:ss"))),
                        true);
        var created = service.confirm(service.quote(id, form).id());
        assertNull(service.order(created.orderId()).completed());
        jdbc.update(
                "UPDATE service_order SET status='COMPLETED', completed=quantity WHERE id=?",
                created.orderId());
        assertEquals(1, service.order(created.orderId()).completed());
    }

    @Test
    void anExpiredAdminRefundPreviewIsShownAsExpiredRatherThanInvitingAnotherConfirmation() {
        String id = refundReview();
        auth(8, "api-provider:update", "payment:reconcile");
        var quote = service.quoteRefundSettlement(id, settlement(id, 6));
        jdbc.update(
                "UPDATE service_order_operation SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                quote.id());
        assertEquals("EXPIRED", service.adminOperation(quote.id()).state());
        assertThrows(BusinessException.class, () -> service.confirmRefundSettlement(quote.id()));
        money("95");
    }

    com.course.platform.infra.external.ApiHttpClient internshipHttp;
    java.util.concurrent.atomic.AtomicReference<Map<String, Object>> internshipRemote;
    final com.fasterxml.jackson.databind.ObjectMapper internshipJson =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    InternshipSchedule internshipSchedule(int offset) {
        return new InternshipSchedule(
                ServiceTime.now().toLocalDate().plusDays(offset),
                List.of(1, 2, 3, 4, 5, 6, 7),
                "08:00:00",
                "18:00:00",
                1,
                true,
                true,
                false,
                false,
                false,
                7,
                0,
                null);
    }

    OrderForm internshipForm(int offset) {
        return new OrderForm(
                0,
                null,
                Map.of(
                        "account",
                        "student-2026",
                        "password",
                        "secret-student-password",
                        "name",
                        "本人",
                        "address",
                        "授权实习地址",
                        "lat",
                        "28.1",
                        "lng",
                        "112.1"),
                List.of(),
                true,
                internshipSchedule(offset));
    }

    ProductCommand internshipProductCommand(boolean enabled, Long version) {
        return new ProductCommand(
                9L,
                "zxjy",
                "zxjy",
                "实习服务",
                "按约定服务日计费",
                new BigDecimal("0.25"),
                enabled,
                version,
                new ContractPriceForm(
                        new BigDecimal("0.10"),
                        ServiceTime.now().toLocalDate().plusDays(30),
                        "已核实上游合同单价、取消规则与资金结算方式",
                        true));
    }

    Long setupInternship() throws Exception {
        provider.setProviderType("sxdk_tw");
        internshipHttp = mock(com.course.platform.infra.external.ApiHttpClient.class);
        internshipRemote = new java.util.concurrent.atomic.AtomicReference<>();
        var adapter =
                new com.course.platform.infra.servicecommerce.InternshipNativeServiceGateway(
                        internshipHttp, new com.course.platform.infra.http.ProviderUrlNormalizer());
        ReflectionTestUtils.setField(service, "gateway", adapter);
        when(internshipHttp.postForString(any(), anyString(), anyMap()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            Map<String, Object> params = a.getArgument(2);
                            String act = params.get("act").toString();
                            if ("addOrder".equals(act) || "editOrder".equals(act)) {
                                var row = new LinkedHashMap<>(params);
                                row.remove("key");
                                row.remove("uid");
                                row.remove("act");
                                row.put("id", "SX-ORDER-1");
                                row.put("code", 1);
                                internshipRemote.set(row);
                            }
                            if ("changeStatus".equals(act))
                                internshipRemote.get().put("code", params.get("code"));
                            return "addOrder".equals(act)
                                    ? "{\"code\":0,\"data\":{\"upstreamOrderId\":\"SX-ORDER-1\"}}"
                                    : "{\"code\":0}";
                        });
        when(internshipHttp.getForString(any(), anyString(), anyMap()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return internshipJson.writeValueAsString(
                                    Map.of("code", 0, "data", List.of(internshipRemote.get())));
                        });
        auth(7, "api-provider:update");
        Long product = service.saveProduct(null, internshipProductCommand(true, null)).id();
        auth(7, "ROLE_USER");
        return product;
    }

    @Test
    void internshipContractPricingIsExplicitPrivateAndNeverFetchedFromAnInventedEndpoint()
            throws Exception {
        Long product = setupInternship();
        verifyNoInteractions(internshipHttp);
        var publicView = service.products(1, 20, false, "sxdk_tw").getRecords().get(0);
        assertNull(publicView.contractPrice());
        assertEquals("元/服务日", publicView.priceUnit());
        var preview = service.quote(product, internshipForm(2));
        assertEquals("0.75", preview.amount());
        assertEquals(3, preview.quantity());
        assertEquals("天", preview.quantityUnit());
        money("100");
        var operation = operations.selectById(preview.id());
        assertFalse(operation.getScheduleJson().contains("password"));
        assertTrue(operation.getPayloadEncrypted().startsWith("ENC:"));
        auth(7, "api-provider:update");
        assertNotNull(service.products(1, 20, true, "sxdk_tw").getRecords().get(0).contractPrice());
        assertThrows(
                BusinessException.class,
                () ->
                        service.saveProduct(
                                null,
                                new ProductCommand(
                                        9L,
                                        "gxy",
                                        "gxy",
                                        "无合同",
                                        "",
                                        new BigDecimal("0.25"),
                                        true,
                                        null)));
        assertFalse(
                service.products(1, 20, true, "sxdk_tw")
                        .getRecords()
                        .get(0)
                        .toString()
                        .contains("secret-student"));
    }

    @Test
    void internshipCreateRenewEditAndCancelReuseOneAtomicLedgerWithoutRepeatedCharges()
            throws Exception {
        Long p = setupInternship();
        var quote = service.quote(p, internshipForm(2));
        var created = service.confirm(quote.id());
        String id = created.orderId();
        money("99.25");
        assertNull(service.order(id).distance());
        assertNull(service.order(id).completed());
        var renew =
                service.quoteAction(
                        id,
                        new ActionForm(
                                "EDIT_SCHEDULE", 0, Map.of("password", ""), internshipSchedule(4)));
        assertEquals("0.50", renew.amount());
        money("99.25");
        service.confirm(renew.id());
        service.confirm(renew.id());
        money("98.75");
        assertEquals(5, service.order(id).quantity());
        var reduce =
                service.quoteAction(
                        id, new ActionForm("EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(1)));
        assertEquals("0.00", reduce.amount());
        service.confirm(reduce.id());
        money("98.75");
        var restore =
                service.quoteAction(
                        id, new ActionForm("EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(4)));
        assertEquals("0.00", restore.amount());
        service.confirm(restore.id());
        money("98.75");
        var now = service.quoteAction(id, new ActionForm("RUN_NOW", 0));
        assertEquals("0.25", now.amount());
        service.confirm(now.id());
        money("98.50");
        assertEquals(5, service.order(id).quantity());
        var refund = service.quoteAction(id, new ActionForm("REFUND", 0));
        assertEquals("1.25", refund.amount());
        service.confirm(refund.id());
        money("99.75");
        assertEquals("REFUNDED", service.order(id).status());
        service.confirm(refund.id());
        money("99.75");
        verify(internshipHttp, times(1))
                .postForString(any(), anyString(), argThat(m -> "addOrder".equals(m.get("act"))));
        verify(internshipHttp, times(1))
                .postForString(any(), anyString(), argThat(m -> "delOrder".equals(m.get("act"))));
    }

    @Test
    void anUncertainInternshipRenewalKeepsOldPlanUntilPrivilegedReconciliation() throws Exception {
        Long p = setupInternship();
        String id = service.confirm(service.quote(p, internshipForm(2)).id()).orderId();
        var renew =
                service.quoteAction(
                        id, new ActionForm("EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(4)));
        when(internshipHttp.postForString(
                        any(), anyString(), argThat(m -> "editOrder".equals(m.get("act")))))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        assertEquals("UNKNOWN", service.confirm(renew.id()).state());
        money("98.75");
        assertEquals(3, orders.selectById(id).getQuantity());
        service.confirm(renew.id());
        verify(internshipHttp, times(1))
                .postForString(any(), anyString(), argThat(m -> "editOrder".equals(m.get("act"))));
        auth(8, "api-provider:update", "payment:reconcile");
        service.resolve(
                renew.id(),
                new ResolveForm("NOT_ACCEPTED", null, null, "已核实上游并未受理此次续期操作，原周期未变化", true));
        money("99.25");
        assertEquals(3, orders.selectById(id).getQuantity());
    }

    @Test
    void expiredContractsPreventDebitButDoNotBlockCancellationOfAlreadyPaidPlans()
            throws Exception {
        Long p = setupInternship();
        var quoted = service.quote(p, internshipForm(2));
        String id = service.confirm(quoted.id()).orderId();
        var next = service.quote(p, internshipForm(1));
        jdbc.update(
                "UPDATE service_product SET contract_valid_until=? WHERE id=?",
                ServiceTime.now().toLocalDate().minusDays(1),
                p);
        assertThrows(BusinessException.class, () -> service.confirm(next.id()));
        assertThrows(BusinessException.class, () -> service.quote(p, internshipForm(1)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quoteAction(
                                id,
                                new ActionForm(
                                        "EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(4))));
        var refund = service.quoteAction(id, new ActionForm("REFUND", 0));
        service.confirm(refund.id());
        money("100");
    }

    @Test
    void changedInternshipScheduleMakesAnOldRefundQuoteUnconfirmable() throws Exception {
        Long p = setupInternship();
        String id = service.confirm(service.quote(p, internshipForm(2)).id()).orderId();
        var refund = service.quoteAction(id, new ActionForm("REFUND", 0));
        var renewal =
                service.quoteAction(
                        id, new ActionForm("EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(4)));
        service.confirm(renewal.id());
        assertThrows(BusinessException.class, () -> service.confirm(refund.id()));
        verify(internshipHttp, never())
                .postForString(any(), anyString(), argThat(m -> "delOrder".equals(m.get("act"))));
        money("98.75");
    }

    @Test
    void internshipZeroQuantityAndNullableDistanceDoNotRelaxSportsValidation() throws Exception {
        assertThrows(
                BusinessException.class,
                () ->
                        service.quote(
                                1L,
                                new OrderForm(0, new BigDecimal("2"), Map.of(), List.of(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.quote(1L, new OrderForm(1, null, Map.of(), List.of(), true)));
        Long p = setupInternship();
        var f = internshipForm(2);
        assertThrows(
                BusinessException.class,
                () ->
                        service.quote(
                                p,
                                new OrderForm(1, null, f.fields(), List.of(), true, f.schedule())));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quote(
                                p,
                                new OrderForm(
                                        0,
                                        BigDecimal.ONE,
                                        f.fields(),
                                        List.of(),
                                        true,
                                        f.schedule())));
        verifyNoInteractions(internshipHttp);
        money("100");
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation", Integer.class));
    }

    @Test
    void renewalsAndImmediateRunsCannotBecomeFreeThroughSubCentRounding() throws Exception {
        Long p = setupInternship();
        jdbc.update(
                "UPDATE service_product SET unit_price=0.001, contract_unit_cost=0.001 WHERE id=?",
                p);
        String id = service.confirm(service.quote(p, internshipForm(9)).id()).orderId();
        assertThrows(
                BusinessException.class,
                () -> service.quoteAction(id, new ActionForm("RUN_NOW", 0)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.quoteAction(
                                id,
                                new ActionForm(
                                        "EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(10))));
        assertEquals(10, service.order(id).quantity());
        money("99.99");
        verify(internshipHttp, never())
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        "nowCheck".equals(m.get("act"))
                                                || "editOrder".equals(m.get("act"))));
    }

    @Test
    void aCalendarQuotePreparedAcrossBeijingMidnightIsNotSavedOrDebited() throws Exception {
        var initial = java.time.LocalDateTime.of(2026, 9, 7, 23, 59, 58);
        var now = new java.util.concurrent.atomic.AtomicReference<>(initial);
        try (var clock = mockStatic(ServiceTime.class)) {
            clock.when(ServiceTime::now).thenAnswer(a -> now.get());
            Long p = setupInternship();
            String id = service.confirm(service.quote(p, internshipForm(2)).id()).orderId();
            when(internshipHttp.getForString(any(), anyString(), anyMap()))
                    .thenAnswer(
                            a -> {
                                now.set(initial.plusSeconds(3));
                                return internshipJson.writeValueAsString(
                                        Map.of("code", 0, "data", List.of(internshipRemote.get())));
                            });
            assertThrows(
                    BusinessException.class,
                    () ->
                            service.quoteAction(
                                    id,
                                    new ActionForm(
                                            "EDIT_SCHEDULE", 0, Map.of(), internshipSchedule(4))));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM service_order_operation", Integer.class));
            assertEquals(3, service.order(id).quantity());
            money("99.25");
        }
    }

    @Test
    void flashSingleTaskDelayIsDurableFreeAndDoesNotResumeAPausedOrder() {
        Long product = flashProduct();
        String time =
                ServiceTime.now()
                        .plusDays(1)
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "uuuu-MM-dd HH:mm:ss"));
        String id =
                service.confirm(
                                service.quote(
                                                product,
                                                new OrderForm(
                                                        1,
                                                        new BigDecimal("2"),
                                                        Map.of(),
                                                        List.of(time),
                                                        true))
                                        .id())
                        .orderId();
        jdbc.update("UPDATE service_order SET status='PAUSED' WHERE id=?", id);
        when(gateway.prepareAction(any(), any(), eq("DELAY_TASK"), anyMap()))
                .thenReturn(Map.of("run_task_id", "TASK-1"));
        when(gateway.execute(any(), any(), any(), eq("DELAY_TASK"), anyMap()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            ServiceOrder saved = a.getArgument(2);
                            return new RemoteResult(
                                    saved.getExternalOrderNo(), "ACTIVE", null, null);
                        });
        var quote =
                service.quoteAction(
                        id,
                        new ActionForm("DELAY_TASK", 0, Map.of("taskId", "TASK-1", "page", "1")));
        assertEquals("0.00", quote.amount());
        assertEquals("无需额外扣款", quote.amountLabel());
        assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
        assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
        verify(gateway, times(1))
                .execute(
                        any(), any(), any(), eq("DELAY_TASK"), eq(Map.of("run_task_id", "TASK-1")));
        assertEquals("PAUSED", service.order(id).status());
        money("99.50");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    ServiceAccountSessionServiceImpl accountSessions;
    com.course.platform.infra.external.ApiHttpClient authHttp;
    com.course.platform.security.RateLimitService authLimiter;

    Long setupFlashAuthorization() {
        Long id = flashProduct();
        authHttp = mock(com.course.platform.infra.external.ApiHttpClient.class);
        var adapter =
                new com.course.platform.infra.servicecommerce.PhpNativeServiceGateway(
                        authHttp, new com.course.platform.infra.http.ProviderUrlNormalizer());
        authLimiter = mock(com.course.platform.security.RateLimitService.class);
        when(authLimiter.check(any()))
                .thenReturn(com.course.platform.security.RateLimitDecision.allowed(1));
        accountSessions =
                new ServiceAccountSessionServiceImpl(
                        accountSessionMapper,
                        products,
                        (com.course.platform.application.service.platform.ApiProviderService)
                                ReflectionTestUtils.getField(service, "providers"),
                        adapter,
                        new com.course.platform.infra.servicecommerce.HeishaFaceUrlPolicy(
                                "https://collect.example"),
                        authLimiter,
                        new com.course.platform.config.RateLimitProperties(),
                        (org.springframework.transaction.PlatformTransactionManager)
                                ReflectionTestUtils.getField(service, "transactions"));
        ReflectionTestUtils.setField(accountSessions, "enabled", true);
        ReflectionTestUtils.setField(accountSessions, "cryptoSecret", "test-service-master-key");
        ReflectionTestUtils.setField(service, "accounts", accountSessions);
        when(authHttp.postForString(any(), anyString(), anyMap()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "supplier calls cannot hold database locks");
                            String url = a.getArgument(1);
                            if (url.contains("_get_user_info_by_"))
                                return "{\"code\":0,\"data\":{\"student\":{\"student_id\":\"BOUND-STUDENT\",\"password\":\"server-only-secret\",\"run_rule_lst\":[{\"run_rule_id\":\"r1\",\"label\":\"已授权计划\"}]},\"zone_list\":[{\"zone_id\":\"z1\",\"name\":\"授权区域\"}]}}";
                            if (url.contains("_send_code") || url.contains("_update_run_rule"))
                                return "{\"code\":0}";
                            throw new AssertionError("Unexpected mocked authorization call");
                        });
        return id;
    }

    SessionView authorizeSms(Long id) {
        var session = accountSessions.start(id, new StartForm("SMS", "13800138000", "测试大学", true));
        assertEquals("SMS_SENT", accountSessions.sendCode(session.id()).state());
        return accountSessions.verify(session.id(), new VerifyForm("123456"));
    }

    OrderForm authorizedOrder(String id) {
        return new OrderForm(
                1,
                new BigDecimal("2"),
                Map.of("runRuleId", "r1", "zoneId", "z1", "runType", "SUN"),
                List.of(
                        ServiceTime.now()
                                .plusDays(1)
                                .format(
                                        java.time.format.DateTimeFormatter.ofPattern(
                                                "uuuu-MM-dd HH:mm:ss"))),
                true,
                null,
                id);
    }

    @Test
    void smsSessionCanCreateOneOrderWithoutReplayingOtpAndNeverReturnsCredentials() {
        Long p = setupFlashAuthorization();
        var s = authorizeSms(p);
        assertEquals("READY", s.state());
        assertNotNull(s.lookup());
        assertFalse(s.toString().contains("server-only-secret"));
        assertFalse(s.toString().contains("BOUND-STUDENT"));
        var stored = accountSessionMapper.selectById(s.id());
        assertTrue(stored.getSnapshotEncrypted().startsWith("ENC:"));
        assertFalse(stored.toString().contains("13800138000"));
        accountSessions.sendCode(s.id());
        accountSessions.verify(s.id(), new VerifyForm("123456"));
        var first = service.quote(p, authorizedOrder(s.id()));
        var duplicate = service.quote(p, authorizedOrder(s.id()));
        assertEquals("0.25", first.amount());
        var confirmed = service.confirm(first.id());
        assertEquals("SUCCEEDED", confirmed.state());
        assertEquals("USED", accountSessions.get(s.id()).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        assertThrows(BusinessException.class, () -> service.confirm(duplicate.id()));
        money("99.75");
        verify(authHttp, times(1))
                .postForString(any(), contains("_get_user_info_by_code"), anyMap());
        verify(authHttp, times(1)).postForString(any(), contains("_send_code"), anyMap());
    }

    @Test
    void authorizationConsumptionAndFundsRollbackTogetherOnInsufficientBalance() {
        Long p = setupFlashAuthorization();
        var s = authorizeSms(p);
        var quote = service.quote(p, authorizedOrder(s.id()));
        jdbc.update("UPDATE sys_user SET balance=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        assertEquals("READY", accountSessions.get(s.id()).state());
        assertNotNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
    }

    @Test
    void foreignUsersChangedProvidersRevokedAndExpiredSessionsCannotAuthorizeAnOrder() {
        Long p = setupFlashAuthorization();
        var s = authorizeSms(p);
        auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> accountSessions.get(s.id()));
        assertThrows(
                BusinessException.class,
                () -> accountSessions.verify(s.id(), new VerifyForm("123456")));
        auth(7, "ROLE_USER");
        provider.setConfigVersion(3L);
        assertThrows(BusinessException.class, () -> service.quote(p, authorizedOrder(s.id())));
        provider.setConfigVersion(2L);
        var quote = service.quote(p, authorizedOrder(s.id()));
        accountSessions.revoke(s.id());
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        var next = authorizeSms(p);
        jdbc.update(
                "UPDATE service_account_session SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                next.id());
        assertThrows(BusinessException.class, () -> service.quote(p, authorizedOrder(next.id())));
        assertEquals("EXPIRED", accountSessions.get(next.id()).state());
        assertNull(accountSessionMapper.selectById(next.id()).getSnapshotEncrypted());
        money("100");
    }

    @Test
    void lostSmsVerificationResultNeverAutomaticallyReplaysTheCode() {
        Long p = setupFlashAuthorization();
        var s = accountSessions.start(p, new StartForm("SMS", "13800138000", "", true));
        accountSessions.sendCode(s.id());
        doThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT))
                .when(authHttp)
                .postForString(any(), contains("_get_user_info_by_code"), anyMap());
        assertEquals("UNKNOWN", accountSessions.verify(s.id(), new VerifyForm("123456")).state());
        assertEquals("UNKNOWN", accountSessions.verify(s.id(), new VerifyForm("123456")).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        verify(authHttp, times(1))
                .postForString(any(), contains("_get_user_info_by_code"), anyMap());
        assertThrows(BusinessException.class, () -> service.quote(p, authorizedOrder(s.id())));
        money("100");
    }

    @Test
    void ruleRefreshInvalidatesOldQuotesAndCannotBeReplayedInTheSameSession() {
        Long p = setupFlashAuthorization();
        var s = authorizeSms(p);
        var old = service.quote(p, authorizedOrder(s.id()));
        assertEquals("READY", accountSessions.refreshRules(s.id()).state());
        assertFalse(accountSessions.get(s.id()).canRefreshRules());
        accountSessions.refreshRules(s.id());
        assertThrows(BusinessException.class, () -> service.confirm(old.id()));
        assertEquals(
                "SUCCEEDED",
                service.confirm(service.quote(p, authorizedOrder(s.id())).id()).state());
        money("99.75");
        verify(authHttp, times(1)).postForString(any(), contains("_update_run_rule"), anyMap());
        verify(authHttp, times(1))
                .postForString(any(), contains("_get_user_info_by_password"), anyMap());
    }

    @Test
    void smsLimitsUsePrivateAccountFingerprintsAndFailClosedBeforeSending() {
        Long p = setupFlashAuthorization();
        var s = accountSessions.start(p, new StartForm("SMS", "13800138000", "", true));
        when(authLimiter.check(
                        argThat(
                                r ->
                                        r != null
                                                && r.dimension()
                                                        .equals("service-auth:sms-cooldown"))))
                .thenReturn(com.course.platform.security.RateLimitDecision.denied(45));
        assertThrows(
                com.course.platform.security.RateLimitExceededException.class,
                () -> accountSessions.sendCode(s.id()));
        verifyNoInteractions(authHttp);
        assertEquals("CREATED", accountSessions.get(s.id()).state());
        var captured =
                org.mockito.ArgumentCaptor.forClass(
                        com.course.platform.security.RateLimitRequest.class);
        verify(authLimiter, atLeastOnce()).check(captured.capture());
        assertTrue(
                captured.getAllValues().stream()
                        .filter(
                                r ->
                                        r.dimension().endsWith(":account")
                                                || r.dimension().endsWith("sms-cooldown"))
                        .allMatch(r -> r.keyMaterial().matches("[0-9a-f]{64}")));
        when(authLimiter.check(any()))
                .thenThrow(new BusinessException(ResultCode.RATE_LIMIT_UNAVAILABLE));
        assertThrows(BusinessException.class, () -> accountSessions.sendCode(s.id()));
        verifyNoInteractions(authHttp);
    }

    @Test
    void concurrentSmsVerificationDispatchesOnceAndLateReplyCannotUndoRevocation()
            throws Exception {
        Long p = setupFlashAuthorization();
        var s = accountSessions.start(p, new StartForm("SMS", "13800138000", "测试大学", true));
        accountSessions.sendCode(s.id());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        a -> {
                            entered.countDown();
                            assertTrue(release.await(10, TimeUnit.SECONDS));
                            return "{\"code\":0,\"data\":{\"student\":{\"student_id\":\"BOUND-STUDENT\",\"run_rule_lst\":[{\"run_rule_id\":\"r1\",\"label\":\"授权计划\"}]},\"zone_list\":[{\"zone_id\":\"z1\",\"name\":\"授权区域\"}]}}";
                        })
                .when(authHttp)
                .postForString(any(), contains("_get_user_info_by_code"), anyMap());
        var first =
                threads.submit(
                        () -> {
                            auth(7, "ROLE_USER");
                            return accountSessions.verify(s.id(), new VerifyForm("123456"));
                        });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals("VERIFYING", accountSessions.verify(s.id(), new VerifyForm("123456")).state());
        accountSessions.revoke(s.id());
        release.countDown();
        assertEquals("REVOKED", first.get(15, TimeUnit.SECONDS).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        verify(authHttp, times(1))
                .postForString(any(), contains("_get_user_info_by_code"), anyMap());
    }

    @Test
    void interruptedRequestsCannotBecomeReadyOrBeReplayed() {
        Long p = setupFlashAuthorization();
        var s =
                accountSessions.start(
                        p, new StartForm("PASSWORD", "student-account", "测试大学", true));
        jdbc.update(
                "UPDATE service_account_session SET state='VERIFYING',update_time=? WHERE id=?",
                ServiceTime.now().minusMinutes(3),
                s.id());
        assertEquals("UNKNOWN", accountSessions.get(s.id()).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        assertEquals("UNKNOWN", accountSessions.verify(s.id(), new VerifyForm("password")).state());
        verifyNoInteractions(authHttp);
    }

    @Test
    void disablingSafetyLimitsBlocksAccountAuthorizationRatherThanAllowingSmsAbuse() {
        Long p = setupFlashAuthorization();
        var config =
                (com.course.platform.config.RateLimitProperties)
                        ReflectionTestUtils.getField(accountSessions, "limits");
        config.setEnabled(false);
        assertThrows(
                BusinessException.class,
                () -> accountSessions.start(p, new StartForm("SMS", "13800138000", "", true)));
        config.setEnabled(true);
        config.setFailClosed(false);
        assertThrows(
                BusinessException.class,
                () -> accountSessions.start(p, new StartForm("SMS", "13800138000", "", true)));
        verifyNoInteractions(authHttp);
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM service_account_session", Integer.class));
    }

    @Test
    void anAuthorizationReplyAfterExpiryCannotRestoreCredentialsOrBecomeReady() {
        Long p = setupFlashAuthorization();
        var s =
                accountSessions.start(
                        p, new StartForm("PASSWORD", "student-account", "测试大学", true));
        doAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            jdbc.update(
                                    "UPDATE service_account_session SET expires_at=? WHERE id=?",
                                    ServiceTime.now().minusSeconds(1),
                                    s.id());
                            return "{\"code\":0,\"data\":{\"student\":{\"student_id\":\"BOUND-STUDENT\",\"run_rule_lst\":[{\"run_rule_id\":\"r1\",\"label\":\"授权计划\"}]},\"zone_list\":[{\"zone_id\":\"z1\",\"name\":\"授权区域\"}]}}";
                        })
                .when(authHttp)
                .postForString(any(), contains("_get_user_info_by_password"), anyMap());
        assertEquals(
                "EXPIRED",
                accountSessions.verify(s.id(), new VerifyForm("private-password")).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        assertThrows(BusinessException.class, () -> service.quote(p, authorizedOrder(s.id())));
        money("100");
    }

    @Test
    void theSameAuthorizationCannotBeMovedToAnotherServiceProduct() {
        Long p = setupFlashAuthorization();
        var s = authorizeSms(p);
        var other = products.selectById(p);
        other.setId(null);
        other.setProject("xbd");
        other.setRemoteProductId("xbd");
        products.insert(other);
        assertThrows(
                BusinessException.class,
                () -> accountSessions.prepare(other, provider, authorizedOrder(s.id())));
        assertEquals("READY", accountSessions.get(s.id()).state());
        money("100");
    }

    private static final String HEISHA_PREFLIGHT =
            """
{"code":1,"data":{"account":{"schoolName":"测试大学","phone":"13800138000"},"runPreflightToken":"private-preflight","checkedAt":"2026-09-08T10:00:00+08:00","plans":[{"planOptionId":"p1","runName":"本人计划","singleMinDistanceKm":1,"singleMaxDistanceKm":3,"timeFragments":[]}],"fences":[{"fenceOptionId":"f1","fenceName":"本人区域"}]}}
""";
    private static final String HEISHA_COLLECTION =
            """
{"code":1,"data":{"phone":"13800138000","faceToken":"private-face-token","collectUrl":"https://collect.example/official?c=private-collection-link","batchStatus":{"completed":false,"fileCount":0,"minFileCount":2,"maxFileCount":3}}}
""";
    private static final String HEISHA_COMPLETE =
            """
{"code":1,"data":{"phone":"13800138000","faceToken":"private-face-token","batchStatus":{"completed":true,"fileCount":2,"minFileCount":2,"maxFileCount":3}}}
""";

    Long setupHeishaAuthorization() {
        setupFlashAuthorization(); // Reuse the real account-session/ledger fixture, not its
                                   // product.
        provider.setProviderType("heisha");
        var faceCatalog = mock(PluginReadOnlyConnector.class);
        when(faceCatalog.getProviderType()).thenReturn("heisha");
        when(faceCatalog.fetchCatalog(any(), any()))
                .thenReturn(
                        List.of(new PluginProduct("3", "人脸日常跑", new BigDecimal("0.1"), "元/公里")));
        ReflectionTestUtils.setField(
                service, "catalogs", new PluginConnectorRegistry(List.of(faceCatalog)));
        auth(7, "api-provider:update");
        Long id =
                service.saveProduct(
                                null,
                                new ProductCommand(
                                        9L,
                                        "default",
                                        "3",
                                        "黑鲨人脸日常跑",
                                        "",
                                        new BigDecimal("0.25"),
                                        true,
                                        null))
                        .id();
        auth(7, "ROLE_USER");
        doAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "No SQL transaction during official"
                                        + " preflight/collection/check");
                            String url = a.getArgument(1);
                            if (url.contains("act=preflight")) return HEISHA_PREFLIGHT;
                            if (url.contains("act=collect_link")) return HEISHA_COLLECTION;
                            if (url.contains("act=face_check")) return HEISHA_COMPLETE;
                            throw new AssertionError("Unexpected simulated Heisha request");
                        })
                .when(authHttp)
                .postForString(any(), anyString(), anyMap());
        return id;
    }

    SessionView preflightFace(Long product) {
        var s =
                accountSessions.start(
                        product, new StartForm("PASSWORD", "13800138000", null, true));
        assertEquals("CREATED", s.state());
        var result = accountSessions.verify(s.id(), new VerifyForm("private-password"));
        assertEquals("FACE_REQUIRED", result.state());
        return result;
    }

    SessionView readyFace(Long product) {
        var s = preflightFace(product);
        assertEquals(
                "FACE_PENDING",
                accountSessions.collectFace(s.id(), new FaceConsentForm(true)).state());
        return accountSessions.checkFace(s.id());
    }

    OrderForm faceOrder(String sessionId) {
        return new OrderForm(
                1,
                new BigDecimal("2"),
                Map.of("planOptionId", "p1", "fenceOptionId", "f1", "runTime", "08:00"),
                List.of(),
                true,
                null,
                sessionId);
    }

    @Test
    void officialFaceCheckoutRequiresConsentCollectionAndAuthoritativeCompletionThenConsumesOnce()
            throws Exception {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        assertTrue(s.face().canCollect());
        assertThrows(BusinessException.class, () -> service.quote(p, faceOrder(s.id())));
        assertThrows(BusinessException.class, () -> accountSessions.issueFaceLaunch(s.id()));
        assertThrows(
                BusinessException.class,
                () -> accountSessions.collectFace(s.id(), new FaceConsentForm(false)));
        verify(authHttp, never()).postForString(any(), contains("collect_link"), anyMap());
        var collected = accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        assertEquals("FACE_PENDING", collected.state());
        assertEquals("https://collect.example", collected.face().collectionOrigin());
        assertFalse(collected.face().status().completed());
        assertThrows(BusinessException.class, () -> service.quote(p, faceOrder(s.id())));
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        assertEquals("READY", accountSessions.checkFace(s.id()).state());
        var publicJson =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .writeValueAsString(accountSessions.get(s.id()));
        for (String secret :
                List.of(
                        "13800138000",
                        "private-password",
                        "private-face-token",
                        "private-collection-link",
                        "private-preflight",
                        "collectUrl",
                        "launchDigest")) assertFalse(publicJson.contains(secret), secret);
        var quote = service.quote(p, faceOrder(s.id()));
        var another = service.quote(p, faceOrder(s.id()));
        assertEquals("0.50", quote.amount());
        assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
        assertEquals("USED", accountSessions.get(s.id()).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        assertThrows(BusinessException.class, () -> service.confirm(another.id()));
        money("99.5");
        verify(authHttp, times(1)).postForString(any(), contains("act=preflight"), anyMap());
        verify(authHttp, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
        verify(authHttp, times(1)).postForString(any(), contains("act=face_check"), anyMap());
        verify(gateway, times(1))
                .execute(
                        any(),
                        any(),
                        any(),
                        eq("CREATE"),
                        argThat(
                                m ->
                                        "private-face-token".equals(m.get("face_token"))
                                                && "13800138000".equals(m.get("phone"))));
    }

    @Test
    void collectionReceiptAloneEvenCompletedIsNotCheckoutAuthorization() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        doReturn(
                        HEISHA_COLLECTION
                                .replace("\"completed\":false", "\"completed\":true")
                                .replace("\"fileCount\":0", "\"fileCount\":2"))
                .when(authHttp)
                .postForString(any(), contains("act=collect_link"), anyMap());
        var result = accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        assertEquals("FACE_PENDING", result.state());
        assertFalse(result.face().status().completed());
        assertThrows(BusinessException.class, () -> service.quote(p, faceOrder(s.id())));
        verify(authHttp, never()).postForString(any(), contains("act=face_check"), anyMap());
        money("100");
    }

    @Test
    void hostedNavigationTicketIsOpaqueSingleUseAndNeverRequiresJwtInTheUrl() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        var first = accountSessions.issueFaceLaunch(s.id());
        var second = accountSessions.issueFaceLaunch(s.id());
        assertTrue(second.ticket().matches("[0-9a-f]{64}"));
        assertTrue(second.expiresAt().isBefore(ServiceTime.now().plusSeconds(61)));
        assertFalse(second.toString().contains(second.ticket()));
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), first.ticket()));
        SecurityContextHolder.clearContext();
        var url = accountSessions.consumeFaceLaunch(s.id(), second.ticket());
        assertEquals(
                "https://collect.example/official?c=private-collection-link", url.toASCIIString());
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), second.ticket()));
        auth(7, "ROLE_USER");
        assertEquals("FACE_PENDING", accountSessions.get(s.id()).state());
        verify(authHttp, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
        verify(authHttp, never()).postForString(any(), contains("act=face_check"), anyMap());
        money("100");
    }

    @Test
    void hostedNavigationRequiresSameOwnerProductVersionAndAnUnrevokedSession() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        var ticket = accountSessions.issueFaceLaunch(s.id());
        auth(8, "ROLE_ADMIN");
        assertThrows(BusinessException.class, () -> accountSessions.issueFaceLaunch(s.id()));
        assertThrows(BusinessException.class, () -> accountSessions.checkFace(s.id()));
        assertThrows(BusinessException.class, () -> accountSessions.get(s.id()));
        auth(7, "ROLE_USER");
        assertThrows(
                BusinessException.class,
                () ->
                        accountSessions.consumeFaceLaunch(
                                UUID.randomUUID().toString(), ticket.ticket()));
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), "b".repeat(64)));
        provider.setConfigVersion(3L);
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), ticket.ticket()));
        provider.setConfigVersion(2L);
        var product = products.selectById(p);
        product.setVersion(product.getVersion() + 1);
        products.updateById(product);
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), ticket.ticket()));
        product.setVersion(product.getVersion() - 1);
        products.updateById(product);
        accountSessions.revoke(s.id());
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), ticket.ticket()));
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        money("100");
    }

    @Test
    void expiredNavigationTicketsAndSessionExpiryCannotOpenCollection() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        var ticket = accountSessions.issueFaceLaunch(s.id());
        var row = accountSessionMapper.selectById(s.id());
        AccountSnapshot a = ReflectionTestUtils.invokeMethod(accountSessions, "decrypt", row);
        String encrypted =
                ReflectionTestUtils.invokeMethod(
                        accountSessions,
                        "encrypt",
                        a.withHeisha(
                                a.heisha()
                                        .launch(
                                                a.heisha().launchDigest(),
                                                ServiceTime.now().minusSeconds(1))));
        row.setSnapshotEncrypted(encrypted);
        accountSessionMapper.updateById(row);
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), ticket.ticket()));
        var next = accountSessions.issueFaceLaunch(s.id());
        jdbc.update(
                "UPDATE service_account_session SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                s.id());
        assertThrows(
                BusinessException.class,
                () -> accountSessions.consumeFaceLaunch(s.id(), next.ticket()));
        assertEquals("EXPIRED", accountSessions.get(s.id()).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
    }

    @Test
    void missingApprovedCollectionOriginsBlocksBeforeAnySupplierCollectionRequest() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        ReflectionTestUtils.setField(
                accountSessions,
                "faceUrls",
                new com.course.platform.infra.servicecommerce.HeishaFaceUrlPolicy(""));
        assertThrows(
                BusinessException.class,
                () -> accountSessions.collectFace(s.id(), new FaceConsentForm(true)));
        assertEquals("FACE_REQUIRED", accountSessions.get(s.id()).state());
        verify(authHttp, never()).postForString(any(), contains("act=collect_link"), anyMap());
    }

    @Test
    void unknownCollectionDoesNotReplayAndUnapprovedLinksNeverLeaveTheServer() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        doReturn(HEISHA_COLLECTION.replace("https://collect.example", "https://unapproved.example"))
                .when(authHttp)
                .postForString(any(), contains("act=collect_link"), anyMap());
        assertEquals(
                "UNKNOWN", accountSessions.collectFace(s.id(), new FaceConsentForm(true)).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        assertEquals(
                "UNKNOWN", accountSessions.collectFace(s.id(), new FaceConsentForm(true)).state());
        assertThrows(BusinessException.class, () -> service.quote(p, faceOrder(s.id())));
        verify(authHttp, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
        money("100");
    }

    @Test
    void ambiguousFaceCheckIsRetryableOnlyAsAReadAndNeverInventsCompletion() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        doThrow(new RuntimeException("simulated lost response"))
                .when(authHttp)
                .postForString(any(), contains("act=face_check"), anyMap());
        assertEquals("FACE_RETRY", accountSessions.checkFace(s.id()).state());
        assertNotNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        assertThrows(BusinessException.class, () -> service.quote(p, faceOrder(s.id())));
        assertFalse(accountSessions.get(s.id()).face().status().completed());
        verify(authHttp, times(1)).postForString(any(), contains("act=face_check"), anyMap());
        doReturn(HEISHA_COMPLETE)
                .when(authHttp)
                .postForString(any(), contains("act=face_check"), anyMap());
        assertEquals("READY", accountSessions.checkFace(s.id()).state());
        verify(authHttp, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
        money("100");
    }

    @Test
    void collectDispatchCommitsBeforeHttpAndConcurrentRevokeWinsOverLateSuccess() throws Exception {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            assertEquals(
                                    "COLLECTING",
                                    accountSessionMapper.selectById(s.id()).getState());
                            entered.countDown();
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            return HEISHA_COLLECTION;
                        })
                .when(authHttp)
                .postForString(any(), contains("act=collect_link"), anyMap());
        var future =
                threads.submit(
                        () -> {
                            auth(7, "ROLE_USER");
                            return accountSessions.collectFace(s.id(), new FaceConsentForm(true));
                        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(
                    "COLLECTING",
                    accountSessions.collectFace(s.id(), new FaceConsentForm(true)).state());
            accountSessions.revoke(s.id());
        } finally {
            release.countDown();
        }
        assertEquals("REVOKED", future.get(5, TimeUnit.SECONDS).state());
        assertNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        verify(authHttp, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
    }

    @Test
    void faceAccountConsumptionAndDebitRollbackTogetherAndUnknownOrderNeverRepeats() {
        Long p = setupHeishaAuthorization();
        var s = readyFace(p);
        var q = service.quote(p, faceOrder(s.id()));
        jdbc.update("UPDATE sys_user SET balance=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertEquals("READY", accountSessions.get(s.id()).state());
        jdbc.update("UPDATE sys_user SET balance=100 WHERE id=7");
        doThrow(new RuntimeException("lost order response"))
                .when(gateway)
                .execute(any(), any(), any(), eq("CREATE"), anyMap());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        assertEquals("USED", accountSessions.get(s.id()).state());
        assertEquals("UNKNOWN", service.confirm(q.id()).state());
        money("99.5");
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), anyMap());
    }

    @Test
    void abandonedFaceCheckKeepsOnlyReadRecoveryWhileCollectionCannotBeReplayed() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        jdbc.update(
                "UPDATE service_account_session SET state='FACE_CHECKING', update_time=? WHERE"
                    + " id=?",
                ServiceTime.now().minusMinutes(3),
                s.id());
        assertEquals("FACE_RETRY", accountSessions.get(s.id()).state());
        assertNotNull(accountSessionMapper.selectById(s.id()).getSnapshotEncrypted());
        verify(authHttp, never()).postForString(any(), contains("act=face_check"), anyMap());
        assertEquals("READY", accountSessions.checkFace(s.id()).state());
        assertFalse(accountSessions.get(s.id()).face().canLaunch());
    }

    @Test
    void removingCollectionTrustPreventsAdvertisingPublishingAndConfirmingFaceProducts() {
        Long p = setupHeishaAuthorization();
        var s = readyFace(p);
        var quote = service.quote(p, faceOrder(s.id()));
        ReflectionTestUtils.setField(
                accountSessions,
                "faceUrls",
                new com.course.platform.infra.servicecommerce.HeishaFaceUrlPolicy(""));
        assertFalse(
                service.products(1, 20, false).getRecords().stream()
                        .filter(v -> v.id().equals(p))
                        .findFirst()
                        .orElseThrow()
                        .available());
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        assertEquals("READY", accountSessionMapper.selectById(s.id()).getState());
        money("100");
        verify(gateway, never()).execute(any(), any(), any(), any(), anyMap());
        auth(7, "api-provider:update");
        assertThrows(
                BusinessException.class,
                () ->
                        service.saveProduct(
                                null,
                                new ProductCommand(
                                        9L,
                                        "default",
                                        "3",
                                        "禁止上架",
                                        "",
                                        new BigDecimal("0.25"),
                                        true,
                                        null)));
    }

    @Test
    void twoConcurrentAnonymousHandoffsCanConsumeTheLocalTicketOnlyOnce() throws Exception {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        accountSessions.collectFace(s.id(), new FaceConsentForm(true));
        var ticket = accountSessions.issueFaceLaunch(s.id());
        var gate = new CountDownLatch(1);
        Callable<Boolean> attempt =
                () -> {
                    SecurityContextHolder.clearContext();
                    assertTrue(gate.await(5, TimeUnit.SECONDS));
                    try {
                        accountSessions.consumeFaceLaunch(s.id(), ticket.ticket());
                        return true;
                    } catch (BusinessException ex) {
                        return false;
                    }
                };
        var first = threads.submit(attempt);
        var second = threads.submit(attempt);
        gate.countDown();
        assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        verify(authHttp, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
        money("100");
    }

    @Test
    void faceDispatchRateLimitsFailClosedWithoutChangingAuthorizationOrContactingSupplier() {
        Long p = setupHeishaAuthorization();
        var s = preflightFace(p);
        when(authLimiter.check(any()))
                .thenReturn(com.course.platform.security.RateLimitDecision.denied(60));
        assertThrows(
                com.course.platform.security.RateLimitExceededException.class,
                () -> accountSessions.collectFace(s.id(), new FaceConsentForm(true)));
        assertEquals("FACE_REQUIRED", accountSessions.get(s.id()).state());
        verify(authHttp, never()).postForString(any(), contains("act=collect_link"), anyMap());
        money("100");
    }
    com.course.platform.infra.external.ApiHttpClient distanceHttp;

    Long setupDistance(String price) {
        distanceHttp = mock(com.course.platform.infra.external.ApiHttpClient.class);
        var nativeDistance = new com.course.platform.infra.servicecommerce.SsbenzDistanceGateway(
                distanceHttp, new com.course.platform.infra.http.ProviderUrlNormalizer());
        provider.setProviderType("ssbenz_xbd");
        provider.setApiUrl("https://authorized.example/xbd/ydapi");
        ReflectionTestUtils.setField(service, "gateway", nativeDistance);
        ReflectionTestUtils.setField(service, "catalogs", new PluginConnectorRegistry(List.of(nativeDistance)));
        when(distanceHttp.postForString(any(), endsWith("/school"), anyMap()))
                .thenReturn("{\"code\":1,\"xbdpr\":\"0.10\",\"xbdprs\":\"0.20\",\"dj\":\"ignored SQL\"}");
        when(distanceHttp.postForString(any(), endsWith("/add"), anyMap())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation WHERE state='DISPATCHING'", Integer.class));
            assertTrue(balance().compareTo(new BigDecimal("100")) < 0);
            return "{\"code\":1,\"id\":42}";
        });
        when(distanceHttp.postForString(any(), endsWith("/order"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":42,\"status\":1}]}");
        auth(7, "api-provider:update", "payment:reconcile");
        Long id = service.saveProduct(null, new ProductCommand(9L, "xbd", "0", "总公里计划", "按总公里数计费",
                new BigDecimal(price), true, null)).id();
        auth(7, "ROLE_USER");
        clearInvocations(distanceHttp);
        return id;
    }

    OrderForm distanceForm(String distance) {
        return new OrderForm(1, new BigDecimal(distance), Map.of("account", "13800138000", "password", "distance-private-password",
                "schoolName", "", "startTime", "09:05", "endTime", "21:10", "weekdays", "1,3,5"), List.of(), true);
    }

    @Test void totalDistancePreviewFreezesNonSecretPlanAndDebitsExactlyOnceOutsideRemoteTransaction() throws Exception {
        Long product = setupDistance("0.25");
        var quote = service.quote(product, distanceForm("120.50"));
        assertEquals("30.13", quote.amount()); assertEquals("单", quote.quantityUnit());
        assertEquals("120.50", quote.distancePlan().totalDistance());
        money("100");
        var operation = operations.selectById(quote.id());
        assertTrue(operation.getPayloadEncrypted().startsWith("ENC:v1:"));
        assertFalse(operation.getScheduleJson().contains("password"));
        assertFalse(operation.getScheduleJson().contains("13800138000"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        var confirmed = service.confirm(quote.id());
        service.confirm(quote.id()); service.confirm(quote.id());
        assertEquals("SUCCEEDED", confirmed.state()); money("69.87");
        var view = service.order(confirmed.orderId());
        assertEquals("SUBMITTED", view.status()); assertNull(view.completed());
        assertEquals(1, view.quantity()); assertEquals("单", view.quantityUnit());
        assertEquals(quote.distancePlan(), view.distancePlan());
        assertTrue(view.actions().isEmpty()); assertNull(view.schedule());
        assertNull(operations.selectById(quote.id()).getPayloadEncrypted());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(view);
        assertFalse(json.contains("distance-private-password")); assertFalse(json.contains("13800138000"));
        assertFalse(json.contains("upstream-secret"));
        verify(distanceHttp, times(1)).postForString(any(), endsWith("/add"), anyMap());
        verify(distanceHttp, times(1)).postForString(any(), endsWith("/school"), anyMap());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test void totalDistanceAvoidsDoubleRoundingAndPreservesExistingSchemaRange() {
        Long product = setupDistance("0.499999");
        assertThrows(BusinessException.class, () -> service.quote(product, distanceForm("0.01")));
        var quote = service.quote(product, distanceForm("0.03"));
        assertEquals("0.01", quote.amount()); // 0.01499997 -> 0.01, NOT 0.015000 -> 0.02.
        service.confirm(quote.id()); money("99.99");
        var largest = service.quote(product, distanceForm("999999.99"));
        assertEquals("499999.00", largest.amount());
        assertEquals("999999.99", largest.distancePlan().totalDistance());
        assertThrows(BusinessException.class, () -> service.quote(product, distanceForm("1000000")));
    }

    @Test void oldPerRunServicesStillRejectMoreThanFiftyKilometres() {
        var old = form();
        assertThrows(BusinessException.class, () -> service.quote(1L, new OrderForm(10, new BigDecimal("50.01"), old.fields(), List.of(), true)));
        assertThrows(BusinessException.class, () -> service.quote(1L, new OrderForm(10, new BigDecimal("0.01"), old.fields(), List.of(), true)));
        verifyNoInteractions(gateway); money("100");
    }

    @Test void distanceOrdersRejectQuantityTaskOrSessionConfusionBeforeReadingCatalog() {
        Long product = setupDistance("0.25");
        var valid = distanceForm("120.50");
        for (OrderForm form : List.of(
                new OrderForm(120, valid.distance(), valid.fields(), List.of(), true),
                new OrderForm(0, valid.distance(), valid.fields(), List.of(), true),
                new OrderForm(1, valid.distance(), valid.fields(), List.of("2030-01-01 09:00:00"), true),
                new OrderForm(1, valid.distance(), valid.fields(), List.of(), true, null, UUID.randomUUID().toString())))
            assertThrows(BusinessException.class, () -> service.quote(product, form));
        verifyNoInteractions(distanceHttp); money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation", Integer.class));
    }

    @Test void distanceSubmissionStatusesNeverSetCompletionOrRefundMoney() {
        var q = service.quote(setupDistance("0.25"), distanceForm("120.50"));
        var result = service.confirm(q.id());
        assertEquals("SUBMITTED", service.sync(result.orderId()).status());
        for (String state : List.of("0", "2", "1")) {
            when(distanceHttp.postForString(any(), endsWith("/order"), anyMap()))
                    .thenReturn("{\"code\":1,\"data\":[{\"id\":42,\"status\":" + state + ",\"fees\":30.13,\"statuslog\":\"已退回全部\"}]}");
            var synced = service.sync(result.orderId());
            assertEquals("1".equals(state) ? "SUBMITTED" : "SUBMISSION_REVIEW", synced.status());
            assertNull(synced.completed()); assertTrue(synced.actions().isEmpty()); money("69.87");
            assertEquals("0.00", synced.refundedAmount());
        }
        assertThrows(BusinessException.class, () -> service.quoteAction(result.orderId(), new ActionForm("REFUND", 0)));
        assertThrows(BusinessException.class, () -> service.logs(result.orderId(), 1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test void missingOrDuplicateDistanceStatusLeavesVersionAndFundsUntouched() {
        var result = service.confirm(service.quote(setupDistance("0.25"), distanceForm("120.50")).id());
        var before = service.order(result.orderId());
        for (String raw : List.of("{\"code\":-1}", "{\"code\":1,\"data\":[]}",
                "{\"code\":1,\"data\":[{\"id\":42,\"status\":1},{\"id\":42,\"status\":0}]}")) {
            when(distanceHttp.postForString(any(), endsWith("/order"), anyMap())).thenReturn(raw);
            assertThrows(ProviderRequestException.class, () -> service.sync(result.orderId()));
            var after = service.order(result.orderId());
            assertEquals(before.version(), after.version()); assertEquals(before.status(), after.status()); money("69.87");
        }
    }

    @Test void rejectedOrLostDistanceCreateRemainsUnknownAndOnlyAuditedNonAcceptanceReturnsOriginalDebit() {
        Long product = setupDistance("0.25");
        when(distanceHttp.postForString(any(), endsWith("/add"), anyMap()))
                .thenReturn("{\"code\":-1,\"msg\":\"可能已扣款，请重试\"}");
        var q = service.quote(product, distanceForm("120.50"));
        var unknown = service.confirm(q.id());
        assertEquals("UNKNOWN", unknown.state()); money("69.87");
        assertEquals("CONFIRMING", service.order(unknown.orderId()).status());
        service.confirm(q.id()); service.confirm(q.id());
        assertThrows(BusinessException.class, () -> service.sync(unknown.orderId()));
        var rejection = new ResolveForm("NOT_ACCEPTED", null, null, "已独立核查此笔提交及全部资金记录，确认没有受理", true);
        for (String permission : List.of("ROLE_ADMIN", "api-provider:update", "payment:reconcile")) {
            auth(7, permission); assertThrows(BusinessException.class, () -> service.resolve(q.id(), rejection)); money("69.87");
        }
        auth(7, "api-provider:update", "payment:reconcile");
        var resolved = service.resolve(q.id(), rejection);
        assertEquals("NOT_ACCEPTED", resolved.state()); money("100");
        assertEquals("CANCELLED", service.order(unknown.orderId()).status());
        assertEquals("30.13", service.order(unknown.orderId()).refundedAmount());
        assertThrows(BusinessException.class, () -> service.resolve(q.id(), rejection));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(distanceHttp, times(1)).postForString(any(), endsWith("/add"), anyMap());
    }

    @Test void distanceManualAcceptanceBindsOnlyPositiveReceiptAndNeverClaimsExecutionOrRefundUnits() {
        Long product = setupDistance("0.25");
        when(distanceHttp.postForString(any(), endsWith("/add"), anyMap()))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        var q = service.quote(product, distanceForm("120.50"));
        var unknown = service.confirm(q.id());
        auth(7, "api-provider:update", "payment:reconcile");
        String evidence = "已核对本人提交和资金记录，确认唯一的提交记录编号";
        assertThrows(BusinessException.class, () -> service.resolve(q.id(), new ResolveForm("ACCEPTED", "wrong-id", null, evidence, true)));
        assertThrows(BusinessException.class, () -> service.resolve(q.id(), new ResolveForm("ACCEPTED", "42", 1, evidence, true)));
        assertEquals("UNKNOWN", service.operation(q.id()).state()); money("69.87");
        var accepted = service.resolve(q.id(), new ResolveForm("ACCEPTED", "42", null, evidence, true));
        assertEquals("SUCCEEDED", accepted.state());
        assertEquals("SUBMITTED", service.order(unknown.orderId()).status());
        assertNull(service.order(unknown.orderId()).completed()); money("69.87");
        verify(distanceHttp, times(1)).postForString(any(), endsWith("/add"), anyMap());
    }

    @Test void distanceCannotEnterIntegerRefundSettlementEvenIfStoredStateIsCorrupted() {
        var result = service.confirm(service.quote(setupDistance("0.25"), distanceForm("120.50")).id());
        jdbc.update("UPDATE service_order SET status='REFUND_REVIEW' WHERE id=?", result.orderId());
        auth(7, "api-provider:update", "payment:reconcile");
        assertThrows(BusinessException.class, () -> service.quoteRefundSettlement(result.orderId(),
                new RefundSettlementForm(service.order(result.orderId()).version(), 1, "已核查资金但不能用次数代表公里", true)));
        money("69.87");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation", Integer.class));
    }

    @Test void distanceInsufficientBalanceRollsBackReservationWithoutDispatch() {
        Long product = setupDistance("0.50");
        var q = service.quote(product, distanceForm("300.00"));
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertEquals("READY", operations.selectById(q.id()).getState()); money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(distanceHttp, never()).postForString(any(), endsWith("/add"), anyMap());
    }

    @Test void distanceConcurrentConfirmationCanDispatchOnlyOnePaidOrder() throws Exception {
        var q = service.quote(setupDistance("0.25"), distanceForm("120.50"));
        var gate = new CountDownLatch(1);
        Callable<QuoteView> confirm = () -> {
            auth(7, "ROLE_USER"); assertTrue(gate.await(5, TimeUnit.SECONDS));
            try { return service.confirm(q.id()); } finally { SecurityContextHolder.clearContext(); }
        };
        var first = threads.submit(confirm); var second = threads.submit(confirm); gate.countDown();
        first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        assertEquals("SUCCEEDED", service.operation(q.id()).state()); money("69.87");
        verify(distanceHttp, times(1)).postForString(any(), endsWith("/add"), anyMap());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test void duplicateDistanceReceiptOrLocalSettlementFailureKeepsSecondPreDebitUnknownWithoutReplaying() {
        Long product = setupDistance("0.25");
        service.confirm(service.quote(product, distanceForm("120.50")).id());
        var q = service.quote(product, distanceForm("120.50"));
        var second = service.confirm(q.id());
        assertEquals("UNKNOWN", second.state()); money("39.74");
        assertNull(orders.selectById(second.orderId()).getExternalOrderNo());
        assertEquals(q.id(), service.order(second.orderId()).pendingOperationId());
        service.confirm(q.id());
        verify(distanceHttp, times(2)).postForString(any(), endsWith("/add"), anyMap());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test void distanceOwnershipConfigurationAndFeatureGateStillApply() {
        Long product = setupDistance("0.25");
        var q = service.quote(product, distanceForm("120.50"));
        auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertThrows(BusinessException.class, () -> service.operation(q.id()));
        auth(7, "ROLE_USER");
        provider.setConfigVersion(3L);
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        provider.setConfigVersion(2L);
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThrows(BusinessException.class, () -> service.confirm(q.id()));
        assertThrows(BusinessException.class, () -> service.quote(product, distanceForm("120.50")));
        money("100"); verify(distanceHttp, never()).postForString(any(), endsWith("/add"), anyMap());
    }

    @Test void totalDistanceTokenChangeCannotReadAnotherAccountsSameReceipt() {
        var created = service.confirm(service.quote(setupDistance("0.25"), distanceForm("120.50")).id());
        String identity = orders.selectById(created.orderId()).getProviderIdentity();
        assertTrue(identity.matches("[0-9a-f]{64}"));
        clearInvocations(distanceHttp);
        provider.setApiKey("different-account-token");
        provider.setConfigVersion(3L);
        assertThrows(BusinessException.class, () -> service.sync(created.orderId()));
        verifyNoInteractions(distanceHttp); money("69.87");
        // The unused UID is not identity, and normalized aliases do not create another account.
        provider.setApiKey("upstream-secret");
        provider.setUsername("unused-uid-changed");
        provider.setApiUrl("https://AUTHORIZED.EXAMPLE/xbd/ydapi/");
        provider.setConfigVersion(4L);
        assertEquals("SUBMITTED", service.sync(created.orderId()).status());
        assertEquals(identity, orders.selectById(created.orderId()).getProviderIdentity());
        assertNull(service.order(created.orderId()).completed()); money("69.87");
        verify(distanceHttp, times(1)).postForString(any(), endsWith("/order"), anyMap());
    }

    @Test void totalDistanceIdentityUsesServerSecretNotAnOfflineGuessableTokenHash() {
        var created = service.confirm(service.quote(setupDistance("0.25"), distanceForm("120.50")).id());
        clearInvocations(distanceHttp);
        ReflectionTestUtils.setField(service, "cryptoSecret", "other-test-service-master-key");
        assertThrows(BusinessException.class, () -> service.sync(created.orderId()));
        verifyNoInteractions(distanceHttp); money("69.87");
        ReflectionTestUtils.setField(service, "cryptoSecret", "test-service-master-key");
        assertEquals("SUBMITTED", service.sync(created.orderId()).status());
    }

    @Test
    void distanceChargesKeepEightDecimalSnapshotBeforeFinalCents() {
        jdbc.update("UPDATE service_product SET unit_price=0.499999 WHERE id=1");
        var preview = service.quote(1L, new OrderForm(3, new BigDecimal("0.11"),
                form().fields(), List.of(), true));
        assertEquals(0, new BigDecimal("0.16").compareTo(new BigDecimal(preview.amount())),
                "Round price * distance * quantity only once, to final cents");
        assertEquals(0, new BigDecimal("0.05499989").compareTo(operations.selectById(preview.id()).getUnitCharge()));
        money("100.00");
    }

    QuoteView preciseDistanceQuote(int quantity) {
        return preciseDistanceQuote("0.499999", "0.11", quantity);
    }

    QuoteView preciseDistanceQuote(String price, String distance, int quantity) {
        jdbc.update("UPDATE service_product SET unit_price=? WHERE id=1", new BigDecimal(price));
        when(catalog.fetchCatalog(any(), any())).thenReturn(List.of(
                new PluginProduct("1", "测试服务", new BigDecimal("0.000001"), "元/公里")));
        return service.quote(1L, new OrderForm(quantity, new BigDecimal(distance),
                form().fields(), List.of(), true));
    }

    @Test
    void preciseChargeSurvivesDatabaseAndConfirmationWithoutDuplicateDebit() {
        var quote = preciseDistanceQuote(3);
        assertEquals("0.05499989", quote.unitCharge());
        assertEquals("0.05499989", service.operation(quote.id()).unitCharge());
        var result = service.confirm(quote.id());
        assertEquals("SUCCEEDED", result.state());
        assertEquals("0.16", result.amount());
        assertEquals(0, new BigDecimal("0.05499989").compareTo(orders.selectById(result.orderId()).getUnitCharge()));
        assertEquals("0.16", service.confirm(quote.id()).amount());
        assertEquals("0.16", service.order(result.orderId()).paidAmount());
        money("99.84");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), any());
    }

    @Test
    void preciseAdditionsUseFrozenChargeAfterCatalogPriceChanges() {
        var created = service.confirm(preciseDistanceQuote(3).id());
        jdbc.update("UPDATE service_product SET unit_price=9,version=version+1 WHERE id=1");
        when(gateway.execute(any(), any(), any(), eq("ADD_TIMES"), any()))
                .thenReturn(new RemoteResult("receipt", "ACTIVE", null, null));
        var addition = service.quoteAction(created.orderId(), new ActionForm("ADD_TIMES", 3));
        assertEquals("0.16", addition.amount());
        assertEquals("0.05499989", addition.unitCharge());
        service.confirm(addition.id());
        service.confirm(addition.id());
        money("99.68");
        assertEquals("0.32", service.order(created.orderId()).paidAmount());
        assertEquals(6, service.order(created.orderId()).quantity());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, times(1)).execute(any(), any(), any(), eq("ADD_TIMES"), any());
    }

    @Test
    void preciseRefundUsesActualReceiptUnitsAndRoundsOnlyFinalAmount() {
        var created = service.confirm(preciseDistanceQuote(6).id());
        when(gateway.refundRemaining(any(), any())).thenReturn(6);
        when(gateway.execute(any(), any(), any(), eq("REFUND"), any()))
                .thenReturn(new RemoteResult("receipt", "REFUNDED", null, 3));
        var refund = service.quoteAction(created.orderId(), new ActionForm("REFUND", 0));
        assertEquals("0.33", refund.amount());
        assertEquals("0.05499989", refund.unitCharge());
        var result = service.confirm(refund.id());
        assertEquals("0.16", result.amount());
        assertEquals(3, result.quantity());
        assertEquals("0.16", service.order(created.orderId()).refundedAmount());
        service.confirm(refund.id());
        money("99.83");
        verify(gateway, times(1)).execute(any(), any(), any(), eq("REFUND"), any());
    }

    @Test
    void accumulatedRoundingNeverRefundsMoreThanActualPaidAmount() {
        var created = service.confirm(preciseDistanceQuote(3).id());
        when(gateway.execute(any(), any(), any(), eq("ADD_TIMES"), any()))
                .thenReturn(new RemoteResult("receipt", "ACTIVE", null, null));
        service.confirm(service.quoteAction(created.orderId(), new ActionForm("ADD_TIMES", 3)).id());
        when(gateway.refundRemaining(any(), any())).thenReturn(6);
        when(gateway.execute(any(), any(), any(), eq("REFUND"), any()))
                .thenReturn(new RemoteResult("receipt", "REFUNDED", null, 6));
        var refund = service.quoteAction(created.orderId(), new ActionForm("REFUND", 0));
        // Six units round to0.33, but two independently confirmed3-unit purchases paid only0.32.
        assertEquals("0.32", refund.amount());
        assertEquals("0.32", service.confirm(refund.id()).amount());
        service.confirm(refund.id());
        money("100.00");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger WHERE direction=1", Integer.class));
    }

    @Test
    void preciseUnknownOrderRetainsAndRestoresOnlyItsActualDebit() {
        when(gateway.execute(any(), any(), any(), eq("CREATE"), any()))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        var preview = preciseDistanceQuote(3);
        var unknown = service.confirm(preview.id());
        assertEquals("UNKNOWN", unknown.state());
        assertEquals("0.16", unknown.amount());
        money("99.84");
        service.confirm(preview.id());
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), any());
        var decision = new ResolveForm("NOT_ACCEPTED", null, null, "已核实订单未受理且无重复执行记录", true);
        assertThrows(BusinessException.class, () -> service.resolve(preview.id(), decision));
        auth(8, "api-provider:update", "payment:reconcile");
        assertEquals("NOT_ACCEPTED", service.resolve(preview.id(), decision).state());
        assertThrows(BusinessException.class, () -> service.resolve(preview.id(), decision));
        money("100.00");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }

    @Test
    void preciseManualRefundUsesEightDecimalsAndDoesNotSendRefundRequest() {
        var created = service.confirm(preciseDistanceQuote(6).id());
        when(gateway.sync(any(), any())).thenAnswer(call -> new RemoteResult(
                ((ServiceOrder) call.getArgument(1)).getExternalOrderNo(), "REFUND_REVIEW", 3, null));
        service.sync(created.orderId());
        clearInvocations(gateway);
        auth(8, "api-provider:update", "payment:reconcile");
        var preview = service.quoteRefundSettlement(created.orderId(), settlement(created.orderId(), 3));
        assertEquals("0.16", preview.amount());
        assertEquals("0.05499989", preview.unitCharge());
        service.confirmRefundSettlement(preview.id());
        service.confirmRefundSettlement(preview.id());
        money("99.83");
        verifyNoInteractions(gateway);
    }

    @Test
    void missingOperationPrecisionMigrationRollsBackQuoteInsteadOfSilentlyChangingPrice() {
        jdbc.execute("ALTER TABLE service_order_operation MODIFY COLUMN unit_charge DECIMAL(16,6) NOT NULL");
        assertThrows(BusinessException.class, () -> preciseDistanceQuote(3));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        money("100");
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void partialPrecisionMigrationCannotDebitOrDispatchAndSameQuoteWorksAfterFix() {
        jdbc.execute("ALTER TABLE service_order MODIFY COLUMN unit_charge DECIMAL(16,6) NOT NULL");
        var preview = preciseDistanceQuote(3);
        assertThrows(BusinessException.class, () -> service.confirm(preview.id()));
        assertEquals("READY", service.operation(preview.id()).state());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
        money("100");
        jdbc.execute("ALTER TABLE service_order MODIFY COLUMN unit_charge DECIMAL(18,8) NOT NULL");
        assertEquals("SUCCEEDED", service.confirm(preview.id()).state());
        money("99.84");
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), any());
    }

    @Test
    void legacyReadyQuoteKeepsOriginalAmountAndSixDecimalContract() {
        var preview = preciseDistanceQuote(3);
        jdbc.update("UPDATE service_order_operation SET unit_charge=0.055000,amount=0.17 WHERE id=?", preview.id());
        var legacy = service.confirm(preview.id());
        assertEquals("0.17", legacy.amount());
        assertEquals("0.05500000", legacy.unitCharge());
        assertEquals(0, new BigDecimal("0.055000").compareTo(orders.selectById(legacy.orderId()).getUnitCharge()));
        money("99.83");
    }

    @Test
    void legacyOrderAdditionsAndRefundsKeepRecordedPriceRatherThanReconstructingIt() {
        var preview = preciseDistanceQuote(3);
        // An old READY snapshot settles through the real ledger, without rewriting its contract.
        jdbc.update("UPDATE service_order_operation SET unit_charge=0.055000,amount=0.17 WHERE id=?", preview.id());
        var created = service.confirm(preview.id());
        jdbc.update("UPDATE service_product SET unit_price=9,version=version+1 WHERE id=1");
        when(gateway.execute(any(), any(), any(), eq("ADD_TIMES"), any()))
                .thenReturn(new RemoteResult("receipt", "ACTIVE", null, null));
        var addition = service.quoteAction(created.orderId(), new ActionForm("ADD_TIMES", 3));
        assertEquals("0.17", addition.amount());
        service.confirm(addition.id());
        when(gateway.refundRemaining(any(), any())).thenReturn(6);
        when(gateway.execute(any(), any(), any(), eq("REFUND"), any()))
                .thenReturn(new RemoteResult("receipt", "REFUNDED", null, 6));
        var refund = service.quoteAction(created.orderId(), new ActionForm("REFUND", 0));
        assertEquals("0.33", refund.amount());
        service.confirm(refund.id());
        money("99.99");
    }

    @Test
    void unrepresentableAdapterChargeIsRejectedBeforePersistingOrDispatching() {
        doReturn(new PreparedOrder(Map.of(), 3, new BigDecimal("0.11"), new BigDecimal("0.111"), "account"))
                .when(gateway).prepare(any(), any(), any());
        assertThrows(BusinessException.class, () -> preciseDistanceQuote(3));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation", Integer.class));
        money("100");
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void doubleRateBillableFactorAlsoKeepsItsExactEightDecimals() {
        doReturn(new PreparedOrder(Map.of(), 3, new BigDecimal("0.11"), new BigDecimal("0.22"), "account"))
                .when(gateway).prepare(any(), any(), any());
        var preview = preciseDistanceQuote("0.249999", "0.11", 3);
        assertEquals("0.05499978", preview.unitCharge());
        assertEquals("0.16", preview.amount());
    }

    @Test
    void tinyExactOrderCanReachOneCentWithoutRoundingItsUnitDownPrematurely() {
        var preview = preciseDistanceQuote("0.007143", "0.10", 7);
        assertEquals("0.00071430", preview.unitCharge());
        assertEquals("0.01", preview.amount());
        service.confirm(preview.id());
        money("99.99");
    }

    @Test
    void tinyOrderBelowHalfCentCannotBecomeChargeableThroughDoubleRounding() {
        assertThrows(BusinessException.class, () -> preciseDistanceQuote("0.049999", "0.10", 1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation", Integer.class));
        money("100");
    }

    @Test
    void concurrentPreciseConfirmationStillCreatesOnlyOneOrderAndOneDebit() throws Exception {
        var preview = preciseDistanceQuote(3);
        var gate = new CountDownLatch(1);
        Callable<QuoteView> attempt = () -> {
            auth(7, "ROLE_USER");
            assertTrue(gate.await(10, TimeUnit.SECONDS));
            return service.confirm(preview.id());
        };
        var first = threads.submit(attempt);
        var second = threads.submit(attempt);
        gate.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        money("99.84");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), any());
    }

    Long heishaProduct(String mode) {
        return heishaProduct(mode, "1");
    }

    Long heishaProduct(String mode, String sku) {
        jdbc.update("DELETE FROM service_product WHERE id NOT IN (SELECT product_id FROM service_order)");
        provider.setProviderType("heisha");
        PluginReadOnlyConnector heishaCatalog = mock(PluginReadOnlyConnector.class);
        when(heishaCatalog.getProviderType()).thenReturn("heisha");
        when(heishaCatalog.fetchCatalog(any(), any())).thenReturn(
                List.of(new PluginProduct(sku, "黑鲨", new BigDecimal("0.10"), "元/公里")));
        ReflectionTestUtils.setField(service, "catalogs", new PluginConnectorRegistry(List.of(heishaCatalog)));
        doAnswer(a -> {
            OrderForm input = a.getArgument(2);
            return new PreparedOrder(Map.of("phone", "13800138000", "password", "local-password-secret",
                    "plan_option_id", "plan-1", "run_time", "08:00", "face_token", "discard-face-secret",
                    "run_preflight_token", "discard-preflight-secret"), input.quantity(), input.distance(), input.distance(), "13***00");
        }).when(gateway).prepare(any(), any(), any());
        auth(7, "api-provider:update");
        Long providerId = "SELF_OPERATED".equals(mode) ? null : 9L;
        String providerType = "SELF_OPERATED".equals(mode) ? "heisha" : null;
        Long id = service.saveProduct(null, new ProductCommand(providerId, "default", sku, "黑鲨", "",
                new BigDecimal("0.25"), true, mode, null, null, providerType)).id();
        auth(7, "ROLE_USER");
        if ("SELF_OPERATED".equals(mode))
            clearInvocations(providerService, providerMapper, heishaCatalog, gateway, commerceAccounts);
        return id;
    }

    Long heishaFaceProduct() {
        jdbc.update("DELETE FROM service_product");
        auth(7, "api-provider:update");
        Long id = service.saveProduct(null, new ProductCommand(null, "default", "3", "黑鲨资格服务",
                "", new BigDecimal("0.25"), true, "SELF_OPERATED", null, null, "heisha")).id();
        auth(7, "ROLE_USER");
        clearInvocations(providerService, providerMapper, catalog, gateway, commerceAccounts);
        return id;
    }

    OrderForm faceLocalForm(String materialDraftId) {
        return new OrderForm(10, new BigDecimal("2"), Map.of(
                "phone", "13800138000", "password", "local-password-secret",
                "run_time", "08:00", "school_name", "测试大学"), List.of(), true,
                null, null, true, materialDraftId);
    }

    @Test
    void selfOperatedProductValidationPricingAndHistoricalSnapshot() {
        assertEquals("UPSTREAM", products.selectById(1L).getFulfillmentMode());
        auth(7, "api-provider:update");
        assertThrows(BusinessException.class, () -> service.saveProduct(null,
                new ProductCommand(9L, "default", "1", "非法", "", BigDecimal.ONE, true, "SELF_OPERATED", null, null)));
        Long id = heishaProduct("SELF_OPERATED");
        // A self-operated sale does not need a matching catalog price.
        ReflectionTestUtils.setField(service, "catalogs", new PluginConnectorRegistry(List.of()));
        var quote = service.quote(id, localForm());
        assertEquals("5.00", quote.amount());
        var created = service.confirm(quote.id());
        auth(7, "api-provider:update");
        var changed = service.saveProduct(id, new ProductCommand(null, "default", "1", "黑鲨", "", BigDecimal.ONE,
                false, "SELF_OPERATED", 0L, null, "heisha"));
        assertEquals(1L, changed.version());
        assertEquals("SELF_OPERATED", changed.fulfillmentMode());
        auth(7, "ROLE_USER");
        assertEquals("SELF_OPERATED", service.order(created.orderId()).fulfillmentMode());
        assertEquals("PENDING", service.sync(created.orderId()).status());
        verify(gateway, never()).sync(any(), any());
    }

    @Test
    void heishaProductCanSwitchFulfillmentModeWithoutChangingSkuIdentity() {
        Long id = heishaProduct("SELF_OPERATED");
        auth(7, "api-provider:update");
        var upstream = service.saveProduct(id, new ProductCommand(9L, "default", "1", "黑鲨", "",
                new BigDecimal("0.25"), true, "UPSTREAM", 0L, null, "heisha"));
        assertEquals(9L, upstream.providerId());
        assertEquals("UPSTREAM", upstream.fulfillmentMode());
        assertEquals(1L, upstream.version());

        var local = service.saveProduct(id, new ProductCommand(null, "default", "1", "黑鲨", "",
                new BigDecimal("0.25"), true, "SELF_OPERATED", 1L, null, "heisha"));
        assertNull(local.providerId());
        assertEquals("SELF_OPERATED", local.fulfillmentMode());
        assertEquals(2L, local.version());
    }

    @Test
    void concurrentSelfOperatedConfirmCreatesOneEncryptedPayloadAndOneDebit() throws Exception {
        var preview = service.quote(heishaProduct("SELF_OPERATED"), localForm());
        CountDownLatch start = new CountDownLatch(1);
        Callable<QuoteView> confirm = () -> { auth(7, "ROLE_USER"); start.await(); return service.confirm(preview.id()); };
        var first = threads.submit(confirm); var second = threads.submit(confirm); start.countDown();
        assertEquals("SUCCEEDED", first.get(10, TimeUnit.SECONDS).state());
        assertEquals("SUCCEEDED", second.get(10, TimeUnit.SECONDS).state());
        service.confirm(preview.id());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_fulfillment", Integer.class));
        money("95.00");
        var view = service.order(service.operation(preview.id()).orderId());
        assertEquals("PENDING", view.status()); assertEquals("SELF_OPERATED", view.fulfillmentMode());
        var stored = orders.selectById(view.id());
        assertNull(stored.getExternalOrderNo()); assertNull(stored.getPendingOperationId());
        assertNull(operations.selectById(preview.id()).getPayloadEncrypted());
        String encrypted = jdbc.queryForObject("SELECT payload_encrypted FROM service_order_fulfillment", String.class);
        assertFalse(encrypted.contains("local-password-secret"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertFalse(json.writeValueAsString(view).contains("local-password-secret"));
        assertFalse(json.writeValueAsString(stored).contains("local-password-secret"));
        auth(8, "service-order:fulfill");
        var details = service.fulfillmentDetails(view.id());
        assertEquals("local-password-secret", details.fields().get("password"));
        assertFalse(details.fields().containsKey("face_token"));
        assertFalse(details.fields().containsKey("run_preflight_token"));
        assertFalse(details.toString().contains("local-password-secret"));
        var securityAudit = (com.course.platform.application.service.security.SecurityAuditService)
                ReflectionTestUtils.getField(service, "securityAudit");
        verify(securityAudit).recordFulfillmentRead(8L, view.id());
        doThrow(new BusinessException("审计不可用")).when(securityAudit).recordFulfillmentRead(8L, view.id());
        assertThrows(BusinessException.class, () -> service.fulfillmentDetails(view.id()));
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void selfOperatedFaceCheckoutUsesLocalDraftWithoutOfficialSessionOrDispatch() {
        jdbc.update("DELETE FROM service_product");
        auth(7, "api-provider:update");
        var product = service.saveProduct(null, new ProductCommand(null, "default", "3",
                "黑鲨商品 3", "", new BigDecimal("0.25"), true, "SELF_OPERATED", null, null,
                "heisha"));
        auth(7, "ROLE_USER");
        clearInvocations(providerService, providerMapper, catalog, gateway, commerceAccounts);
        assertThrows(BusinessException.class, () -> service.quote(product.id(), new OrderForm(
                1, new BigDecimal("2"), Map.of("phone", "13800138000", "password", "private-password",
                        "run_time", "08:00"), List.of(), true, null, null, true, null)));
        var draft = service.createMaterialDraft(new MaterialDraftForm(
                product.id(), product.version(), pngDataUrl(), true));
        var quote = service.quote(product.id(), new OrderForm(
                1, new BigDecimal("2"), Map.of("phone", "13800138000", "password", "private-password",
                        "run_time", "08:00"), List.of(), true, null, null, true, draft.id()));
        var confirmed = service.confirm(quote.id());
        assertEquals("SUCCEEDED", confirmed.state());
        assertEquals("PENDING", service.order(confirmed.orderId()).status());
        assertEquals("USED", jdbc.queryForObject(
                "SELECT state FROM service_fulfillment_material_draft WHERE id=?", String.class, draft.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_order_fulfillment_asset WHERE order_id=?", Integer.class,
                confirmed.orderId()));
        String assetId = jdbc.queryForObject(
                "SELECT id FROM service_order_fulfillment_asset WHERE order_id=?", String.class,
                confirmed.orderId());
        auth(8, "service-order:fulfill");
        var fields = service.fulfillmentDetails(confirmed.orderId()).fields();
        assertEquals("private-password", fields.get("password"));
        assertFalse(fields.containsKey("face_token"));
        assertThrows(BusinessException.class,
                () -> service.fulfillmentAsset(confirmed.orderId(), assetId),
                "fulfillment permission alone must not grant biometric image access");
        auth(8, "service-order:fulfill", "service-order:biometric");
        byte[] privateImage = service.fulfillmentAsset(confirmed.orderId(), assetId);
        assertEquals((byte) 0x89, privateImage[0]);
        var securityAudit = (com.course.platform.application.service.security.SecurityAuditService)
                ReflectionTestUtils.getField(service, "securityAudit");
        verify(securityAudit).recordFulfillmentAssetRead(8L, confirmed.orderId(), assetId, "FACE_QUALIFICATION");
        doThrow(new BusinessException("无法记录资料访问，请稍后重试"))
                .when(securityAudit).recordFulfillmentAssetRead(8L, confirmed.orderId(), assetId, "FACE_QUALIFICATION");
        var auditFailure = assertThrows(BusinessException.class,
                () -> service.fulfillmentAsset(confirmed.orderId(), assetId));
        assertFalse(auditFailure.toString().contains("private-audit-storage-detail"));
        assertNull(auditFailure.getCause());
        verify(providerService, never()).loadDecrypted(anyLong());
        verify(providerMapper, never()).selectById(anyLong());
        verify(catalog, never()).fetchCatalog(any(), any());
        verify(gateway, never()).lookup(any(), any(), anyMap());
        verify(gateway, never()).prepare(any(), any(), any());
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
        verify(gateway, never()).sync(any(), any());
        verifyNoInteractions(commerceAccounts);
        money("99.50");
    }

    @Test
    void selfOperatedOrderMaterialDraftSupportsNeedsInfoAfterProductIsDisabledAndVersionChanges() {
        Long productId = heishaFaceProduct();
        var initialDraft = service.createMaterialDraft(new MaterialDraftForm(productId,
                products.selectById(productId).getVersion(), pngDataUrl(), true));
        var created = service.confirm(service.quote(productId, faceLocalForm(initialDraft.id())).id());
        String orderId = created.orderId();

        auth(7, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.createOrderMaterialDraft(orderId,
                new OrderMaterialDraftForm(pngDataUrl(), true)),
                "an order cannot receive supplement material before NEEDS_INFO");
        auth(8, "service-order:fulfill");
        service.verifyFulfillment(orderId, new VerificationForm("NEEDS_INFO", 0L, 0L,
                "请补交清晰的人脸资格材料", null, null, null, null));

        auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.createOrderMaterialDraft(orderId,
                new OrderMaterialDraftForm(pngDataUrl(), true)),
                "another user cannot create materials for this order");

        auth(7, "api-provider:update");
        service.saveProduct(productId, new ProductCommand(null, "default", "3", "黑鲨资格服务", "",
                new BigDecimal("0.25"), false, "SELF_OPERATED", 0L, null, "heisha"));
        assertEquals(1L, products.selectById(productId).getVersion());
        auth(7, "ROLE_USER");
        var draft = service.createOrderMaterialDraft(orderId, new OrderMaterialDraftForm(pngDataUrl(), true));

        var refreshed = service.updateFulfillmentMaterials(orderId, new MaterialUpdateForm(
                0L, 1L, "replacement-password", draft.id(), true, "已按要求重新提交材料"));

        assertEquals("PENDING", refreshed.status());
        assertEquals("PENDING", refreshed.verificationStatus());
        assertTrue(refreshed.faceMaterialPresent());
        assertEquals("USED", jdbc.queryForObject(
                "SELECT state FROM service_fulfillment_material_draft WHERE id=?", String.class, draft.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_order_fulfillment_asset WHERE order_id=? AND content_encrypted IS NOT NULL",
                Integer.class, orderId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT material_version FROM service_order_fulfillment WHERE order_id=?", Long.class, orderId));

        auth(8, "service-order:fulfill");
        var verified = service.verifyFulfillment(orderId, new VerificationForm("VERIFY", 0L, 2L,
                "材料已核实", "plan-1", "每日计划", "fence-1", "校区范围"));
        assertEquals("VERIFIED", verified.verificationStatus());
        assertEquals("ACTIVE", service.manageFulfillment(orderId,
                new LocalFulfillmentForm("START", 0L, null, null)).status());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"1", "2"})
    void orderMaterialDraftRequiresOwnedNeedsInfoFaceOrderAndSeparateAuthorization(String sku) {
            Long productId = heishaProduct("SELF_OPERATED", sku);
            var created = service.confirm(service.quote(productId, localForm()).id());
            String orderId = created.orderId();
            auth(8, "service-order:fulfill");
            service.verifyFulfillment(orderId, new VerificationForm("NEEDS_INFO", 0L, 0L,
                    "请补充信息", null, null, null, null));

            auth(7, "ROLE_USER");
            assertThrows(BusinessException.class, () -> service.createOrderMaterialDraft(orderId,
                    new OrderMaterialDraftForm(pngDataUrl(), false)));
            assertThrows(BusinessException.class, () -> service.createOrderMaterialDraft(orderId,
                    new OrderMaterialDraftForm(pngDataUrl(), true)),
                    "SKU 1/2 orders do not accept qualification face images");
    }

    @Test
    void selfOperatedFailuresRollbackDebitOrderAndPayloadAndRejectStaleProductQuote() {
        Long id = heishaProduct("SELF_OPERATED");
        var quote = service.quote(id, localForm());
        jdbc.update("UPDATE sys_user SET balance=0 WHERE id=7");
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_fulfillment", Integer.class));
        jdbc.update("UPDATE sys_user SET balance=100 WHERE id=7");
        var mapper = mock(ServiceOrderFulfillmentMapper.class);
        when(mapper.insert(any(ServiceOrderFulfillment.class))).thenThrow(new IllegalStateException("simulated persistence failure"));
        Object real = ReflectionTestUtils.getField(service, "fulfillmentMapper");
        ReflectionTestUtils.setField(service, "fulfillmentMapper", mapper);
        assertThrows(IllegalStateException.class, () -> service.confirm(quote.id()));
        money("100");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals("READY", service.operation(quote.id()).state());
        ReflectionTestUtils.setField(service, "fulfillmentMapper", real);
        auth(7, "api-provider:update");
        service.saveProduct(id, new ProductCommand(null, "default", "1", "黑鲨", "", BigDecimal.ONE,
                true, "SELF_OPERATED", 0L, null, "heisha"));
        auth(7, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        money("100");
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void upstreamHeishaStillExecutesAndSyncs() {
        var quote = service.quote(heishaProduct("UPSTREAM"), form());
        service.confirm(quote.id()); service.confirm(quote.id());
        var order = orders.selectById(service.operation(quote.id()).orderId());
        assertEquals("UPSTREAM", order.getFulfillmentMode()); assertNotNull(order.getExternalOrderNo());
        auth(7, "api-provider:update");
        service.saveProduct(order.getProductId(), new ProductCommand(9L, "default", "1", "黑鲨", "",
                BigDecimal.ONE, true, "UPSTREAM", 0L, null, "heisha"));
        auth(7, "ROLE_USER");
        assertEquals("UPSTREAM", service.order(order.getId()).fulfillmentMode());
        when(gateway.sync(any(), any())).thenReturn(new RemoteResult(order.getExternalOrderNo(), "ACTIVE", 2, null));
        assertEquals(2, service.sync(order.getId()).completed());
        verify(gateway, times(1)).execute(any(), any(), any(), eq("CREATE"), any());
        verify(gateway, times(1)).sync(any(), any());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_fulfillment", Integer.class));
    }

    @Test
    void selfOperatedWorkerIsExcludedInSqlEvenWithRemoteIdentifier() {
        Long productId = heishaProduct("SELF_OPERATED");
        var created = service.confirm(service.quote(productId, localForm()).id());
        jdbc.update("UPDATE service_order SET status='ACTIVE', external_order_no='legacy-invalid' WHERE id=?", created.orderId());
        ReflectionTestUtils.setField(service, "statusRefreshEnabled", true);
        assertFalse(orders.dueStatusChecks(ServiceTime.now(), 10).contains(created.orderId()));
        assertEquals(0, service.refreshDueStatuses());
        service.sync(created.orderId());
        verify(gateway, never()).sync(any(), any());
    }

    @Test
    void selfOperatedLifecycleAuthorizationProgressAuditAndTerminalSafety() throws Exception {
        var created = service.confirm(service.quote(heishaProduct("SELF_OPERATED"), localForm()).id());
        String id = created.orderId();
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("START", 0L, null, null)));
        assertThrows(BusinessException.class, () -> service.fulfillmentDetails(id));
        auth(8, "api-provider:update", "payment:reconcile");
        assertThrows(BusinessException.class, () -> service.fulfillmentDetails(id));
        auth(8, "service-order:fulfill");
        service.verifyFulfillment(id, new VerificationForm("VERIFY", 0L, 0L, "核验完成",
                null, null, null, null));
        service.manageFulfillment(id, new LocalFulfillmentForm("START", 0L, null, null));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("COMPLETE", 0L, null, null)));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("ATTENTION", 1L, null, " ")));
        service.manageFulfillment(id, new LocalFulfillmentForm("ATTENTION", 1L, null, "待检查"));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("RESUME", 2L, null, null)));
        service.manageFulfillment(id, new LocalFulfillmentForm("RESUME", 2L, null, "已检查"));
        service.manageFulfillment(id, new LocalFulfillmentForm("PROGRESS", 3L, 2, null));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("PROGRESS", 4L, 1, null)));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("PROGRESS", 4L, 11, null)));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("ATTENTION", 4L, null, "local-password-secret")));
        service.manageFulfillment(id, new LocalFulfillmentForm("ATTENTION", 4L, null, "需要复查"));
        service.manageFulfillment(id, new LocalFulfillmentForm("RESUME", 5L, null, "已恢复"));
        var completed = service.manageFulfillment(id, new LocalFulfillmentForm("COMPLETE", 6L, null, null));
        assertEquals("COMPLETED", completed.status()); assertEquals(completed.quantity(), completed.completed());
        assertThrows(BusinessException.class, () -> service.manageFulfillment(id, new LocalFulfillmentForm("COMPLETE", 7L, null, null)));
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation WHERE order_id=?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation WHERE order_id=? AND resolved_by=8 AND previous_status IS NOT NULL AND resulting_status IS NOT NULL AND completed_snapshot IS NOT NULL", Integer.class, id));
        auth(7, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.quoteAction(id, new ActionForm("LOCAL_START", 0)));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(service.events(id)).contains("local-password-secret"));
    }

    @Test
    void resumeCannotBypassPendingQualification() {
        var created = service.confirm(service.quote(heishaProduct("SELF_OPERATED"), localForm()).id());
        String id = created.orderId();
        auth(8, "service-order:fulfill");

        service.manageFulfillment(id, new LocalFulfillmentForm("ATTENTION", 0L, null, "等待核验"));
        var resumed = service.manageFulfillment(id,
                new LocalFulfillmentForm("RESUME", 1L, null, "继续等待核验"));

        assertEquals("PENDING", resumed.status());
        assertEquals("PENDING", resumed.verificationStatus());
        assertThrows(BusinessException.class,
                () -> service.manageFulfillment(id, new LocalFulfillmentForm("START", 2L, null, null)));
        service.verifyFulfillment(id, new VerificationForm("VERIFY", 2L, 0L, "核验完成",
                null, null, null, null));
        assertEquals("ACTIVE", service.manageFulfillment(id,
                new LocalFulfillmentForm("START", 2L, null, null)).status());
    }

    @Test
    void localCancelRefundIsFullLedgerBackedIdempotentAndRejectDoesNotRefund() {
        Long productId = heishaProduct("SELF_OPERATED");
        var created = service.confirm(service.quote(productId, localForm()).id());
        auth(8, "service-order:fulfill");
        assertThrows(BusinessException.class, () -> service.manageFulfillment(created.orderId(),
                new LocalFulfillmentForm("CANCEL_REFUND", 0L, null, " ")));
        var refunded = service.manageFulfillment(created.orderId(),
                new LocalFulfillmentForm("CANCEL_REFUND", 0L, null, "用户在开始前申请取消"));
        assertEquals("REFUNDED", refunded.status());
        assertEquals("5.00", refunded.refundedAmount());
        money("100");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_ledger WHERE biz_type=? AND biz_no=? AND direction=1",
                Integer.class, AccountLedgerServiceImpl.BIZ_REFUND, "SERVICE_LOCAL:" + created.orderId()));
        assertThrows(BusinessException.class, () -> service.manageFulfillment(created.orderId(),
                new LocalFulfillmentForm("CANCEL_REFUND", 0L, null, "重复取消")));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_ledger WHERE biz_type=? AND biz_no=? AND direction=1",
                Integer.class, AccountLedgerServiceImpl.BIZ_REFUND, "SERVICE_LOCAL:" + created.orderId()));

        auth(7, "ROLE_USER");
        var rejectedOrder = service.confirm(service.quote(productId, localForm()).id());
        auth(8, "service-order:fulfill");
        var rejected = service.verifyFulfillment(rejectedOrder.orderId(), new VerificationForm(
                "REJECT", 0L, 0L, "资格条件不符合", null, null, null, null));
        assertEquals("REJECTED", rejected.verificationStatus());
        assertEquals("PENDING", rejected.status());
        assertEquals("0.00", rejected.refundedAmount());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_ledger WHERE biz_type=? AND direction=1",
                Integer.class, AccountLedgerServiceImpl.BIZ_REFUND));
        verify(gateway, never()).execute(any(), any(), any(), any(), any());
        verify(gateway, never()).sync(any(), any());
        verify(providerService, never()).loadDecrypted(anyLong());
    }

    @Test
    void selfOperatedConcurrentProgressOnlyOneVersionWins() throws Exception {
        var created = service.confirm(service.quote(heishaProduct("SELF_OPERATED"), localForm()).id());
        auth(8, "service-order:fulfill");
        service.verifyFulfillment(created.orderId(), new VerificationForm("VERIFY", 0L, 0L, null,
                null, null, null, null));
        service.manageFulfillment(created.orderId(), new LocalFulfillmentForm("START", 0L, null, null));
        service.manageFulfillment(created.orderId(), new LocalFulfillmentForm("PROGRESS", 1L, 2, null));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> firstUpdate = () -> localProgressAttempt(created.orderId(), 3, start);
        Callable<Boolean> secondUpdate = () -> localProgressAttempt(created.orderId(), 4, start);
        var first = threads.submit(firstUpdate); var second = threads.submit(secondUpdate); start.countDown();
        assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertEquals(3L, orders.selectById(created.orderId()).getVersion());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation WHERE action='LOCAL_PROGRESS'", Integer.class));
    }

    boolean localProgressAttempt(String id, int completed, CountDownLatch start) throws Exception {
        auth(8, "service-order:fulfill"); start.await();
        try { service.manageFulfillment(id, new LocalFulfillmentForm("PROGRESS", 2L, completed, null)); return true; }
        catch (BusinessException expected) { assertFalse(expected.getMessage().contains("local-password-secret")); return false; }
    }

}
