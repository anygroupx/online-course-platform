package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.*;

/** Real H2/MyBatis transactions plus the real internship adapter; all HTTP is mocked. */
class InternshipStatusRefreshTransactionTest {
    private final ServiceCommerceTransactionTest fixture = new ServiceCommerceTransactionTest();
    private ServiceCommerceServiceImpl service;
    private String id;

    @BeforeEach
    void setup() throws Exception {
        fixture.setup();
        service = fixture.service;
        Long product = fixture.setupInternship();
        id = service.confirm(service.quote(product, fixture.internshipForm(2)).id()).orderId();
        ReflectionTestUtils.setField(service, "statusRefreshEnabled", true);
        clearInvocations(fixture.internshipHttp, fixture.gateway, fixture.catalog);
    }

    @AfterEach
    void cleanup() { fixture.cleanup(); }

    @ParameterizedTest
    @CsvSource({"1,ACTIVE", "2,PAUSED", "0,ATTENTION", "3,ATTENTION", "-1,ATTENTION"})
    void periodicReadUsesExactPlanIdentityWithoutImpersonationOrAnyEntitlementAndMoneyChange(int code, String status)
            throws Exception {
        var before = entitlementAndMoney();
        var version = order().getVersion();
        var updated = order().getUpdateTime();
        fixture.internshipRemote.get().put("code", code);
        when(fixture.internshipHttp.getForString(any(), anyString(), anyMap())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNotNull(order().getStatusCheckToken());
            assertTrue(order().getStatusCheckUntil().isAfter(ServiceTime.now()));
            assertEquals(1, fixture.jdbc.update("UPDATE service_order SET title=title WHERE id=?", id));
            return response();
        });
        SecurityContextHolder.clearContext();
        assertEquals(List.of(id), fixture.orders.dueStatusChecks(ServiceTime.now(), 10));
        assertEquals(1, service.refreshDueStatuses());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(status, order().getStatus());
        assertEquals(version + (code == 1 ? 0 : 1), order().getVersion());
        if (code == 1) assertEquals(updated, order().getUpdateTime());
        assertEquals(0, order().getCompleted());
        assertEquals(before, entitlementAndMoney());
        assertSuccessfulCheck();
        fixture.auth(7, "ROLE_USER");
        var view = service.order(id);
        assertNull(view.completed());
        assertEquals("天", view.quantityUnit());
        assertEquals(3, view.quantity());
        assertEquals(fixture.internshipSchedule(2), view.schedule());
        assertEquals(order().getStatusCheckedAt(), view.statusCheck().checkedAt());
        assertFalse(view.statusCheck().delayed());
        String publicJson = fixture.internshipJson.writeValueAsString(view);
        for (String forbidden : List.of("secret-student", "SX-ORDER-1", "statusCheckToken", "statusCheckAttemptAt", "phone", "password"))
            assertFalse(publicJson.contains(forbidden));
        assertEquals(0, service.refreshDueStatuses());
        verifyOnlyQuery(1);
    }

    @ParameterizedTest
    @CsvSource({"1,COMPLETED", "2,COMPLETED", "3,ATTENTION"})
    void endOfServicePeriodIsNotAttendanceCompletionAndDoesNotRenewOrRefund(int code, String expected) throws Exception {
        agePlan();
        fixture.internshipRemote.get().put("code", code);
        var before = entitlementAndMoney();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(expected, order().getStatus());
        assertEquals(0, order().getCompleted());
        assertEquals(before, entitlementAndMoney());
        var view = service.order(id);
        assertNull(view.completed());
        assertNotNull(view.statusCheck().checkedAt());
        if ("COMPLETED".equals(expected)) {
            assertFalse(view.actions().contains("RUN_NOW"));
            assertTrue(view.actions().contains("EDIT_SCHEDULE"), "renewal remains an explicit quoted action");
            assertTrue(view.actions().contains("REFUND"), "expiration does not remove explicit cancellation");
            makeDue();
            assertEquals(0, service.refreshDueStatuses());
        }
        verifyOnlyQuery(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"endDate", "weekdays", "runMode", "missingOrder", "malformedCode", "invalidJson", "network"})
    void failedReadKeepsTheLastCheckAndEveryBusinessValueInsteadOfGuessingFromTheDate(String scenario) throws Exception {
        agePlan();
        // A prior valid paused state remains visible even when the service period has elapsed.
        fixture.jdbc.update("UPDATE service_order SET status='PAUSED' WHERE id=?", id);
        var checked = ServiceTime.now().minusMinutes(30);
        fixture.jdbc.update("UPDATE service_order SET status_checked_at=?,status_check_state='OK' WHERE id=?", checked, id);
        String previousTime = service.order(id).statusCheck().checkedAt().toString();
        switch (scenario) {
            case "endDate" -> fixture.internshipRemote.get().put("end_time", fixture.internshipSchedule(4).endDate().toString());
            case "weekdays" -> fixture.internshipRemote.get().put("check_week", "0,1,2");
            case "runMode" -> fixture.internshipRemote.get().put("runType", 3);
            case "missingOrder" -> fixture.internshipRemote.get().put("id", "OTHER-ID");
            case "malformedCode" -> fixture.internshipRemote.get().put("code", true);
            case "invalidJson" -> when(fixture.internshipHttp.getForString(any(), anyString(), anyMap())).thenReturn(response() + "{}");
            case "network" -> when(fixture.internshipHttp.getForString(any(), anyString(), anyMap())).thenThrow(new IllegalStateException("private-response"));
        }
        var before = businessSnapshot();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertEquals("RETRY", order().getStatusCheckState());
        assertEquals(1, order().getStatusCheckFailures());
        assertNull(order().getStatusCheckToken());
        assertEquals(previousTime, service.order(id).statusCheck().checkedAt().toString());
        assertTrue(service.order(id).statusCheck().delayed());
        assertEquals(0, service.refreshDueStatuses(), "failures use the shared backoff queue");
        verifyOnlyQuery(1);
    }

    @Test
    void refundReviewIsNeverReopenedOrSettledByAValidRead() {
        fixture.jdbc.update("UPDATE service_order SET status='REFUND_REVIEW' WHERE id=?", id);
        var before = businessSnapshot();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertEquals("REFUND_REVIEW", service.order(id).status());
        assertTrue(service.order(id).actions().isEmpty());
        assertSuccessfulCheck();
        verifyOnlyQuery(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "PAUSED", "ATTENTION", "REFUND_REVIEW"})
    void internshipCandidateStatesMatchTheSharedSqlAndLeaseEligibility(String state) {
        fixture.jdbc.update("UPDATE service_order SET status=? WHERE id=?", state, id);
        for (String excluded : List.of("COMPLETED", "REFUNDED", "CANCELLED", "UNKNOWN", "SUBMITTING")) {
            String clone = cloneOrder();
            fixture.jdbc.update("UPDATE service_order SET status=? WHERE id=?", excluded, clone);
        }
        for (String missing : Arrays.asList(null, "", "   ")) {
            String clone = cloneOrder();
            fixture.jdbc.update("UPDATE service_order SET external_order_no=? WHERE id=?", missing, clone);
        }
        String pending = cloneOrder();
        fixture.jdbc.update("UPDATE service_order SET pending_operation_id=? WHERE id=?", UUID.randomUUID().toString(), pending);
        String disabled = cloneOrder();
        fixture.jdbc.update("UPDATE service_order SET user_id=8 WHERE id=?", disabled);
        fixture.jdbc.update("UPDATE sys_user SET status=0 WHERE id=8");
        String future = cloneOrder();
        fixture.jdbc.update("UPDATE service_order SET status_check_after=? WHERE id=?", ServiceTime.now().plusHours(1), future);
        String leased = cloneOrder();
        fixture.orders.claimStatusCheck(leased, "another-reader", ServiceTime.now(), ServiceTime.now().plusMinutes(5), ServiceTime.now().minusSeconds(1));
        assertEquals(List.of(id), fixture.orders.dueStatusChecks(ServiceTime.now(), 10));
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(0, service.refreshDueStatuses());
        verifyOnlyQuery(1);
    }

    @Test
    void backgroundDisabledStillAllowsOneDeliberateOwnerReadButNeverAnotherUsersRead() {
        ReflectionTestUtils.setField(service, "statusRefreshEnabled", false);
        assertEquals(0, service.refreshDueStatuses());
        verifyNoInteractions(fixture.internshipHttp);
        var before = entitlementAndMoney();
        assertNotNull(service.sync(id).statusCheck().checkedAt());
        assertEquals(before, entitlementAndMoney());
        fixture.auth(8, "ROLE_USER");
        assertThrows(BusinessException.class, () -> service.sync(id));
        verifyOnlyQuery(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"count", "missingCount", "taskId"})
    void internshipAdapterCannotSmuggleAttendanceCountsOrSubtasksIntoTheOrder(String scenario) {
        ReflectionTestUtils.setField(service, "gateway", fixture.gateway);
        var result = new RemoteResult(order().getExternalOrderNo(), "COMPLETED",
                "missingCount".equals(scenario) ? null : "count".equals(scenario) ? 1 : 0,
                null, "taskId".equals(scenario) ? "TASK-1" : null);
        when(fixture.gateway.sync(any(), any())).thenReturn(result);
        var before = businessSnapshot();
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        assertEquals("RETRY", order().getStatusCheckState());
        assertNull(service.order(id).completed());
        verify(fixture.gateway).sync(any(), any());
        verifyNoMoreInteractions(fixture.gateway);
        verifyNoInteractions(fixture.internshipHttp);
    }

    @Test
    void unchangedCheckDoesNotInvalidateAnAlreadyQuotedRenewal() {
        var quote = service.quoteAction(id, new ActionForm("EDIT_SCHEDULE", 0, Map.of(), fixture.internshipSchedule(4)));
        var before = businessSnapshot();
        clearInvocations(fixture.internshipHttp);
        assertEquals(1, service.refreshDueStatuses());
        assertEquals(before, businessSnapshot());
        verifyOnlyQuery(1);
        assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
        assertEquals(5, order().getQuantity());
        fixture.money("98.75");
    }

    @Test
    void inFlightOldStatusCannotOverwriteAnExplicitlyConfirmedRenewal() throws Exception {
        var quote = service.quoteAction(id, new ActionForm("EDIT_SCHEDULE", 0, Map.of(), fixture.internshipSchedule(4)));
        fixture.internshipRemote.get().put("code", 2);
        String frozenResponse = response();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(fixture.internshipHttp.getForString(any(), anyString(), anyMap())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return frozenResponse;
        });
        var work = fixture.threads.submit(service::refreshDueStatuses);
        Map<String, Object> afterRenewal;
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
            afterRenewal = businessSnapshot();
            assertEquals(5, order().getQuantity());
            assertNotNull(order().getStatusCheckToken());
        } finally {
            release.countDown();
        }
        assertEquals(1, work.get(5, TimeUnit.SECONDS));
        assertEquals(afterRenewal, businessSnapshot());
        assertEquals("ACTIVE", order().getStatus());
        assertNull(order().getStatusCheckedAt());
        assertNull(order().getStatusCheckToken());
        fixture.money("98.75");
    }

    private void agePlan() throws Exception {
        var aged = DailyServicePlan.create(fixture.internshipSchedule(-1), ServiceTime.now().toLocalDate().minusDays(3));
        fixture.jdbc.update("UPDATE service_order SET schedule_json=? WHERE id=?", fixture.internshipJson.writeValueAsString(aged), id);
        fixture.internshipRemote.get().put("end_time", aged.schedule().endDate().toString());
    }

    private String response() throws Exception {
        return fixture.internshipJson.writeValueAsString(Map.of("code", 0, "data", List.of(fixture.internshipRemote.get())));
    }

    private String cloneOrder() {
        var clone = new ServiceOrder();
        BeanUtils.copyProperties(order(), clone);
        clone.setId(UUID.randomUUID().toString());
        clone.setExternalOrderNo("OTHER-" + UUID.randomUUID());
        assertEquals(1, fixture.orders.insert(clone));
        return clone.getId();
    }

    private void makeDue() {
        fixture.jdbc.update("UPDATE service_order SET status_check_after=? WHERE id=?", ServiceTime.now().minusSeconds(1), id);
    }

    private ServiceOrder order() { return fixture.orders.selectById(id); }

    private Map<String, Object> entitlementAndMoney() {
        return Map.of(
                "order", fixture.jdbc.queryForMap("SELECT user_id,provider_id,provider_identity,project,external_order_no,quantity,unit_charge,paid_amount,refunded_amount,schedule_json FROM service_order WHERE id=?", id),
                "operations", fixture.jdbc.queryForList("SELECT * FROM service_order_operation ORDER BY id"),
                "ledger", fixture.jdbc.queryForList("SELECT * FROM account_ledger ORDER BY id"),
                "users", fixture.jdbc.queryForList("SELECT id,balance,total_recharge FROM sys_user ORDER BY id"));
    }

    private Map<String, Object> businessSnapshot() {
        return Map.of("entitlementAndMoney", entitlementAndMoney(), "order", fixture.jdbc.queryForMap(
                "SELECT status,completed,version,pending_operation_id,external_sub_order_no,update_time FROM service_order WHERE id=?", id));
    }

    private void assertSuccessfulCheck() {
        assertNotNull(order().getStatusCheckedAt());
        assertEquals("OK", order().getStatusCheckState());
        assertEquals(0, order().getStatusCheckFailures());
        assertNull(order().getStatusCheckToken());
        assertNull(order().getStatusCheckUntil());
    }

    private void verifyOnlyQuery(int count) {
        verify(fixture.internshipHttp, times(count)).getForString(eq(fixture.provider), eq(fixture.provider.getApiUrl()),
                argThat(params -> params.size() == 5 && "getOrder".equals(params.get("act"))
                        && Integer.valueOf(1).equals(params.get("page")) && Integer.valueOf(100).equals(params.get("pagesize"))));
        verifyNoMoreInteractions(fixture.internshipHttp);
        verifyNoInteractions(fixture.gateway, fixture.catalog);
    }
}
