package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.ServiceOrder;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.persistence.mapper.ServiceOrderMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses the existing real H2/MyBatis commerce fixture; no supplier or payment HTTP is used. */
class ServiceStatusRefreshTransactionTest {
    private final ServiceCommerceTransactionTest fixture = new ServiceCommerceTransactionTest();
    private ServiceCommerceServiceImpl service;
    private String id;

    @BeforeEach
    void setup() throws Exception {
        fixture.setup();
        service = fixture.service;
        id = fixture.create().orderId();
        ReflectionTestUtils.setField(service, "statusRefreshEnabled", true);
        clearInvocations(fixture.gateway, fixture.catalog);
    }

    @AfterEach
    void cleanup() {
        fixture.cleanup();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false"})
    void eitherDisabledFlagPreventsAllDatabaseAndGatewayWork(boolean enabled, boolean refresh) {
        ReflectionTestUtils.setField(service, "enabled", enabled);
        ReflectionTestUtils.setField(service, "statusRefreshEnabled", refresh);
        var mapper = mock(ServiceOrderMapper.class);
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        assertFalse(service.statusRefreshActive());
        assertEquals(0, service.refreshDueStatuses());
        verifyNoInteractions(mapper, fixture.gateway, fixture.catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"flash", "heisha", "jiguang", "wuxin"})
    void supportedTypesUpdateWithoutUserImpersonationOrTransactionDuringHttp(String type) {
        fixture.provider.setProviderType(type);
        String identity = ReflectionTestUtils.invokeMethod(service, "identity", fixture.provider);
        String project = Set.of("flash", "wuxin").contains(type) ? "sdxy" : "default";
        fixture.jdbc.update("UPDATE service_order SET provider_type=?,provider_identity=?,project=? WHERE id=?",
                type, identity, project, id);
        var money = financialSnapshot();
        long version = order().getVersion();
        when(fixture.gateway.sync(any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            ServiceOrder snapshot = call.getArgument(1);
            ServiceOrder claimed = order();
            assertNotNull(claimed.getStatusCheckToken());
            assertTrue(claimed.getStatusCheckUntil().isAfter(ServiceTime.now()));
            assertNotNull(claimed.getStatusCheckAttemptAt());
            // This separate connection can write during HTTP; no order SQL lock is held.
            assertEquals(1, fixture.jdbc.update("UPDATE service_order SET title=title WHERE id=?", id));
            return new RemoteResult(snapshot.getExternalOrderNo(), "ACTIVE", 3, null);
        });
        SecurityContextHolder.clearContext();
        assertEquals(1, service.refreshDueStatuses());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(3, order().getCompleted());
        assertEquals(version + 1, order().getVersion());
        assertSuccessfulCheck();
        assertEquals(money, financialSnapshot());
        verifyOnlyStatusReads(1);
        assertEquals(0, service.refreshDueStatuses(), "successful checks are not immediately repeated");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "PAUSED", "ATTENTION", "REFUND_REVIEW"})
    void eligibleStatesRefreshButRefundReviewCannotBeReopenedOrSettled(String state) {
        fixture.jdbc.update("UPDATE service_order SET status=? WHERE id=?", state, id);
        var money = financialSnapshot();
        respond("ACTIVE", 2);
        assertEquals(1, service.refreshDueStatuses());
        assertEquals("REFUND_REVIEW".equals(state) ? state : "ACTIVE", order().getStatus());
        assertEquals(2, order().getCompleted());
        assertEquals(money, financialSnapshot());
        verifyOnlyStatusReads(1);
    }

