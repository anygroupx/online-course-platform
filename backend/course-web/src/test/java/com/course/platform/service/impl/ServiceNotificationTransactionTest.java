package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.course.platform.application.service.servicenotification.ServiceNotificationSender;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.domain.servicenotification.*;
import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;
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

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

class ServiceNotificationTransactionTest {
    static final String ORDER = "20c5c14d-2eba-4dd7-a023-52a49a3dcc6b",
            OTHER = "30c5c14d-2eba-4dd7-a023-52a49a3dcc6b",
            TOKEN = "privateShowdocKey0123456789abcdef";
    ServiceNotificationServiceImpl service;
    ServiceNotificationSender sender;
    JdbcTemplate jdbc;
    ServiceNotificationPreferenceMapper prefs;
    ServiceNotificationDeliveryMapper jobs;
    RateLimitService limiter;
    RateLimitProperties limits;
    AtomicReference<Message> last;
    ExecutorService threads;

    @BeforeEach
    void setup() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:notify_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                        "sa",
                        "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE api_provider(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO api_provider VALUES(9)");
        jdbc.execute(
                "CREATE TABLE sys_user(id BIGINT PRIMARY KEY,balance DECIMAL(14,2),status INT"
                        + " DEFAULT 1)");
        jdbc.update("INSERT INTO sys_user(id,balance) VALUES(7,100),(8,100)");
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        for (String file :
                List.of(
                        "018_native_service_orders.sql",
                        "019_internship_service_plans.sql",
                        "020_service_account_sessions.sql",
                        "029_native_service_price_precision.sql",
                        "030_native_service_status_refresh.sql",
                        "023_native_service_notifications.sql")) {
            String ddl =
                    Files.readString(root.resolve("database/migrations/" + file))
                            .replaceAll("(?m)^--.*$", "")
                            .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
            for (String stmt : ddl.split(";")) if (!stmt.isBlank()) jdbc.execute(stmt);
        }
        jdbc.update(
                "INSERT INTO"
                    + " service_product(id,provider_id,provider_type,project,remote_product_id,title,unit_price,create_time,update_time)"
                    + " VALUES(1,9,'sxdk_tw','xyb','xyb','fixture',0.25,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (var row : Map.of(ORDER, 7L, OTHER, 8L).entrySet())
            jdbc.update(
                    "INSERT INTO"
                        + " service_order(id,user_id,product_id,provider_id,provider_version,provider_identity,provider_type,project,remote_product_id,external_order_no,title,account_label,status,quantity,completed,unit_charge,paid_amount,create_time,update_time)"
                        + " VALUES(?,?,1,9,1,?,'sxdk_tw','xyb','xyb',?,'private-title','private-account','ACTIVE',10,0,0.25,2.50,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    row.getKey(),
                    row.getValue(),
                    "a".repeat(64),
                    "remote-" + row.getValue());
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper :
                List.of(
                        ServiceNotificationPreferenceMapper.class,
                        ServiceNotificationDeliveryMapper.class,
                        ServiceOrderMapper.class,
                        ServiceOperationMapper.class)) config.addMapper(mapper);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(interceptor);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var sql = new SqlSessionTemplate(factory.getObject());
        prefs = sql.getMapper(ServiceNotificationPreferenceMapper.class);
        jobs = sql.getMapper(ServiceNotificationDeliveryMapper.class);
        sender = mock(ServiceNotificationSender.class);
        last = new AtomicReference<>();
        when(sender.send(anyString(), any()))
                .thenAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "No SQL lock may span notification HTTP");
                            last.set(a.getArgument(1));
                            assertEquals(
                                    1,
                                    jdbc.queryForObject(
                                            "SELECT COUNT(*) FROM service_notification_delivery"
                                                    + " WHERE state='DISPATCHING'",
                                            Integer.class));
                            return Receipt.ACCEPTED;
                        });
        limiter = mock(RateLimitService.class);
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        limits = new RateLimitProperties();
        service =
                new ServiceNotificationServiceImpl(
                        prefs,
                        jobs,
                        sql.getMapper(ServiceOrderMapper.class),
                        sql.getMapper(ServiceOperationMapper.class),
                        sender,
                        limiter,
                        limits,
                        new DataSourceTransactionManager(ds));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "deliveryEnabled", true);
        ReflectionTestUtils.setField(service, "cryptoSecret", "notification-test-master-secret");
        auth(7);
        threads = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        threads.shutdownNow();
    }

    void auth(long uid) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                uid, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    SettingsView configure() {
        return service.configure(
                ORDER, new ConfigureForm(TOKEN, service.settings(ORDER).version(), true));
    }

    SettingsView challenge() {
        var p = configure();
        return service.challenge(ORDER, new VersionForm(p.version(), true));
    }

    String code() {
        var m = Pattern.compile("验证码：([0-9]{6})").matcher(last.get().content());
        assertTrue(m.find());
        return m.group(1);
    }

    SettingsView activate() {
        var p = challenge();
        return service.verify(ORDER, new VerifyForm(code(), p.version(), true));
    }

    String changed() {
        jdbc.update("UPDATE service_order SET status='PAUSED',version=version+1 WHERE id=?", ORDER);
        service.observe(ORDER);
        return jdbc.queryForObject(
                "SELECT id FROM service_notification_delivery WHERE kind='ORDER_UPDATE'",
                String.class);
    }

    void noMoney() {
        assertEquals(
                0,
                new java.math.BigDecimal("100")
                        .compareTo(
                                jdbc.queryForObject(
                                        "SELECT balance FROM sys_user WHERE id=7",
                                        java.math.BigDecimal.class)));
    }

    @Test
    void settingsAndConfigureAreLocalOnlyUntilExplicitReceiverChallenge() {
        assertFalse(service.settings(ORDER).configured());
        var p = configure();
        assertTrue(p.configured());
        assertFalse(p.enabled());
        assertFalse(p.verified());
        assertFalse(prefs.selectById(ORDER).getTokenEncrypted().contains(TOKEN));
        service.settings(ORDER);
        service.deliveries(ORDER, 1, 20);
        service.processBatch();
        verifyNoInteractions(sender);
        noMoney();
    }

    @Test
    void challengePersistsBeforeOneHttpCallAndNothingSensitiveReturnsInViews() {
        var p = challenge();
        assertTrue(p.canVerify());
        assertFalse(p.enabled());
        assertNotNull(prefs.selectById(ORDER).getCodeHash());
        assertNull(jobs.selectById(p.challengeDeliveryId()).getPayloadEncrypted());
        assertEquals("ACCEPTED", service.delivery(p.challengeDeliveryId()).state());
        assertFalse(p.toString().contains(TOKEN));
        assertFalse(service.deliveries(ORDER, 1, 20).toString().contains(code()));
        service.challenge(ORDER, new VersionForm(p.version(), true));
        verify(sender, times(1)).send(eq(TOKEN), any());
        noMoney();
    }

    @Test
    void receivedCodeIsRequiredBeforeUpdatesAreEnabled() {
        var p = challenge();
        jdbc.update("UPDATE service_order SET status='PAUSED' WHERE id=?", ORDER);
        service.processBatch();
        verify(sender, times(1)).send(anyString(), any());
        var result = service.verify(ORDER, new VerifyForm(code(), p.version(), true));
        assertTrue(result.enabled());
        assertTrue(result.verified());
        assertFalse(result.canVerify());
        assertNull(prefs.selectById(ORDER).getCodeHash());
        service.processBatch();
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void verificationFailuresCommitAndExpireAfterFiveAttempts() {
        var p = challenge();
        String wrong = code().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++)
            assertThrows(
                    BusinessException.class,
                    () -> service.verify(ORDER, new VerifyForm(wrong, p.version(), true)));
        assertEquals(5, prefs.selectById(ORDER).getVerifyAttempts());
        assertFalse(service.settings(ORDER).canVerify());
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(code(), p.version(), true)));
        assertFalse(service.settings(ORDER).enabled());
    }

    @Test
    void expiredProofOrMissingConsentCannotEnableNotifications() {
        var p = challenge();
        jdbc.update(
                "UPDATE service_notification_preference SET challenge_expires_at=? WHERE"
                        + " order_id=?",
                ServiceTime.now().minusSeconds(1),
                ORDER);
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(code(), p.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(code(), p.version(), false)));
        assertThrows(
                BusinessException.class,
                () -> service.configure(ORDER, new ConfigureForm(TOKEN, p.version(), false)));
    }

    @Test
    void lostChallengeResponseCanStillBeProvedByActualReceivedCodeWithoutResending() {
        doAnswer(
                        a -> {
                            last.set(a.getArgument(1));
                            throw new RuntimeException(TOKEN);
                        })
                .when(sender)
                .send(anyString(), any());
        var p = challenge();
        assertEquals("UNKNOWN", service.delivery(p.challengeDeliveryId()).state());
        service.challenge(ORDER, new VersionForm(p.version(), true));
        service.processBatch();
        verify(sender, times(1)).send(anyString(), any());
        assertTrue(service.verify(ORDER, new VerifyForm(code(), p.version(), true)).enabled());
    }

    @Test
    void otherOwnersCannotReadModifyOrSendEvenWithAdminLookingRole() {
        var p = challenge();
        auth(8);
        assertThrows(BusinessException.class, () -> service.settings(ORDER));
        assertThrows(
                BusinessException.class,
                () -> service.configure(ORDER, new ConfigureForm(TOKEN, p.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.challenge(ORDER, new VersionForm(p.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(code(), p.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.disconnect(ORDER, new VersionForm(p.version(), true)));
        assertThrows(BusinessException.class, () -> service.delivery(p.challengeDeliveryId()));
        assertThrows(BusinessException.class, () -> service.deliveries(ORDER, 1, 20));
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void observationQueuesOneSanitizedLatestStateWithoutTouchingAccountOrSupplier() {
        activate();
        clearInvocations(sender);
        String id = changed();
        service.observe(ORDER);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM service_notification_delivery WHERE"
                                + " kind='ORDER_UPDATE'",
                        Integer.class));
        assertEquals("READY", jobs.selectById(id).getState());
        verifyNoInteractions(sender);
        service.dispatch(id);
        assertEquals("ACCEPTED", jobs.selectById(id).getState());
        assertTrue(last.get().content().contains("服务已暂停"));
        for (String secret : List.of(TOKEN, "private-title", "private-account", "remote-7"))
            assertFalse(last.get().content().contains(secret));
        service.dispatch(id);
        verify(sender, times(1)).send(anyString(), any());
        noMoney();
    }

    @Test
    void ambiguousUpdateIsNotRetriedOrInterpretedAsADeliveredMessage() {
        activate();
        String id = changed();
        doReturn(Receipt.UNKNOWN).when(sender).send(anyString(), any());
        service.dispatch(id);
        service.processBatch();
        service.dispatch(id);
        assertEquals("UNKNOWN", jobs.selectById(id).getState());
        assertNull(jobs.selectById(id).getPayloadEncrypted());
        verify(sender, times(2)).send(anyString(), any());
    }

    @Test
    void changedOrDisconnectedTargetCancelsUndispatchedMessagesAndClearsSecrets() {
        var p = activate();
        String id = changed();
        service.disconnect(ORDER, new VersionForm(p.version(), true));
        service.dispatch(id);
        assertEquals("CANCELLED", jobs.selectById(id).getState());
        assertNull(jobs.selectById(id).getPayloadEncrypted());
        assertNull(prefs.selectById(ORDER).getTokenEncrypted());
        assertNull(prefs.selectById(ORDER).getCodeHash());
        assertFalse(service.settings(ORDER).enabled());
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void changingCredentialRequiresNewProofAndInvalidatesOldChallenge() {
        var p = challenge();
        String oldCode = code();
        var replacement =
                service.configure(
                        ORDER,
                        new ConfigureForm(
                                "differentPrivateKey0123456789abcdef", p.version(), true));
        assertFalse(replacement.verified());
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(oldCode, p.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(oldCode, replacement.version(), true)));
    }

    @Test
    void concurrentDispatchClaimsExactlyOneDurableJob() throws Exception {
        activate();
        String id = changed();
        clearInvocations(sender);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        a -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            entered.countDown();
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            return Receipt.ACCEPTED;
                        })
                .when(sender)
                .send(anyString(), any());
        var one = threads.submit(() -> service.dispatch(id));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            service.dispatch(id);
        } finally {
            release.countDown();
        }
        one.get(5, TimeUnit.SECONDS);
        verify(sender, times(1)).send(anyString(), any());
        assertEquals("ACCEPTED", jobs.selectById(id).getState());
    }

    @Test
    void disconnectCannotRecallAnAlreadyDispatchedMessageButStopsLaterOnes() {
        var p = activate();
        String id = changed();
        doAnswer(
                        a -> {
                            auth(7);
                            service.disconnect(ORDER, new VersionForm(p.version(), true));
                            return Receipt.ACCEPTED;
                        })
                .when(sender)
                .send(anyString(), any());
        service.dispatch(id);
        assertEquals("ACCEPTED", jobs.selectById(id).getState());
        assertFalse(service.settings(ORDER).enabled());
        assertNull(prefs.selectById(ORDER).getTokenEncrypted());
        service.processBatch();
        verify(sender, times(2)).send(anyString(), any());
    }

    @Test
    void actualLocalSettlementFailureRemainsUnknownWithoutAnotherHttpCall() {
        activate();
        String id = changed();
        jdbc.execute(
                "ALTER TABLE service_notification_delivery ADD CONSTRAINT test_commit_failure"
                        + " CHECK(kind<>'ORDER_UPDATE' OR state<>'ACCEPTED')");
        service.dispatch(id);
        assertEquals("UNKNOWN", jobs.selectById(id).getState());
        assertNull(jobs.selectById(id).getPayloadEncrypted());
        service.processBatch();
        verify(sender, times(2)).send(anyString(), any());
    }

    @Test
    void outboxAndObservationAdvanceAtomicallyOrBothRollBack() {
        activate();
        jdbc.execute(
                "ALTER TABLE service_notification_preference ADD CONSTRAINT"
                        + " test_observation_failure CHECK(sequence<2)");
        jdbc.update("UPDATE service_order SET status='PAUSED' WHERE id=?", ORDER);
        assertThrows(RuntimeException.class, () -> service.observe(ORDER));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM service_notification_delivery WHERE"
                                + " kind='ORDER_UPDATE'",
                        Integer.class));
        assertTrue(prefs.selectById(ORDER).getObservedJson().contains("ACTIVE"));
        jdbc.execute(
                "ALTER TABLE service_notification_preference DROP CONSTRAINT"
                        + " test_observation_failure");
        service.observe(ORDER);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM service_notification_delivery WHERE"
                                + " kind='ORDER_UPDATE'",
                        Integer.class));
    }

    @Test
    void rateLimitFailureHoldsReadyJobWithoutSendingAndNeverContainsTokenInRateKeys() {
        activate();
        String id = changed();
        when(limiter.check(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.dispatch(id));
        assertEquals("READY", jobs.selectById(id).getState());
        verify(sender, times(1)).send(anyString(), any());
        when(limiter.check(any())).thenReturn(RateLimitDecision.allowed(1));
        service.dispatch(id);
        verify(limiter, atLeastOnce())
                .check(
                        argThat(
                                r ->
                                        r.dimension().equals("notification:delivery-target")
                                                && r.keyMaterial().matches("[a-f0-9]{64}")
                                                && !r.keyMaterial().contains(TOKEN)));
    }

    @Test
    void separateFeatureSwitchBlocksSendingButAllowsSafeDisconnection() {
        var p = activate();
        String id = changed();
        ReflectionTestUtils.setField(service, "deliveryEnabled", false);
        service.processBatch();
        service.dispatch(id);
        assertFalse(service.settings(ORDER).deliveryAvailable());
        assertThrows(
                BusinessException.class,
                () -> service.configure(ORDER, new ConfigureForm(TOKEN, p.version(), true)));
        service.disconnect(ORDER, new VersionForm(p.version(), true));
        verify(sender, times(1)).send(anyString(), any());
        assertNull(prefs.selectById(ORDER).getTokenEncrypted());
    }

    @Test
    void abandonedDispatchAndExpiredQueueNeverAutomaticallyRetry() {
        activate();
        String id = changed();
        jdbc.update(
                "UPDATE service_notification_delivery SET state='DISPATCHING',update_time=? WHERE"
                        + " id=?",
                ServiceTime.now().minusMinutes(3),
                id);
        service.processBatch();
        assertEquals("UNKNOWN", jobs.selectById(id).getState());
        jdbc.update("UPDATE service_order SET status='ACTIVE' WHERE id=?", ORDER);
        service.observe(ORDER);
        String pending =
                jdbc.queryForObject(
                        "SELECT id FROM service_notification_delivery WHERE state='READY'",
                        String.class);
        jdbc.update(
                "UPDATE service_notification_delivery SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                pending);
        service.dispatch(pending);
        assertEquals("EXPIRED", jobs.selectById(pending).getState());
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void absentProgressAndUncertainOperationAreExplicitNotSuccessClaims() {
        activate();
        jdbc.update(
                "INSERT INTO"
                    + " service_order_operation(id,order_id,user_id,product_id,product_version,provider_version,action,state,quantity,unit_charge,amount,account_label,expires_at,create_time,update_time)"
                    + " VALUES('pending-op',?,7,1,1,1,'PAUSE','UNKNOWN',0,0.25,0,'private-account',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                ORDER);
        jdbc.update("UPDATE service_order SET pending_operation_id='pending-op' WHERE id=?", ORDER);
        service.processBatch();
        assertTrue(last.get().content().contains("未提供可核实的完成次数"));
        assertTrue(last.get().content().contains("请勿重复提交"));
        assertFalse(last.get().content().contains("已完成"));
    }

    @Test
    void unsupportedUnconfirmedOrdersAndBadPaginationFailBeforeAnySend() {
        jdbc.update("UPDATE service_order SET provider_type='flash' WHERE id=?", ORDER);
        assertThrows(BusinessException.class, () -> configure());
        jdbc.update(
                "UPDATE service_order SET provider_type='sxdk_tw',external_order_no=NULL WHERE"
                        + " id=?",
                ORDER);
        assertThrows(BusinessException.class, () -> configure());
        jdbc.update("UPDATE service_order SET external_order_no='remote-1' WHERE id=?", ORDER);
        assertThrows(BusinessException.class, () -> service.deliveries(ORDER, 0, 20));
        assertThrows(BusinessException.class, () -> service.deliveries(ORDER, 1, 101));
        verifyNoInteractions(sender);
    }

    @Test
    void oldSettingsVersionsCannotOverwriteOrCancelNewRecipient() {
        var p = configure();
        var replacement = configure();
        assertThrows(
                BusinessException.class,
                () -> service.disconnect(ORDER, new VersionForm(p.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.challenge(ORDER, new VersionForm(p.version(), true)));
        assertEquals(replacement.version(), service.settings(ORDER).version());
    }

    @Test
    void disabledOrDeletedOwnerIsRevokedBeforeAnotherExternalSend() {
        activate();
        String id = changed();
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
        service.dispatch(id);
        assertEquals("CANCELLED", jobs.selectById(id).getState());
        assertNull(prefs.selectById(ORDER).getTokenEncrypted());
        assertFalse(prefs.selectById(ORDER).getEnabled());
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void reassignedOrderCannotNotifyTheOldOwnerEvenWithAQueuedDelivery() {
        activate();
        String id = changed();
        jdbc.update("UPDATE service_order SET user_id=8 WHERE id=?", ORDER);
        service.dispatch(id);
        assertEquals("CANCELLED", jobs.selectById(id).getState());
        assertNull(prefs.selectById(ORDER).getTokenEncrypted());
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void localStoredZeroIsNotEvidenceOfAttendanceProgressAndPeriodEndIsNotSuccess() {
        activate();
        jdbc.update("UPDATE service_order SET completed=9,version=version+1 WHERE id=?", ORDER);
        service.processBatch();
        verify(sender, times(1)).send(anyString(), any());
        jdbc.update("UPDATE service_order SET status='COMPLETED' WHERE id=?", ORDER);
        service.processBatch();
        assertTrue(last.get().content().contains("服务周期已结束，不代表考勤成功"));
        assertTrue(last.get().content().contains("未提供可核实的完成次数"));
        assertFalse(last.get().content().contains("9 / 10"));
    }

    @Test
    void newOwnerCannotInheritTheFormerReceiverOrUseTheirProof() {
        var former = challenge();
        String formerCode = code();
        jdbc.update("UPDATE service_order SET user_id=8 WHERE id=?", ORDER);
        auth(8);
        var inherited = service.settings(ORDER);
        assertFalse(inherited.configured());
        assertFalse(inherited.enabled());
        assertFalse(inherited.verified());
        assertFalse(inherited.canVerify());
        assertNull(inherited.challengeDeliveryId());
        assertNull(inherited.challengeExpiresAt());
        assertNull(inherited.verifiedAt());
        assertThrows(
                BusinessException.class,
                () -> service.challenge(ORDER, new VersionForm(inherited.version(), true)));
        assertThrows(
                BusinessException.class,
                () -> service.verify(ORDER, new VerifyForm(formerCode, inherited.version(), true)));
        assertEquals(0, service.deliveries(ORDER, 1, 20).getTotal());
        assertThrows(BusinessException.class, () -> service.delivery(former.challengeDeliveryId()));
        // Explicit replacement is allowed, but still requires a new proof for this owner.
        var replacement = configure();
        assertEquals(8L, prefs.selectById(ORDER).getUserId());
        assertFalse(replacement.verified());
        assertNull(prefs.selectById(ORDER).getCodeHash());
        var proof = service.challenge(ORDER, new VersionForm(replacement.version(), true));
        assertTrue(service.verify(ORDER, new VerifyForm(code(), proof.version(), true)).enabled());
        verify(sender, times(2)).send(anyString(), any());
        noMoney();
    }

    @Test
    void mutationRechecksOwnershipAfterRateLimitPreflight() {
        when(limiter.check(any()))
                .thenAnswer(
                        a -> {
                            jdbc.update("UPDATE service_order SET user_id=8 WHERE id=?", ORDER);
                            return RateLimitDecision.allowed(1);
                        });
        assertThrows(
                BusinessException.class,
                () -> service.configure(ORDER, new ConfigureForm(TOKEN, 0L, true)));
        assertNull(prefs.selectById(ORDER));
        verifyNoInteractions(sender);
        noMoney();
    }

    @Test
    void newOwnerCanExplicitlyRemoveAnInheritedRecipientWithoutSending() {
        activate();
        String pending = changed();
        jdbc.update("UPDATE service_order SET user_id=8 WHERE id=?", ORDER);
        auth(8);
        service.disconnect(ORDER, new VersionForm(service.settings(ORDER).version(), true));
        assertNull(prefs.selectById(ORDER).getTokenEncrypted());
        assertEquals(8L, prefs.selectById(ORDER).getUserId());
        assertEquals("CANCELLED", jobs.selectById(pending).getState());
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void expiredPayloadsAreClearedEvenWhenTheRateLimiterIsUnavailable() {
        activate();
        String pending = changed();
        jdbc.update(
                "UPDATE service_notification_delivery SET expires_at=? WHERE id=?",
                ServiceTime.now().minusSeconds(1),
                pending);
        clearInvocations(limiter);
        when(limiter.check(any())).thenReturn(null);
        service.dispatch(pending);
        assertEquals("EXPIRED", jobs.selectById(pending).getState());
        assertNull(jobs.selectById(pending).getPayloadEncrypted());
        verifyNoInteractions(limiter);
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void aRateLimitedRecipientCannotStarveOtherOwnersReadyMessages() {
        activate();
        for (int i = 0; i < 21; i++) {
            jdbc.update("UPDATE service_order SET quantity=quantity+1 WHERE id=?", ORDER);
            service.observe(ORDER);
        }
        jdbc.update(
                "UPDATE service_notification_delivery SET update_time='2001-01-01' WHERE"
                    + " state='READY'");
        auth(8);
        var p = service.configure(OTHER, new ConfigureForm(TOKEN, 0L, true));
        p = service.challenge(OTHER, new VersionForm(p.version(), true));
        service.verify(OTHER, new VerifyForm(code(), p.version(), true));
        jdbc.update("UPDATE service_order SET status='PAUSED' WHERE id=?", OTHER);
        service.observe(OTHER);
        jdbc.update(
                "UPDATE service_notification_delivery SET update_time='2002-01-01' WHERE"
                    + " state='READY' AND order_id=?",
                OTHER);
        when(limiter.check(any()))
                .thenAnswer(
                        a -> {
                            RateLimitRequest request = a.getArgument(0);
                            return request.dimension().equals("notification:delivery-user")
                                            && request.keyMaterial().equals("7")
                                    ? RateLimitDecision.denied(3600)
                                    : RateLimitDecision.allowed(1);
                        });
        service.processBatch();
        verify(sender, times(2)).send(anyString(), any());
        service.processBatch();
        verify(sender, times(3)).send(anyString(), any());
        assertTrue(last.get().content().contains(OTHER));
        assertEquals(
                21,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM service_notification_delivery WHERE state='READY' AND"
                            + " user_id=7",
                        Integer.class));
        assertEquals(
                "ACCEPTED",
                jdbc.queryForObject(
                        "SELECT state FROM service_notification_delivery WHERE kind='ORDER_UPDATE'"
                            + " AND order_id=?",
                        String.class,
                        OTHER));
        noMoney();
    }
}
