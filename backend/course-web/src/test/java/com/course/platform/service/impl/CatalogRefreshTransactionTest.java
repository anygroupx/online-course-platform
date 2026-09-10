package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.catalogrefresh.CatalogRefreshTypes.*;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

class CatalogRefreshTransactionTest {
    JdbcTemplate jdbc;
    CatalogRefreshServiceImpl service;
    CoursePriceRefreshMapper batches;
    CoursePlatformMapper platforms;
    PlatformCategoryMapper categories;
    PlatformDockingService docking;
    List<PlatformItem> remote;
    ValidatorFactory validators;
    ExecutorService threads;

    @BeforeEach
    void setup() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:refresh_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        String schema = Files.readString(root.resolve("database/schema.sql"));
        for (String table : List.of("api_provider", "course_platform")) {
            var m =
                    Pattern.compile("CREATE TABLE `" + table + "` \\(.*?;", Pattern.DOTALL)
                            .matcher(schema);
            assertTrue(m.find());
            jdbc.execute(
                    m.group()
                            .replace("`idx_status`", "`" + table + "_idx_status`")
                            .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4.*?;", ";"));
        }
        jdbc.execute("ALTER TABLE course_platform ADD COLUMN category_id BIGINT");
        jdbc.execute(
                "CREATE TABLE platform_category("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100) NOT NULL,"
                        + "sort_order INT DEFAULT 0,status TINYINT DEFAULT 1,"
                        + "remote_category_id VARCHAR(50),remote_api_provider_id BIGINT,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP)" );
        String localizedMigration =
                Files.readString(
                                root.resolve(
                                        "database/migrations/029_localized_display_and_category_multiplier.sql"))
                        .replaceAll("(?m)^--.*$", "");
        for (String sql : localizedMigration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        String migration =
                Files.readString(
                                root.resolve(
                                        "database/migrations/024_existing_course_price_refresh.sql"))
                        .replaceAll("(?m)^--.*$", "")
                        .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : migration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.update(
                "INSERT INTO"
                    + " api_provider(id,provider_type,name,status,config_version,verified_at,balance,api_key)"
                    + " VALUES(9,'27','fixture',1,2,CURRENT_TIMESTAMP,100,'private-encrypted-key'),(10,'27','other"
                    + " fixture',1,2,CURRENT_TIMESTAMP,100,'other-key')");
        for (long id : List.of(1L, 2L, 3L))
            jdbc.update(
                    "INSERT INTO"
                        + " course_platform(id,name,dock_param,query_param,dock_api_id,query_api_id,base_price,description,status,sort_order,category_id,rate_type,password_rule,password_enabled,is_self_operated)"
                        + " VALUES(?,?,?,'query-original',?,10,2.50,'local"
                        + " description',0,27,88,'ADD','original-rule',1,0)",
                    id,
                    "Local " + id,
                    id == 2 ? "B" : "A",
                    id == 3 ? 10 : 9);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (var type :
                List.of(
                        CoursePriceRefreshMapper.class,
                        CoursePlatformMapper.class,
                        PlatformCategoryMapper.class,
                        ApiProviderMapper.class)) config.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var sql = new SqlSessionTemplate(factory.getObject());
        batches = sql.getMapper(CoursePriceRefreshMapper.class);
        platforms = sql.getMapper(CoursePlatformMapper.class);
        categories = sql.getMapper(PlatformCategoryMapper.class);
        docking = mock(PlatformDockingService.class);
        remote =
                new ArrayList<>(
                        List.of(
                                item("A", "1.235", "new description", "1"),
                                item("B", "2.5", null, "2"),
                                item("C", "3", "not imported", "1")));
        when(docking.fetchProviderProducts(eq(9L), any()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "No supplier HTTP while holding SQL transactions");
                            return new ArrayList<>(remote);
                        });
        validators = Validation.buildDefaultValidatorFactory();
        service =
                new CatalogRefreshServiceImpl(
                        batches,
                        platforms,
                        categories,
                        sql.getMapper(ApiProviderMapper.class),
                        docking,
                        new DataSourceTransactionManager(ds),
                        validators.getValidator());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "cryptoSecret", "price-refresh-test-master-key");
        auth(7, "platform:update");
        threads = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (validators != null) validators.close();
        if (threads != null) threads.shutdownNow();
    }

    void auth(long id, String... permissions) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                id,
                                null,
                                Arrays.stream(permissions)
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()));
    }

    PlatformItem item(String id, String price, String content, String category) {
        return PlatformItem.builder()
                .id(id)
                .name("Upstream name must not replace local")
                .price(new BigDecimal(price))
                .content(content)
                .categoryId(category)
                .build();
    }

    PreviewForm form() {
        return new PreviewForm(
                9L, "ALL_EXISTING", new BigDecimal("1.2"), null, List.of(), List.of());
    }

    View preview() {
        return service.preview(form());
    }

    View confirm(String id) {
        return service.confirm(id, new ConfirmForm(true));
    }

    @Test
    void localizedMigrationAddsNullableColumnsInMysqlCompatibleH2() {
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                                + " WHERE TABLE_NAME='COURSE_PLATFORM' AND COLUMN_NAME='DISPLAY_NAME'"
                                + " AND IS_NULLABLE='YES'",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
                                + " WHERE TABLE_NAME='PLATFORM_CATEGORY' AND COLUMN_NAME='PRICE_MULTIPLIER'"
                                + " AND IS_NULLABLE='YES'",
                        Integer.class));
    }

    @Test
    void categoryMultiplierOverridesGlobalMultiplierDuringPreviewAndApply() {
        jdbc.update(
                "INSERT INTO platform_category(id,name,price_multiplier) VALUES(88,'本地分类',2.00)");
        jdbc.update("UPDATE course_platform SET display_name='本地显示名' WHERE id=1");

        View view = preview();

        assertEquals("2.47", view.plan().rows().get(0).newPrice());
        assertEquals("5.00", view.plan().rows().get(1).newPrice());
        confirm(view.id());
        prices("2.47", "5.00");
        assertEquals("本地显示名", platforms.selectById(1L).getDisplayName());
        assertEquals("Local 1", platforms.selectById(1L).getName());
    }

    void prices(String a, String b) {
        assertEquals(0, new BigDecimal(a).compareTo(platforms.selectById(1L).getBasePrice()));
        assertEquals(0, new BigDecimal(b).compareTo(platforms.selectById(2L).getBasePrice()));
    }

    @Test
    void previewIsReadOnlyAndRoundsExactlyWithoutCreatingMissingCourses() {
        var v = preview();
        assertEquals("READY", v.state());
        assertEquals(2, v.plan().rows().size());
        assertEquals("1.48", v.plan().rows().get(0).newPrice());
        assertEquals(1, v.plan().notImported());
        prices("2.50", "2.50");
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM course_platform", Integer.class));
        assertTrue(SecretCrypto.isEncrypted(batches.selectById(v.id()).getPlanEncrypted()));
        assertFalse(batches.selectById(v.id()).toString().contains("new description"));
    }

    @Test
    void applyWritesOnlyPriceAndProvidedDescriptionAndPreservesAllOtherFields() {
        var p = preview();
        var before = platforms.selectById(1L);
        var done = confirm(p.id());
        assertEquals("APPLIED", done.state());
        prices("1.48", "3.00");
        var after = platforms.selectById(1L);
        assertEquals(before.getName(), after.getName());
        assertEquals(before.getCategoryId(), after.getCategoryId());
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getDockParam(), after.getDockParam());
        assertEquals(before.getQueryParam(), after.getQueryParam());
        assertEquals(before.getQueryApiId(), after.getQueryApiId());
        assertEquals(before.getRateType(), after.getRateType());
        assertEquals(before.getPasswordRule(), after.getPasswordRule());
        assertEquals(before.getPasswordEnabled(), after.getPasswordEnabled());
        assertEquals(before.getSortOrder(), after.getSortOrder());
        assertEquals("new description", after.getDescription());
        assertEquals("local description", platforms.selectById(2L).getDescription());
        assertEquals(new BigDecimal("2.50"), platforms.selectById(3L).getBasePrice());
        assertEquals(
                new BigDecimal("100.00"),
                jdbc.queryForObject(
                        "SELECT balance FROM api_provider WHERE id=9", BigDecimal.class));
        verify(docking, times(1)).fetchProviderProducts(9L, null);
    }

    @Test
    void categoryAndExclusionsAreEnforcedLocallyEvenIfUpstreamIgnoresFilters() {
        var v =
                service.preview(
                        new PreviewForm(
                                9L, "ALL_EXISTING", BigDecimal.ONE, "1", List.of("2"), List.of()));
        assertEquals(1, v.plan().rows().size());
        assertEquals("A", v.plan().rows().get(0).remoteId());
        assertEquals(1, v.plan().excluded());
        verify(docking).fetchProviderProducts(9L, "1");
    }

    @Test
    void selectedOnlyModeReportsMissingAndNotImportedWithoutCreatingOrDeleting() {
        var v =
                service.preview(
                        new PreviewForm(
                                9L,
                                "SELECTED",
                                BigDecimal.ONE,
                                null,
                                List.of(),
                                List.of("A", "C", "missing")));
        assertEquals(1, v.plan().rows().size());
        assertEquals(1, v.plan().notImported());
        assertEquals(1, v.plan().selectedMissing());
        confirm(v.id());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM course_platform", Integer.class));
        prices("1.24", "2.50");
    }

    @Test
    void remoteAbsentContentPreservesLocalButExplicitEmptyContentClearsIt() {
        remote.set(0, item("A", "2.50", "", "1"));
        var v = preview();
        assertTrue(v.plan().rows().get(0).descriptionProvided());
        confirm(v.id());
        assertEquals("", platforms.selectById(1L).getDescription());
        assertEquals("local description", platforms.selectById(2L).getDescription());
    }

    @Test
    void duplicateRemoteIdentifierRejectsWholePreview() {
        remote.add(item("A", "50", "conflict", "1"));
        assertThrows(BusinessException.class, this::preview);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM course_price_refresh", Integer.class));
        prices("2.50", "2.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1E999", "0.00001", "99999999999999999999"})
    void invalidRemoteAmountsNeverCreateExecutablePreview(String value) {
        remote.set(0, item("A", value, "content", "1"));
        assertThrows(BusinessException.class, this::preview);
        prices("2.50", "2.50");
    }

    @Test
    void oversizedDescriptionIsRejectedWithoutTruncation() {
        remote.set(0, item("A", "1", "x".repeat(501), "1"));
        assertThrows(BusinessException.class, this::preview);
        prices("2.50", "2.50");
    }

    @Test
    void supplierConfigChangedDuringLookupInvalidatesPreview() {
        when(docking.fetchProviderProducts(eq(9L), any()))
                .thenAnswer(
                        a -> {
                            jdbc.update(
                                    "UPDATE api_provider SET config_version=config_version+1 WHERE"
                                            + " id=9");
                            return remote;
                        });
        assertThrows(BusinessException.class, this::preview);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM course_price_refresh", Integer.class));
    }

    @Test
    void differentOwnerOrMissingPermissionCannotReadOrApplyBatch() {
        var v = preview();
        auth(8, "platform:update");
        assertThrows(BusinessException.class, () -> service.get(v.id()));
        assertThrows(BusinessException.class, () -> confirm(v.id()));
        auth(7, "order:update");
        assertThrows(BusinessException.class, this::preview);
        assertThrows(BusinessException.class, () -> service.get(v.id()));
        prices("2.50", "2.50");
    }

    @Test
    void changedProviderOrLocalBindingMakesEntireBatchStale() {
        var v = preview();
        jdbc.update("UPDATE course_platform SET dock_param='replacement' WHERE id=2");
        assertEquals("STALE", confirm(v.id()).state());
        prices("2.50", "2.50");
    }

    @Test
    void concurrentLocalPriceEditCannotBeOverwrittenAndEarlierRowsStayUnchanged() {
        var v = preview();
        jdbc.update("UPDATE course_platform SET base_price=7.00 WHERE id=2");
        assertEquals("STALE", confirm(v.id()).state());
        prices("2.50", "7.00");
    }

    @Test
    void concurrentDescriptionEditCannotBeOverwritten() {
        var v = preview();
        jdbc.update("UPDATE course_platform SET description='another admin edit' WHERE id=1");
        assertEquals("STALE", confirm(v.id()).state());
        prices("2.50", "2.50");
    }

    @Test
    void unrelatedConcurrentNameCategoryAndStatusEditsArePreserved() {
        var v = preview();
        jdbc.update(
                "UPDATE course_platform SET name='new local name',category_id=99,status=1 WHERE"
                        + " id=1");
        assertEquals("APPLIED", confirm(v.id()).state());
        var p = platforms.selectById(1L);
        assertEquals("new local name", p.getName());
        assertEquals(99L, p.getCategoryId());
        assertEquals(1, p.getStatus());
    }

    @Test
    void providerDisableOrVersionChangeStopsAllLocalWrites() {
        var v = preview();
        jdbc.update("UPDATE api_provider SET status=0,config_version=config_version+1 WHERE id=9");
        assertEquals("STALE", confirm(v.id()).state());
        prices("2.50", "2.50");
    }

    @Test
    void expiredQuoteAndMissingConsentNeverApply() {
        var v = preview();
        assertThrows(
                BusinessException.class, () -> service.confirm(v.id(), new ConfirmForm(false)));
        jdbc.update(
                "UPDATE course_price_refresh SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                v.id());
        assertEquals("EXPIRED", service.get(v.id()).state());
        assertEquals("EXPIRED", confirm(v.id()).state());
        prices("2.50", "2.50");
    }

    @Test
    void originalRecordRecoversLostResponseAndRepeatedConfirmNeverReapplies() {
        var v = preview();
        confirm(v.id());
        jdbc.update("UPDATE course_platform SET base_price=9.00 WHERE id=1");
        assertEquals("APPLIED", service.get(v.id()).state());
        assertEquals("APPLIED", confirm(v.id()).state());
        prices("9.00", "3.00");
        verify(docking, times(1)).fetchProviderProducts(9L, null);
    }

    @Test
    void databaseFailureRollsBackAllRowsAndBatchState() {
        var v = preview();
        jdbc.execute(
                "ALTER TABLE course_platform ADD CONSTRAINT fixture_price_failure CHECK(id<>2 OR"
                        + " base_price=2.50)");
        assertThrows(RuntimeException.class, () -> confirm(v.id()));
        prices("2.50", "2.50");
        assertEquals("READY", service.get(v.id()).state());
    }

    @Test
    void simultaneousConfirmationsOnlyApplyOnce() throws Exception {
        var v = preview();
        var latch = new CountDownLatch(1);
        Callable<String> confirm =
                () -> {
                    auth(7, "platform:update");
                    latch.await();
                    try {
                        return confirm(v.id()).state();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
        var a = threads.submit(confirm);
        var b = threads.submit(confirm);
        latch.countDown();
        assertEquals("APPLIED", a.get(10, TimeUnit.SECONDS));
        assertEquals("APPLIED", b.get(10, TimeUnit.SECONDS));
        prices("1.48", "3.00");
        verify(docking, times(1)).fetchProviderProducts(9L, null);
    }

    @Test
    void featureDisabledStillAllowsReadOnlyRecovery() {
        var v = preview();
        ReflectionTestUtils.setField(service, "enabled", false);
        assertEquals("READY", service.get(v.id()).state());
        assertThrows(BusinessException.class, this::preview);
        assertThrows(BusinessException.class, () -> confirm(v.id()));
        prices("2.50", "2.50");
    }

    @Test
    void overFiveHundredMatchingRowsCannotSilentlyPartiallyUpdate() {
        for (long id = 4; id <= 503; id++)
            jdbc.update(
                    "INSERT INTO course_platform(id,name,dock_param,dock_api_id,base_price)"
                            + " VALUES(?,'fixture','A',9,2.50)",
                    id);
        assertThrows(BusinessException.class, this::preview);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM course_price_refresh", Integer.class));
    }

    @Test
    void invalidSelectionOrMultiplierIsRejectedBeforeSupplierLookup() {
        assertThrows(
                BusinessException.class,
                () ->
                        service.preview(
                                new PreviewForm(
                                        9L,
                                        "SELECTED",
                                        BigDecimal.ONE,
                                        null,
                                        List.of(),
                                        List.of())));
        assertThrows(
                BusinessException.class,
                () ->
                        service.preview(
                                new PreviewForm(
                                        9L,
                                        "ALL_EXISTING",
                                        BigDecimal.ONE,
                                        null,
                                        List.of(),
                                        List.of("A"))));
        assertThrows(
                BusinessException.class,
                () ->
                        service.preview(
                                new PreviewForm(
                                        9L,
                                        "ALL_EXISTING",
                                        new BigDecimal("1001"),
                                        null,
                                        List.of(),
                                        List.of())));
        assertThrows(
                BusinessException.class,
                () ->
                        service.preview(
                                new PreviewForm(
                                        9L,
                                        "ALL_EXISTING",
                                        new BigDecimal("1.00001"),
                                        null,
                                        List.of(),
                                        List.of())));
        verifyNoInteractions(docking);
    }

    @Test
    void everyExistingLocalBindingIsPreviewedAndUpdatedNotArbitrarilyTheFirst() {
        jdbc.update(
                "INSERT INTO course_platform(id,name,dock_param,dock_api_id,base_price)"
                    + " VALUES(4,'Another local A','A',9,5.00)");
        var v = preview();
        assertEquals(List.of(1L, 2L, 4L), v.plan().rows().stream().map(Row::localId).toList());
        confirm(v.id());
        assertEquals(new BigDecimal("1.48"), platforms.selectById(4L).getBasePrice());
        assertEquals("Another local A", platforms.selectById(4L).getName());
        assertEquals(new BigDecimal("2.50"), platforms.selectById(3L).getBasePrice());
    }

    @Test
    void applyUsesExactlyThePreviewedSnapshotAndDoesNotRefetchSupplierPrices() {
        var v = preview();
        remote.set(0, item("A", "900", "changed remotely after preview", "1"));
        confirm(v.id());
        prices("1.48", "3.00");
        verify(docking, times(1)).fetchProviderProducts(9L, null);
        assertEquals("new description", platforms.selectById(1L).getDescription());
    }

    @Test
    void failedLocalApplyCanOnlyBeRetriedByExplicitlyConfirmingTheSameBatch() {
        var v = preview();
        jdbc.execute(
                "ALTER TABLE course_platform ADD CONSTRAINT fixture_retry_failure CHECK(id<>2 OR"
                    + " base_price=2.50)");
        assertThrows(RuntimeException.class, () -> confirm(v.id()));
        assertEquals("READY", service.get(v.id()).state());
        jdbc.execute("ALTER TABLE course_platform DROP CONSTRAINT fixture_retry_failure");
        assertEquals("APPLIED", confirm(v.id()).state());
        prices("1.48", "3.00");
        verify(docking, times(1)).fetchProviderProducts(9L, null);
    }
}
