package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.servicecommerce.LeidianNativeServiceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service/adapter and H2/MyBatis ledger. All remote responses are synthetic; no network is used. */
class LeidianCommerceTransactionTest {
    private static final String EVIDENCE = "已逐项核实订单编号、账号、执行次数及实际资金记录";
    final ServiceCommerceTransactionTest f = new ServiceCommerceTransactionTest();
    final ObjectMapper json = new ObjectMapper();
    final AtomicInteger writes = new AtomicInteger();
    final List<String> calls = new CopyOnWriteArrayList<>();
    ApiHttpClient http;
    LeidianNativeServiceGateway gateway;
    ObjectNode remote;
    Long productId;
    String project = "1", account = "student-001", cost = "0.12", uncertainAction;
    int remaining = 10;
    boolean remotePresent = true;
    Runnable onIdentityRead;
    CountDownLatch entered, release;

    @BeforeEach
    void setup() throws Exception {
        f.setup();
        f.provider.setProviderType("leidian");
        f.provider.setUsername("42");
        http = mock(ApiHttpClient.class);
        gateway = spy(new LeidianNativeServiceGateway(http, new ProviderUrlNormalizer()));
        ReflectionTestUtils.setField(f.service, "gateway", gateway);
        ReflectionTestUtils.setField(f.service, "catalogs", new PluginConnectorRegistry(List.of(gateway)));
        remote = json.createObjectNode().put("id", "17").put("yid", "yid-451").put("user_id", "42")
                .put("app_id", project).put("uid", account).put("days", "10").put("mile", "3.2")
                .put("zone_id", "7").put("zone_name", "操场 A&B").put("start_date", tomorrow())
                .put("run_time", "07:30:00 - 08:30:00").put("status", "1");
        remote.set("run_week", json.readTree("[1,3,5]"));
        when(http.postForString(eq(f.provider), anyString(), anyMap())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "HTTP must not hold ledger/order locks");
            String url = inv.getArgument(1);
            Map<String,Object> body = inv.getArgument(2);
            String action = url.substring(url.indexOf("act=") + 4).split("&")[0];
            calls.add(action);
            switch (action) {
                case "get_price": return "{\"code\":1,\"data\":\"" + cost + "\"}";
                case "get_rule": return """
                        {"code":1,"school":"测试大学","rule":[{"mile":3.2,"start_time":"07:30","end_time":"08:30"}],
                         "run_zones":[{"id":7,"name":"操场 A&B"}]}
                        """;
                case "orders":
                    if (Integer.valueOf(1).equals(body.get("type")) && onIdentityRead != null) {
                        Runnable callback = onIdentityRead; onIdentityRead = null; callback.run();
                    }
                    var root = json.createObjectNode().put("code", 1);
                    var rows = root.putArray("data");
                    if (remotePresent) rows.add(remote);
                    root.putObject("pagination").put("page", 1).put("limit", 100)
                            .put("last_page", remotePresent ? 1 : 0).put("total", remotePresent ? 1 : 0);
                    return root.toString();
                case "get_residue_num": return "{\"code\":1,\"residue_num\":" + remaining + "}";
                case "get_log": return "{\"code\":1,\"data\":{\"runTask\":[{\"id\":\"task-1\",\"runTime\":\""
                        + tomorrow() + " 07:30:00\",\"statusCode\":0}]}}";
                case "get_auth_link": return "{\"code\":1,\"msg\":\"成绩信息：等待核对\"}";
                case "add_order", "cancel_order", "batch_edit_time", "edit_run_time":
                    writes.incrementAndGet();
                    if (entered != null) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    if ("cancel_order".equals(action)) remotePresent = false;
                    if (action.equals(uncertainAction)) throw new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
                    return "add_order".equals(action) ? "{\"code\":1,\"id\":\"yid-451\"}" : "{\"code\":1}";
                default: throw new AssertionError("Unexpected fixed action " + action);
            }
        });
        publish();
    }

    @AfterEach
    void cleanup() { if (release != null) release.countDown(); f.cleanup(); }

    void publish() {
        f.auth(7, "api-provider:update");
        productId = f.service.saveProduct(null, new ProductCommand(9L, project, project, "运动计划", "",
                new BigDecimal("0.25"), true, null)).id();
        f.auth(7, "ROLE_USER");
    }
    String tomorrow() { return ServiceTime.now().toLocalDate().plusDays(1).toString(); }
    Map<String,String> planFields() { return Map.of("startDate", tomorrow(), "startTime", "07:30", "endTime", "08:30", "weekdays", "1,3,5"); }
    Map<String,String> fields() {
        Map<String,String> fields = new LinkedHashMap<>(planFields()); fields.put("account", account);
        if (!"4".equals(project)) fields.put("zoneId", "7");
        return fields;
    }
    QuoteView quote() { return f.service.quote(productId, new OrderForm(10, new BigDecimal("3.2"), fields(), List.of(), true)); }
    QuoteView create() { var done = f.service.confirm(quote().id()); assertEquals("SUCCEEDED", done.state()); return done; }
    QuoteView unknownCreate() { uncertainAction = "add_order"; var done = f.service.confirm(quote().id()); assertEquals("UNKNOWN", done.state()); return done; }
    void admin() { f.auth(7, "api-provider:update", "payment:reconcile"); }
    ResolveForm accepted() { return new ResolveForm("ACCEPTED", "yid-451", null, EVIDENCE, true, "17"); }
    int ledgerRows() { return f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class); }
    void allowRefresh(String id) { f.jdbc.update("UPDATE service_order SET status_check_after=NULL WHERE id=?", id); }
    QuoteView cancel(String id) { var q = f.service.quoteAction(id, new ActionForm("CANCEL", 0)); return f.service.confirm(q.id()); }
    RefundSettlementForm settlement(String id, int units) {
        return new RefundSettlementForm(f.orders.selectById(id).getVersion(), units, EVIDENCE, true);
    }

    @Test
    void previewDoesNotDebitAndConfirmationStoresPrivateBindingAndBothReceiptsExactlyOnce() throws Exception {
        var q = quote(); f.money("100"); assertEquals(0, writes.get());
        assertEquals("5.00", q.amount()); assertEquals("0.50000000", q.unitCharge()); assertEquals("次", q.quantityUnit());
        var before = f.operations.selectById(q.id());
        assertFalse(before.getPayloadEncrypted().contains(account)); assertFalse(before.getScheduleJson().contains(account));
        assertTrue(json.readValue(before.getScheduleJson(), ServiceAccountFingerprint.class).matches(account));
        var done = f.service.confirm(q.id()); assertEquals("SUCCEEDED", done.state()); f.money("95.00");
        var saved = f.orders.selectById(done.orderId());
        assertEquals("yid-451", saved.getExternalOrderNo()); assertEquals("17", saved.getExternalSubOrderNo());
        assertEquals(before.getScheduleJson(), saved.getScheduleJson()); assertNull(f.operations.selectById(q.id()).getPayloadEncrypted());
        var view = f.service.order(done.orderId()); assertNull(view.schedule()); assertEquals(0, view.completed());
        assertTrue(view.actions().containsAll(List.of("CANCEL", "EDIT_PLAN", "CHANGE_TIME", "SCORE_INFO")));
        assertEquals("SUCCEEDED", f.service.confirm(q.id()).state()); assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
    }

    @ParameterizedTest @ValueSource(strings = {"2", "3", "4"})
    void allProjectsApplyTheirCorrectBillableDistance(String selected) {
        project = selected;
        if ("4".equals(project)) { account = "13800138000"; remote.put("zone_id", "1").put("zone_name", "-"); }
        remote.put("app_id", project).put("uid", account); publish(); calls.clear();
        var q = quote(); assertEquals("4".equals(project) ? "8.00" : "5.00", q.amount());
        assertEquals("SUCCEEDED", f.service.confirm(q.id()).state());
        f.money("4".equals(project) ? "92.00" : "95.00"); assertEquals(1, writes.get());
        if ("4".equals(project)) assertFalse(calls.contains("get_rule"));
    }

    @Test
    void concurrentConfirmationReservesMoneyAndDispatchesOnlyOnce() throws Exception {
        var q = quote(); entered = new CountDownLatch(1); release = new CountDownLatch(1);
        var first = f.threads.submit(() -> { f.auth(7, "ROLE_USER"); return f.service.confirm(q.id()); });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals("DISPATCHING", f.service.confirm(q.id()).state());
        release.countDown(); assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).state());
        f.money("95.00"); assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test
    void aLostCreateResponseKeepsItsReservationAndCannotBeReplayed() {
        var q = unknownCreate(); assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertEquals(q.id(), f.orders.selectById(q.orderId()).getPendingOperationId());
        assertNotNull(f.operations.selectById(q.id()).getPayloadEncrypted());
        f.money("95.00"); assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test
    void unboundDiscoveryRemainsUnknownWithoutRedispatchOrAnAutomaticRefund() {
        remote.put("uid", "different-account"); var q = f.service.confirm(quote().id());
        assertEquals("UNKNOWN", q.state()); assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertNull(f.orders.selectById(q.orderId()).getExternalOrderNo());
        f.money("95.00"); assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test
    void dispatchRechecksCostWithoutRepeatingTheBusinessWriteOrCreditingAnUncertainResult() {
        var q = quote(); cost = "0.30";
        assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertEquals(0, writes.get()); f.money("95.00"); assertEquals(1, ledgerRows());
    }

    @Test
    void foreignConfirmationAndAChangedProviderVersionFailBeforeReservation() {
        var q = quote(); clearInvocations(http);
        f.auth(8, "ROLE_USER"); assertThrows(BusinessException.class, () -> f.service.confirm(q.id()));
        f.auth(7, "ROLE_USER"); f.provider.setConfigVersion(3L);
        assertThrows(BusinessException.class, () -> f.service.confirm(q.id()));
        verifyNoInteractions(http); f.money("100"); assertEquals(0, ledgerRows()); assertEquals(0, writes.get());
    }

    @Test
    void aQuoteCannotBeConfirmedAcrossBeijingMidnight() {
        var now = ServiceTime.now().toLocalDate().atTime(23, 59, 50);
        try (var clock = mockStatic(ServiceTime.class)) {
            clock.when(ServiceTime::now).thenReturn(now);
            var q = quote(); assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(), q.expiresAt());
            clock.when(ServiceTime::now).thenReturn(q.expiresAt());
            assertThrows(BusinessException.class, () -> f.service.confirm(q.id()));
            assertEquals(0, writes.get()); assertEquals(0, ledgerRows()); f.money("100");
        }
    }

    @Test
    void changingPlansAndTaskTimesIsUnchargedAndReadOnlyInformationCannotBeConfirmedAsAnAction() {
        var order = create();
        var plan = f.service.quoteAction(order.orderId(), new ActionForm("EDIT_PLAN", 0, planFields()));
        assertEquals("0.00", plan.amount()); assertEquals("SUCCEEDED", f.service.confirm(plan.id()).state());
        var task = f.service.quoteAction(order.orderId(), new ActionForm("CHANGE_TIME", 0,
                Map.of("taskId", "task-1", "page", "1", "time", tomorrow() + " 07:31:00")));
        assertEquals("0.00", task.amount()); assertEquals("SUCCEEDED", f.service.confirm(task.id()).state());
        int requests = calls.size();
        for (String action : List.of("SCORE_INFO", "REFUND", "ADD_TIMES"))
            assertThrows(BusinessException.class, () -> f.service.quoteAction(order.orderId(), new ActionForm(action, 0)));
        assertEquals(requests, calls.size()); assertEquals(3, writes.get()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @Test
    void editedPlanAndIndividualTaskPreviewsExpireAtTheirOwnCalendarDeadlines() {
        var order = create(); var now = ServiceTime.now().toLocalDate().atTime(23, 59, 50);
        try (var clock = mockStatic(ServiceTime.class)) {
            clock.when(ServiceTime::now).thenReturn(now);
            var plan = f.service.quoteAction(order.orderId(), new ActionForm("EDIT_PLAN", 0, planFields()));
            assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(), plan.expiresAt());
            var task = f.service.quoteAction(order.orderId(), new ActionForm("CHANGE_TIME", 0,
                    Map.of("taskId", "task-1", "page", "1", "time", now.toLocalDate() + " 23:59:55")));
            assertEquals(now.plusSeconds(5), task.expiresAt());
            clock.when(ServiceTime::now).thenReturn(task.expiresAt());
            assertThrows(BusinessException.class, () -> f.service.confirm(task.id()));
            clock.when(ServiceTime::now).thenReturn(plan.expiresAt());
            assertThrows(BusinessException.class, () -> f.service.confirm(plan.id()));
            assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
        }
    }

    @Test
    void cancellationIsOnlyARefundReviewAndStopsStatusRefreshWithoutCreditingBalance() {
        var order = create(); remaining = 6;
        var q = f.service.quoteAction(order.orderId(), new ActionForm("CANCEL", 0));
        assertEquals("0.00", q.amount()); assertEquals(0, q.quantity()); f.money("95.00");
        assertEquals("SUCCEEDED", f.service.confirm(q.id()).state());
        var saved = f.orders.selectById(order.orderId());
        assertEquals("REFUND_REVIEW", saved.getStatus()); assertEquals(4, saved.getCompleted());
        int requests = calls.size();
        assertEquals("REFUND_REVIEW", f.service.sync(order.orderId()).status());
        ReflectionTestUtils.setField(f.service, "statusRefreshEnabled", true);
        assertEquals(0, f.service.refreshDueStatuses());
        assertTrue(f.orders.dueStatusChecks(ServiceTime.now(), 10).isEmpty());
        var view = f.service.order(order.orderId()); assertTrue(view.actions().isEmpty()); assertNull(view.statusCheck());
        assertEquals(requests, calls.size()); assertEquals(2, writes.get()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @Test
    void aCancelledOrSettledOrderCannotReadDeletedRemoteLogsOrOptions() {
        var order = create(); assertEquals("SUCCEEDED", cancel(order.orderId()).state());
        clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.logs(order.orderId(), 1));
        assertThrows(BusinessException.class, () -> f.service.orderOptions(order.orderId()));
        assertThrows(BusinessException.class, () -> f.service.scoreInfo(order.orderId()));
        verifyNoInteractions(http);
        admin(); var refund = f.service.quoteRefundSettlement(order.orderId(), settlement(order.orderId(), 10));
        f.service.confirmRefundSettlement(refund.id());
        assertNull(f.service.order(order.orderId()).statusCheck());
        assertThrows(BusinessException.class, () -> f.service.logs(order.orderId(), 1));
        assertThrows(BusinessException.class, () -> f.service.orderOptions(order.orderId()));
        verifyNoInteractions(http);
    }

    @Test
    void refundSettlementRequiresBothPermissionsAndCreditsTheOwnerOnlyOnce() {
        var order = create(); remaining = 6; cancel(order.orderId());
        var form = settlement(order.orderId(), 6);
        for (String permission : List.of("ROLE_ADMIN", "api-provider:update", "payment:reconcile")) {
            f.auth(8, permission);
            assertThrows(BusinessException.class, () -> f.service.quoteRefundSettlement(order.orderId(), form));
        }
        f.auth(8, "api-provider:update", "payment:reconcile");
        var q = f.service.quoteRefundSettlement(order.orderId(), form); assertEquals("3.00", q.amount());
        admin(); assertThrows(BusinessException.class, () -> f.service.confirmRefundSettlement(q.id()));
        f.auth(8, "api-provider:update", "payment:reconcile");
        assertEquals("SUCCEEDED", f.service.confirmRefundSettlement(q.id()).state());
        assertEquals("SUCCEEDED", f.service.confirmRefundSettlement(q.id()).state());
        f.money("98.00"); assertEquals(new BigDecimal("100.00"), f.jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=8", BigDecimal.class));
        assertEquals(2, ledgerRows()); assertEquals(2, writes.get());
        assertThrows(BusinessException.class, () -> f.service.quoteRefundSettlement(order.orderId(), form));
    }

    @Test
    void settlementCannotExceedKnownUnusedUnitsOrTheRemainingNetPayment() {
        var order = create(); remaining = 6; cancel(order.orderId()); admin();
        for (int units : List.of(-1, 7, 11))
            assertThrows(BusinessException.class, () -> f.service.quoteRefundSettlement(order.orderId(), settlement(order.orderId(), units)));
        // Model an already-audited historical partial refund; the new credit is still capped by net payment.
        f.jdbc.update("UPDATE service_order SET refunded_amount=4.00 WHERE id=?", order.orderId());
        var q = f.service.quoteRefundSettlement(order.orderId(), settlement(order.orderId(), 6));
        assertEquals("1.00", q.amount()); f.service.confirmRefundSettlement(q.id()); f.money("96.00");
        assertEquals(new BigDecimal("5.00"), f.orders.selectById(order.orderId()).getRefundedAmount());
        assertEquals(2, ledgerRows());
    }

    @Test
    void uncertainCancellationAcceptedByAnAdministratorStillDoesNotCreditAnyMoney() {
        var order = create(); remaining = 6; f.service.sync(order.orderId()); uncertainAction = "cancel_order";
        var q = cancel(order.orderId()); assertEquals("UNKNOWN", q.state());
        int requests = calls.size(); admin();
        var decision = new ResolveForm("ACCEPTED", null, null, EVIDENCE, true);
        assertEquals("SUCCEEDED", f.service.resolve(q.id(), decision).state());
        assertEquals("REFUND_REVIEW", f.orders.selectById(order.orderId()).getStatus());
        assertEquals(4, f.orders.selectById(order.orderId()).getCompleted());
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), decision));
        assertEquals(requests, calls.size()); f.money("95.00"); assertEquals(1, ledgerRows()); assertEquals(2, writes.get());
    }

    @Test
    void cancellationResolutionRejectsAnyInlineRefundQuantity() {
        var order = create(); uncertainAction = "cancel_order"; var q = cancel(order.orderId()); admin();
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), new ResolveForm("ACCEPTED", null, 10, EVIDENCE, true)));
        assertEquals("UNKNOWN", f.operations.selectById(q.id()).getState()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @Test
    void acceptedRecoveryRequiresBothIdsAndAnIdentityBoundReadWithoutAnotherCreation() {
        var q = unknownCreate(); remaining = 7; admin();
        var recovered = f.service.resolve(q.id(), accepted()); assertEquals("SUCCEEDED", recovered.state());
        var saved = f.orders.selectById(q.orderId());
        assertEquals("yid-451", saved.getExternalOrderNo()); assertEquals("17", saved.getExternalSubOrderNo());
        assertEquals(3, saved.getCompleted()); assertEquals("ACTIVE", saved.getStatus()); assertNull(saved.getPendingOperationId());
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), accepted()));
        assertEquals(1, writes.get()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @ParameterizedTest @CsvSource({"id,18", "yid,yid-other", "uid,different-account", "user_id,43", "app_id,2", "days,9", "mile,3.3"})
    void recoveryCannotAttachADifferentRecordOrPurchaser(String key, String value) {
        var q = unknownCreate(); remote.put(key, value); admin();
        assertThrows(ProviderRequestException.class, () -> f.service.resolve(q.id(), accepted()));
        assertEquals("UNKNOWN", f.operations.selectById(q.id()).getState());
        assertNull(f.orders.selectById(q.orderId()).getExternalOrderNo()); f.money("95.00"); assertEquals(1, ledgerRows()); assertEquals(1, writes.get());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", "-1", "17 OR 1=1", "yid-451"})
    void recoveryRejectsAMissingOrMalformedInternalRecordIdBeforeRemoteReads(String id) {
        var q = unknownCreate(); admin(); clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), new ResolveForm("ACCEPTED", "yid-451", null, EVIDENCE, true, id)));
        verifyNoInteractions(http); f.money("95.00"); assertEquals(1, ledgerRows());
    }

    @ParameterizedTest @ValueSource(strings = {"ROLE_ADMIN", "api-provider:update", "payment:reconcile"})
    void recoveryCannotReadOrSettleWithOnlyOnePermission(String permission) {
        var q = unknownCreate(); f.auth(7, permission); clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), accepted()));
        verifyNoInteractions(http); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @ParameterizedTest @ValueSource(strings = {"version", "operation", "quantity", "binding", "configuration"})
    void aRecoveryReadCannotCommitAfterItsSnapshotChanges(String changed) {
        var q = unknownCreate(); admin();
        onIdentityRead = () -> {
            switch (changed) {
                case "version" -> f.jdbc.update("UPDATE service_order SET version=version+1 WHERE id=?", q.orderId());
                case "operation" -> f.jdbc.update("UPDATE service_order_operation SET update_time=? WHERE id=?",
                        f.operations.selectById(q.id()).getUpdateTime().plusSeconds(1), q.id());
                case "quantity" -> f.jdbc.update("UPDATE service_order SET quantity=11 WHERE id=?", q.orderId());
                case "binding" -> f.jdbc.update("UPDATE service_order SET schedule_json='{}' WHERE id=?", q.orderId());
                case "configuration" -> f.provider.setConfigVersion(3L);
                default -> throw new AssertionError(changed);
            }
        };
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), accepted()));
        assertEquals("UNKNOWN", f.operations.selectById(q.id()).getState());
        assertNull(f.orders.selectById(q.orderId()).getExternalOrderNo()); f.money("95.00"); assertEquals(1, ledgerRows());
    }

    @Test
    void anAuditedNonAcceptanceReturnsReservedFundsOnlyOnceWithoutRedispatch() {
        var q = unknownCreate(); admin(); int requests = calls.size();
        var decision = new ResolveForm("NOT_ACCEPTED", null, null, EVIDENCE, true);
        assertEquals("NOT_ACCEPTED", f.service.resolve(q.id(), decision).state());
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), decision));
        assertEquals("NOT_ACCEPTED", f.service.confirm(q.id()).state());
        assertEquals(requests, calls.size()); assertEquals(1, writes.get()); assertEquals(2, ledgerRows()); f.money("100");
    }

    @Test
    void scoreInfoIsReadOnlyAndAllSensitiveReadsRequireTheOwnerEvenForAdministrators() {
        var order = create(); assertEquals("成绩信息：等待核对", f.service.scoreInfo(order.orderId()).text());
        int requests = calls.size(); f.auth(8, "api-provider:update", "payment:reconcile");
        assertThrows(BusinessException.class, () -> f.service.scoreInfo(order.orderId()));
        assertThrows(BusinessException.class, () -> f.service.logs(order.orderId(), 1));
        assertThrows(BusinessException.class, () -> f.service.orderOptions(order.orderId()));
        assertThrows(BusinessException.class, () -> f.service.sync(order.orderId()));
        assertThrows(BusinessException.class, () -> f.service.order(order.orderId()));
        assertEquals(requests, calls.size()); assertEquals(1, ledgerRows()); assertEquals(1, writes.get());
    }

    @Test
    void scoreInfoCannotUseAnotherProviderIdentityOrAnOrderWithAPendingOperation() {
        var order = create(); int requests = calls.size(); f.provider.setUsername("43");
        assertThrows(BusinessException.class, () -> f.service.scoreInfo(order.orderId()));
        assertEquals(requests, calls.size()); f.provider.setUsername("42");
        uncertainAction = "batch_edit_time";
        var action = f.service.quoteAction(order.orderId(), new ActionForm("EDIT_PLAN", 0, planFields()));
        assertEquals("UNKNOWN", f.service.confirm(action.id()).state()); requests = calls.size();
        assertThrows(BusinessException.class, () -> f.service.scoreInfo(order.orderId()));
        assertEquals(requests, calls.size()); assertEquals(1, ledgerRows());
    }

    @Test
    void readOnlyRefreshIsMonotoneAndExhaustionDoesNotClaimCompletedScoresOrRefunds() {
        var order = create(); remaining = 7;
        assertEquals(3, f.service.sync(order.orderId()).completed());
        remaining = 8; allowRefresh(order.orderId());
        assertThrows(ProviderRequestException.class, () -> f.service.sync(order.orderId()));
        assertEquals(3, f.orders.selectById(order.orderId()).getCompleted());
        remaining = 0; allowRefresh(order.orderId());
        var exhausted = f.service.sync(order.orderId()); assertEquals(10, exhausted.completed()); assertEquals("ACTIVE", exhausted.status());
        assertEquals(1, ledgerRows()); assertEquals(1, writes.get()); f.money("95.00");
    }

    @Test
    void scheduledRefreshUsesTheSameTwoIdBindingAndNeverWritesOrSettlesFunds() {
        var order = create(); ReflectionTestUtils.setField(f.service, "statusRefreshEnabled", true); remaining = 8;
        assertEquals(1, f.service.refreshDueStatuses()); assertEquals(2, f.orders.selectById(order.orderId()).getCompleted());
        assertEquals(0, f.service.refreshDueStatuses());
        assertEquals(1, writes.get()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @ParameterizedTest @ValueSource(strings = {"missing-id", "wrong-id", "wrong-receipt", "completed", "refund"})
    void statusRefreshRejectsUnverifiableGatewayReceiptsWithoutChangingTheOrder(String invalid) {
        var order = create();
        var reply = new RemoteResult("wrong-receipt".equals(invalid) ? "other" : "yid-451",
                "completed".equals(invalid) ? "COMPLETED" : "ACTIVE", 2, "refund".equals(invalid) ? 1 : null,
                "missing-id".equals(invalid) ? null : "wrong-id".equals(invalid) ? "18" : "17");
        doReturn(reply).when(gateway).sync(any(), any());
        assertThrows(BusinessException.class, () -> f.service.sync(order.orderId()));
        assertEquals(0, f.orders.selectById(order.orderId()).getCompleted());
        assertEquals("ACTIVE", f.orders.selectById(order.orderId()).getStatus()); f.money("95.00"); assertEquals(1, ledgerRows());
    }

    @ParameterizedTest @ValueSource(strings = {"missing-id", "completed", "refund", "over-used"})
    void createCannotBeFinalizedFromAnIncompleteOrOverclaimingGatewayReceipt(String invalid) {
        var reply = new RemoteResult("yid-451", "completed".equals(invalid) ? "COMPLETED" : "ACTIVE",
                "over-used".equals(invalid) ? 11 : 0, "refund".equals(invalid) ? 1 : null, "missing-id".equals(invalid) ? null : "17");
        doReturn(reply).when(gateway).execute(any(), any(), any(), eq("CREATE"), anyMap());
        var q = f.service.confirm(quote().id()); assertEquals("UNKNOWN", q.state());
        assertNull(f.orders.selectById(q.orderId()).getExternalOrderNo()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

    @ParameterizedTest @ValueSource(strings = {"wrong-id", "wrong-receipt", "refunded-status", "refund-units", "regression", "over-used"})
    void cancellationCannotCreditOrFinalizeFromAnUnverifiableGatewayReceipt(String invalid) {
        var order = create(); remaining = 6; f.service.sync(order.orderId());
        var reply = new RemoteResult("wrong-receipt".equals(invalid) ? "other" : "yid-451",
                "refunded-status".equals(invalid) ? "REFUNDED" : "REFUND_REVIEW",
                "regression".equals(invalid) ? 3 : "over-used".equals(invalid) ? 11 : 4,
                "refund-units".equals(invalid) ? 6 : null, "wrong-id".equals(invalid) ? "18" : "17");
        doReturn(reply).when(gateway).execute(any(), any(), any(), eq("CANCEL"), anyMap());
        assertEquals("UNKNOWN", cancel(order.orderId()).state());
        assertEquals("ACTIVE", f.orders.selectById(order.orderId()).getStatus());
        assertEquals(4, f.orders.selectById(order.orderId()).getCompleted()); assertEquals(1, ledgerRows()); f.money("95.00");
    }
    @Test
    void anInFlightCreateCanOnlyBeRecoveredAfterTheFiveMinuteSafetyWindow() {
        var q = unknownCreate(); admin();
        f.jdbc.update("UPDATE service_order_operation SET state='DISPATCHING' WHERE id=?", q.id());
        clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), accepted()));
        verifyNoInteractions(http);
        f.jdbc.update("UPDATE service_order_operation SET update_time=? WHERE id=?", ServiceTime.now().minusMinutes(6), q.id());
        assertEquals("SUCCEEDED", f.service.resolve(q.id(), accepted()).state());
        assertEquals(1, writes.get()); assertEquals(1, ledgerRows()); f.money("95.00");
    }

}