    @Test
    void finalUnboundPendingDisabledOwnerAndUnsupportedOrdersAreNotCandidates() {
        for (String status : List.of("COMPLETED", "REFUNDED", "CANCELLED", "SUBMITTING", "UNKNOWN")) {
            String clone = cloneOrder();
            fixture.jdbc.update("UPDATE service_order SET status=? WHERE id=?", status, clone);
        }
        for (String external : Arrays.asList(null, "", "   ")) {
            String clone = cloneOrder();
            fixture.jdbc.update("UPDATE service_order SET external_order_no=? WHERE id=?", external, clone);
        }
        String pending = cloneOrder();
        fixture.jdbc.update("UPDATE service_order SET pending_operation_id=? WHERE id=?", UUID.randomUUID().toString(), pending);
        for (long owner : List.of(8L, 99L)) {
            String clone = cloneOrder();
            fixture.jdbc.update("UPDATE service_order SET user_id=? WHERE id=?", owner, clone);
        }
        fixture.jdbc.update("UPDATE sys_user SET status=0 WHERE id=8");
        for (String type : List.of("ssbenz_xbd", "unrecognized")) {
            String clone = cloneOrder();
            fixture.jdbc.update("UPDATE service_order SET provider_type=? WHERE id=?", type, clone);
        }
        String future = cloneOrder();
        fixture.jdbc.update("UPDATE service_order SET status_check_after=? WHERE id=?", ServiceTime.now().plusHours(1), future);
        String leased = cloneOrder();
        fixture.orders.claimStatusCheck(leased, "other-worker", ServiceTime.now(), ServiceTime.now().plusMinutes(5), ServiceTime.now().minusMinutes(1));
        assertEquals(List.of(id), fixture.orders.dueStatusChecks(ServiceTime.now(), 10));
        respond("ACTIVE", 0);
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(0, service.refreshDueStatuses());
        verifyOnlyStatusReads(1);
    }

    @Test
    void unchangedStatusKeepsReadyQuoteAndBusinessTimestampValid() {
        when(fixture.gateway.prepareAction(any(), any(), anyString(), any())).thenReturn(Map.of());
        var quote = service.quoteAction(id, new ActionForm("ADD_TIMES", 2));
        var before = order();
        var money = financialSnapshot();
        respond("ACTIVE", 0);
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before.getVersion(), order().getVersion());
        assertEquals(before.getUpdateTime(), order().getUpdateTime());
        assertEquals(money, financialSnapshot());
        when(fixture.gateway.execute(any(), any(), any(), eq("ADD_TIMES"), any()))
                .thenReturn(new RemoteResult(before.getExternalOrderNo(), "ACTIVE", 0, null));
        assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
        assertEquals("ACTIVE", order().getStatus());
        assertEquals(12, order().getQuantity());
        fixture.money("94");
    }

    @Test
    void changedProgressInvalidatesReadyQuoteWithoutDispatchingIt() {
        when(fixture.gateway.prepareAction(any(), any(), anyString(), any())).thenReturn(Map.of());
        var quote = service.quoteAction(id, new ActionForm("ADD_TIMES", 2));
        respond("ACTIVE", 2);
        assertEquals(1, service.refreshDueStatuses());
        assertThrows(BusinessException.class, () -> service.confirm(quote.id()));
        verify(fixture.gateway, never()).execute(any(), any(), any(), any(), any());
        fixture.money("95");
    }

    @Test
    void failuresBackOffToOneHourKeepLastSuccessfulTimeAndResetAfterRecovery() {
        respond("ACTIVE", 0);
        service.sync(id);
        var checkedAt = order().getStatusCheckedAt();
        var before = businessSnapshot();
        doThrow(new IllegalStateException("credential/body must not persist")).when(fixture.gateway).sync(any(), any());
        for (int attempt = 1; attempt <= 9; attempt++) {
            makeDue(id);
            var start = ServiceTime.now();
            assertEquals(1, service.refreshDueStatuses());
            var current = order();
            assertEquals(Math.min(attempt, 8), current.getStatusCheckFailures());
            assertEquals("RETRY", current.getStatusCheckState());
            long expected = Math.min(60, 5L << Math.min(attempt - 1, 7));
            assertNear(start.plusMinutes(expected), current.getStatusCheckAfter());
            assertEquals(checkedAt, current.getStatusCheckedAt());
            assertNull(current.getStatusCheckToken());
            assertEquals(before, businessSnapshot());
            assertEquals(0, service.refreshDueStatuses());
        }
        // A deliberate manual read can bypass backoff, but not an in-flight lease.
        respond("ACTIVE", 0);
        var result = service.sync(id);
        assertFalse(result.statusCheck().delayed());
        assertSuccessfulCheck();
        assertEquals(before, businessSnapshot());
    }

    @Test
    void failedFirstPageDoesNotStarveLaterOrdersAndBatchIsBounded() {
        for (int i = 0; i < 11; i++) cloneOrder();
        var money = financialSnapshot();
        when(fixture.gateway.sync(any(), any())).thenThrow(new IllegalStateException("temporary failure"));
        assertEquals(10, service.refreshDueStatuses());
        assertEquals(2, service.refreshDueStatuses());
        assertEquals(0, service.refreshDueStatuses());
        var calls = mockingDetails(fixture.gateway).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sync"))
                .map(i -> ((ServiceOrder) i.getArgument(1)).getId()).toList();
        assertEquals(12, new HashSet<>(calls).size());
        assertEquals(money, financialSnapshot());
        verifyOnlyStatusReads(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "wrongId", "badStatus", "refundStatus", "negative", "excess", "noCount", "refundUnits", "badSubId"})
    void malformedResponsesPreserveBusinessStateAndMoney(String scenario) {
        String remote = order().getExternalOrderNo();
        RemoteResult result = switch (scenario) {
            case "null" -> null;
            case "wrongId" -> new RemoteResult("another-order", "COMPLETED", 10, null);
            case "badStatus" -> new RemoteResult(remote, "UNRECOGNIZED", 10, null);
            case "refundStatus" -> new RemoteResult(remote, "REFUNDED", 10, null);
            case "negative" -> new RemoteResult(remote, "ACTIVE", -1, null);
            case "excess" -> new RemoteResult(remote, "ACTIVE", 11, null);
            case "noCount" -> new RemoteResult(remote, "ACTIVE", null, null);
            case "refundUnits" -> new RemoteResult(remote, "ACTIVE", 0, 1);
            default -> new RemoteResult(remote, "ACTIVE", 0, null, "<script>private</script>");
        };
        when(fixture.gateway.sync(any(), any())).thenReturn(result);
        var before = businessSnapshot();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertEquals("RETRY", order().getStatusCheckState());
        assertNull(order().getStatusCheckedAt());
        verifyOnlyStatusReads(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "unverified", "identity"})
    void invalidConfigurationIsNotCalledAndItsOrderIsBackedOff(String scenario) {
        switch (scenario) {
            case "disabled" -> fixture.provider.setStatus(0);
            case "unverified" -> fixture.provider.setVerifiedAt(null);
            default -> fixture.provider.setUsername("changed-account");
        }
        var before = businessSnapshot();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertEquals("RETRY", order().getStatusCheckState());
        assertEquals(0, service.refreshDueStatuses());
        verifyNoInteractions(fixture.gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "address", "account", "disabled", "unverified", "user", "feature", "refresh"})
    void configurationIdentityOwnerAndFlagChangesDuringReadInvalidateItsResult(String scenario) {
        var before = businessSnapshot();
        when(fixture.gateway.sync(any(), any())).thenAnswer(call -> {
            switch (scenario) {
                case "version" -> fixture.provider.setConfigVersion(3L);
                case "address" -> fixture.provider.setApiUrl("https://changed.example");
                case "account" -> fixture.provider.setUsername("changed-account");
                case "disabled" -> fixture.provider.setStatus(0);
                case "unverified" -> fixture.provider.setVerifiedAt(null);
                case "user" -> fixture.jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
                case "feature" -> ReflectionTestUtils.setField(service, "enabled", false);
                case "refresh" -> ReflectionTestUtils.setField(service, "statusRefreshEnabled", false);
            }
            return new RemoteResult(order().getExternalOrderNo(), "COMPLETED", 10, null);
        });
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertNull(order().getStatusCheckedAt());
        assertNull(order().getStatusCheckToken());
        verifyOnlyStatusReads(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"external_order_no", "user_id", "provider_id", "provider_type", "provider_identity", "project"})
    void changedOrderBindingCannotReceiveResultEvenWithoutAVersionBump(String column) {
        Object value = switch (column) {
            case "user_id" -> 8L;
            case "provider_id" -> 99L;
            case "provider_identity" -> "b".repeat(64);
            default -> "changed";
        };
        var money = financialSnapshot();
        long version = order().getVersion();
        when(fixture.gateway.sync(any(), any())).thenAnswer(call -> {
            ServiceOrder snapshot = call.getArgument(1);
            fixture.jdbc.update("UPDATE service_order SET " + column + "=? WHERE id=?", value, id);
            return new RemoteResult(snapshot.getExternalOrderNo(), "COMPLETED", 10, null);
        });
        assertEquals(1, service.refreshDueStatuses());
        assertEquals("ACTIVE", order().getStatus());
        assertEquals(0, order().getCompleted());
        assertEquals(version, order().getVersion());
        assertNull(order().getStatusCheckedAt());
        assertNull(order().getStatusCheckToken());
        assertEquals(money, financialSnapshot());
    }

    @Test
    void twoIndependentWorkerCallsCannotClaimTheSameOrder() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        blockRead(entered, release, 2);
        var first = fixture.threads.submit(service::refreshDueStatuses);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.threads.submit(service::refreshDueStatuses).get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        assertEquals(1, first.get(5, TimeUnit.SECONDS));
        verifyOnlyStatusReads(1);
        assertSuccessfulCheck();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void manualAndBackgroundReadsShareOneLease(boolean backgroundFirst) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        blockRead(entered, release, 0);
        var first = fixture.threads.submit(() -> {
            if (backgroundFirst) return service.refreshDueStatuses();
            fixture.auth(7, "ROLE_USER");
            try { service.sync(id); return 1; }
            finally { SecurityContextHolder.clearContext(); }
        });
        var before = businessSnapshot();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(BusinessException.class, () -> service.sync(id));
            assertEquals(0, service.refreshDueStatuses());
        } finally {
            release.countDown();
        }
        assertEquals(1, first.get(5, TimeUnit.SECONDS));
        assertEquals(before, businessSnapshot());
        verifyOnlyStatusReads(1);
        assertSuccessfulCheck();
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldResponseCannotOverwriteUnchangedNewRead() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(fixture.gateway.sync(any(), any())).thenAnswer(call -> {
            ServiceOrder snapshot = call.getArgument(1);
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return new RemoteResult(snapshot.getExternalOrderNo(), "ACTIVE", 4, null);
            }
            return new RemoteResult(snapshot.getExternalOrderNo(), "ACTIVE", 0, null);
        });
        var before = businessSnapshot();
        var first = fixture.threads.submit(service::refreshDueStatuses);
        LocalDateTime checked;
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            fixture.jdbc.update("UPDATE service_order SET status_check_until=? WHERE id=?", ServiceTime.now().minusSeconds(1), id);
            makeDue(id);
            assertEquals(1, service.refreshDueStatuses());
            checked = order().getStatusCheckedAt();
            assertNotNull(checked);
        } finally {
            release.countDown();
        }
        assertEquals(1, first.get(5, TimeUnit.SECONDS));
        assertEquals(checked, order().getStatusCheckedAt());
        assertEquals(before, businessSnapshot());
        assertSuccessfulCheck();
        verifyOnlyStatusReads(2);
    }

    @Test
    void expiredLeaseCannotApplyEvenIfNoReplacementWorkerArrived() {
        var before = businessSnapshot();
        when(fixture.gateway.sync(any(), any())).thenAnswer(call -> {
            fixture.jdbc.update("UPDATE service_order SET status_check_until=? WHERE id=?", ServiceTime.now().minusSeconds(1), id);
            return new RemoteResult(order().getExternalOrderNo(), "COMPLETED", 10, null);
        });
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertNull(order().getStatusCheckedAt());
        assertNull(order().getStatusCheckToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD_TIMES", "REFUND"})
    void businessOperationDuringHttpCannotBeOverwrittenOrRetried(String action) throws Exception {
        when(fixture.gateway.prepareAction(any(), any(), anyString(), any())).thenReturn(Map.of());
        when(fixture.gateway.refundRemaining(any(), any())).thenReturn(10);
        var quote = service.quoteAction(id, new ActionForm(action, "ADD_TIMES".equals(action) ? 2 : 0));
        if ("ADD_TIMES".equals(action)) {
            when(fixture.gateway.execute(any(), any(), any(), eq(action), any()))
                    .thenReturn(new RemoteResult(order().getExternalOrderNo(), "ACTIVE", 0, null));
        } else {
            when(fixture.gateway.execute(any(), any(), any(), eq(action), any()))
                    .thenThrow(new IllegalStateException("lost response"));
        }
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        blockRead(entered, release, 10);
        var first = fixture.threads.submit(service::refreshDueStatuses);
        Map<String, Object> afterOperation;
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var result = service.confirm(quote.id());
            assertEquals("ADD_TIMES".equals(action) ? "SUCCEEDED" : "UNKNOWN", result.state());
            // updateById used by business operations must preserve the read lease until release.
            assertNotNull(order().getStatusCheckToken());
            afterOperation = businessSnapshot();
        } finally {
            release.countDown();
        }
        assertEquals(1, first.get(5, TimeUnit.SECONDS));
        assertEquals(afterOperation, businessSnapshot());
        assertNull(order().getStatusCheckedAt());
        assertNull(order().getStatusCheckToken());
        fixture.money("ADD_TIMES".equals(action) ? "94" : "95");
        verify(fixture.gateway, times(1)).execute(any(), any(), any(), eq(action), any());
        verify(fixture.gateway, times(1)).sync(any(), any());
    }

    @Test
    void ordinaryInsertsAndUpdatesCannotWriteOrEraseCheckMetadata() {
        ServiceOrder stale = order();
        var now = ServiceTime.now();
        fixture.orders.claimStatusCheck(id, "lease", now, now.plusMinutes(5), now.plusMinutes(5));
        var leased = metadata();
        stale.setStatusCheckToken("attacker");
        stale.setStatusCheckUntil(now.plusHours(1));
        stale.setStatusCheckedAt(now);
        stale.setStatusCheckAfter(now.plusHours(1));
        stale.setStatusCheckAttemptAt(now);
        stale.setStatusCheckFailures(8);
        stale.setStatusCheckState("RETRY");
        stale.setStatus("PAUSED");
        assertEquals(1, fixture.orders.updateById(stale));
        assertEquals(leased, metadata());
        stale.setId(UUID.randomUUID().toString());
        stale.setExternalOrderNo("UP-" + UUID.randomUUID());
        assertEquals(1, fixture.orders.insert(stale));
        ServiceOrder inserted = fixture.orders.selectById(stale.getId());
        assertNull(inserted.getStatusCheckToken());
        assertNull(inserted.getStatusCheckedAt());
        assertEquals(0, inserted.getStatusCheckFailures());
        assertEquals(0, fixture.orders.statusCheckSucceeded(id, "wrong-token", now, now.plusMinutes(5)));
        assertEquals(0, fixture.orders.statusCheckFailed(id, "wrong-token", now, now.plusMinutes(5), 1));
        assertEquals(0, fixture.orders.releaseStatusCheck(id, "wrong-token", now));
        assertEquals(leased, metadata());
    }

    @Test
    void failedMetadataWriteRollsBackBusinessStatusInTheSameTransaction() {
        var mapper = spy(fixture.orders);
        doReturn(0).when(mapper).statusCheckSucceeded(anyString(), anyString(), any(), any());
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        respond("COMPLETED", 10);
        var before = businessSnapshot();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertNull(order().getStatusCheckedAt());
        assertEquals("RETRY", order().getStatusCheckState());
        assertNull(order().getStatusCheckToken());
    }

    @Test
    void manualReadRemainsAvailableWithBackgroundFlagOffAndKeepsOwnerBoundary() {
        ReflectionTestUtils.setField(service, "statusRefreshEnabled", false);
        respond("ACTIVE", 0);
        var before = businessSnapshot();
        assertNotNull(service.sync(id).statusCheck().checkedAt());
        assertEquals(before, businessSnapshot());
        fixture.auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.sync(id));
        verifyOnlyStatusReads(1);
    }

    @Test
    void interruptedBatchDoesNotClaimOrders() throws Exception {
        assertEquals(0, fixture.threads.submit(() -> {
            Thread.currentThread().interrupt();
            try { return service.refreshDueStatuses(); }
            finally { Thread.interrupted(); }
        }).get(5, TimeUnit.SECONDS));
        assertNull(order().getStatusCheckToken());
        assertNull(order().getStatusCheckAttemptAt());
        verifyNoInteractions(fixture.gateway);
    }

    private void respond(String status, int completed) {
        doAnswer(call -> {
            ServiceOrder snapshot = call.getArgument(1);
            return new RemoteResult(snapshot.getExternalOrderNo(), status, completed, null);
        }).when(fixture.gateway).sync(any(), any());
    }

    private void blockRead(CountDownLatch entered, CountDownLatch release, int completed) {
        when(fixture.gateway.sync(any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ServiceOrder snapshot = call.getArgument(1);
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new RemoteResult(snapshot.getExternalOrderNo(), "ACTIVE", completed, null);
        });
    }

    private String cloneOrder() {
        var clone = new ServiceOrder();
        BeanUtils.copyProperties(order(), clone);
        clone.setId(UUID.randomUUID().toString());
        clone.setExternalOrderNo("UP-" + UUID.randomUUID());
        assertEquals(1, fixture.orders.insert(clone));
        return clone.getId();
    }

    private void makeDue(String orderId) {
        fixture.jdbc.update("UPDATE service_order SET status_check_after=? WHERE id=?", ServiceTime.now().minusSeconds(1), orderId);
    }

    private ServiceOrder order() { return fixture.orders.selectById(id); }

    private Map<String, Object> metadata() {
        return fixture.jdbc.queryForMap("SELECT status_checked_at,status_check_after,status_check_attempt_at,status_check_failures,status_check_state,status_check_token,status_check_until FROM service_order WHERE id=?", id);
    }

    private Map<String, Object> financialSnapshot() {
        return Map.of(
                "orders", fixture.jdbc.queryForList("SELECT id,quantity,unit_charge,paid_amount,refunded_amount FROM service_order ORDER BY id"),
                "operations", fixture.jdbc.queryForList("SELECT * FROM service_order_operation ORDER BY id"),
                "ledger", fixture.jdbc.queryForList("SELECT * FROM account_ledger ORDER BY id"),
                "users", fixture.jdbc.queryForList("SELECT id,balance,total_recharge FROM sys_user ORDER BY id"));
    }

    private Map<String, Object> businessSnapshot() {
        return Map.of("money", financialSnapshot(), "order", fixture.jdbc.queryForMap(
                "SELECT status,completed,version,pending_operation_id,external_sub_order_no,update_time FROM service_order WHERE id=?", id));
    }

    private void assertSuccessfulCheck() {
        var current = order();
        assertNotNull(current.getStatusCheckedAt());
        assertEquals("OK", current.getStatusCheckState());
        assertEquals(0, current.getStatusCheckFailures());
        assertNull(current.getStatusCheckToken());
        assertNull(current.getStatusCheckUntil());
        assertNear(current.getStatusCheckedAt().plusMinutes(5), current.getStatusCheckAfter());
    }

    private static void assertNear(LocalDateTime expected, LocalDateTime actual) {
        assertNotNull(actual);
        assertTrue(Math.abs(Duration.between(expected, actual).toSeconds()) <= 2, "unexpected retry/refresh interval");
    }

    private void verifyOnlyStatusReads(int count) {
        verify(fixture.gateway, times(count)).sync(any(), any());
        verifyNoMoreInteractions(fixture.gateway);
    }
}
