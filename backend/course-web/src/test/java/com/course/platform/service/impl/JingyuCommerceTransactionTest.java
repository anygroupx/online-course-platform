package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.servicecommerce.JingyuNativeServiceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
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

/** Real service + fixed gateway + H2/MyBatis ledger; every HTTP response is synthetic, with no network. */
class JingyuCommerceTransactionTest {
    private static final String EVIDENCE = "已核实两个订单编号、账号、任务状态、退款次数和实际资金记录";
    private static final String PHONE = "13800138000", UID = "150123", PASSWORD = " fixture +&= 密码 ";
    final ServiceCommerceTransactionTest f = new ServiceCommerceTransactionTest();
    final ObjectMapper json = new ObjectMapper();
    final AtomicInteger writes = new AtomicInteger();
    final List<String> calls = new CopyOnWriteArrayList<>();
    ApiHttpClient http;
    JingyuNativeServiceGateway gateway;
    ObjectNode remote, student;
    ArrayNode tasks;
    Long productId;
    String project = "keep", cost = "0.01", retail = "0.01", uncertainAction;
    int remaining = 3;
    Runnable onIdentityRead;
    CountDownLatch entered, release;

    @BeforeEach
    void setup() throws Exception {
        f.setup(); f.provider.setProviderType("jingyu"); f.provider.setUsername("42");
        http = mock(ApiHttpClient.class);
        gateway = spy(new JingyuNativeServiceGateway(http, new ProviderUrlNormalizer()));
        ReflectionTestUtils.setField(f.service, "gateway", gateway);
        ReflectionTestUtils.setField(f.service, "catalogs", new PluginConnectorRegistry(List.of(gateway)));
        configureProject("keep");
        tasks = json.createArrayNode();
        for (int i = 1; i <= 3; i++) tasks.add(json.createObjectNode().put("run_task_id", "task-" + i)
                .put("start_time", time(i)).put("status_display", "未开始"));
        when(http.postForString(eq(f.provider), anyString(), anyMap())).thenAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "HTTP must stay outside ledger/order transactions");
            String url = inv.getArgument(1), action = url.substring(url.indexOf("&act=") + 5);
            Map<String, Object> body = inv.getArgument(2); calls.add(action);
            switch (action) {
                case "get_price": return "{\"code\":1,\"data\":\"" + cost + "\"}";
                case "get_keep_user_info", "get_bdlp_user_info", "get_yyd_user_info": return data(json.createObjectNode().set("student", student));
                case "get_school_data": return data(json.createObjectNode().set("list", json.createArrayNode()
                        .add(json.createObjectNode().put("school_id", "51").put("name", "示例学院"))));
                case "get_keep_zone_data", "get_bdlp_zone_data":
                    return data(json.createObjectNode().set("list", json.createArrayNode()
                            .add(json.createObjectNode().put("zone_id", "7").put("name", "示例跑区"))));
                case "orders":
                    assertEquals("1", body.get("type")); assertEquals("1", body.get("page")); assertEquals("20", body.get("limit"));
                    if (onIdentityRead != null) { Runnable callback = onIdentityRead; onIdentityRead = null; callback.run(); }
                    ObjectNode page = json.createObjectNode().put("code", 1); page.putArray("data").add(remote);
                    page.putObject("pagination").put("page", 1).put("limit", 20).put("last_page", 1).put("total", 1);
                    return page.toString();
                case "get_task_data": return data(json.createObjectNode().set("list", tasks));
                case "get_remain_count": return data(json.createObjectNode().put("refund_cnt", remaining));
                case "keep_add", "bdlp_add", "yyd_add", "refund", "change_run_status", "edit_task", "delay_task", "fast_delay_task":
                    writes.incrementAndGet();
                    if (entered != null) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    if ("change_run_status".equals(action)) remote.put("pause", (String) body.get("status"));
                    if ("fast_delay_task".equals(action)) remote.put("status_display", "正常");
                    if ("refund".equals(action)) remote.put("status_display", "已退款");
                    if (action.equals(uncertainAction)) throw new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
                    return action.endsWith("_add") ? data(json.createObjectNode().put("id", "17").put(project + "_order_id", project + "-451"))
                            : "{\"code\":1}";
                default: throw new AssertionError("Unexpected fixed action " + action);
            }
        });
        publish();
    }

    @AfterEach
    void cleanup() { if (release != null) release.countDown(); f.cleanup(); }

    void configureProject(String selected) {
        project = selected;
        remote = json.createObjectNode().put("id", "17").put(project + "_order_id", project + "-451")
                .put("uid", "42").put("user", account()).put("num", "3").put("distance", "1.5")
                .put("zone_id", "7").put("zone_name", "示例跑区").put("status_display", "正常").put("pause", "1")
                .put("min_minute", "4").put("max_minute", "12").put("school_name", "示例学院")
                .put("run_type", "1").put("is_auth", "1").put("auth_type", "设备授权").put("auth_time", "2026-01-01 08:00:00");
        student = json.createObjectNode();
        if ("yyd".equals(project)) {
            remote.put("pass", PASSWORD).put("run_rule_item_id", "91");
            student.put("student_id", "81").put("school_id", "51").put("number", account());
            student.putArray("run_rule_items").add(json.createObjectNode().put("run_rule_item_id", "91").put("min_dis", "1.5")
                    .set("zone", json.createObjectNode().put("zone_id", "7").put("name", "示例跑区")));
        } else if ("bdlp".equals(project)) {
            student.put("uid", UID); student.putObject("school").put("name", "示例学院");
            student.putObject("device").put("is_expired", false).put("login_type_display", "设备授权").put("refresh_at", "2026-01-01 08:00:00");
            student.putObject("run_rule").put("min_dis", "1.5");
        } else {
            student.put("student_id", "81").put("phone", PHONE).put("default_zone_id", "7");
            student.putObject("default_zone").put("zone_id", "7").put("name", "示例跑区");
        }
    }
    String account() { return "yyd".equals(project) ? "001_student-25" : "bdlp".equals(project) ? UID : PHONE; }
    String time(int days) { return ServiceTime.now().toLocalDate().plusDays(days) + " 07:30:00"; }
    String data(com.fasterxml.jackson.databind.JsonNode value) { return json.createObjectNode().put("code", 1).set("data", value).toString(); }
    Map<String, String> fields() { return "yyd".equals(project)
            ? Map.of("account", account(), "password", PASSWORD, "schoolId", "51", "schoolName", "示例学院", "runRuleId", "91")
            : "bdlp".equals(project) ? Map.of("account", UID, "zoneId", "7", "runType", "1")
            : Map.of("account", PHONE, "password", PASSWORD, "zoneId", "7", "minMinute", "4", "maxMinute", "12"); }
    OrderForm form() { return new OrderForm(3, new BigDecimal("1.5"), fields(), List.of(time(1), time(2), time(3)), true); }
    void publish() {
        f.auth(7, "api-provider:update");
        ServiceProduct existing = productId == null ? null : f.products.selectById(productId);
        boolean update = existing != null && project.equals(existing.getProject());
        productId = f.service.saveProduct(update ? existing.getId() : null,
                new ProductCommand(9L, project, project, "运动计划", "", new BigDecimal(retail), true,
                        update ? existing.getVersion() : null)).id();
        f.auth(7, "ROLE_USER");
    }
    QuoteView quote() { return f.service.quote(productId, form()); }
    QuoteView create() { var done = f.service.confirm(quote().id()); assertEquals("SUCCEEDED", done.state()); return done; }
    QuoteView unknownCreate() { uncertainAction = project + "_add"; var done = f.service.confirm(quote().id()); assertEquals("UNKNOWN", done.state()); return done; }
    void admin() { f.auth(8, "api-provider:update", "payment:reconcile"); }
    ResolveForm acceptedCreate() { return new ResolveForm("ACCEPTED", project + "-451", null, EVIDENCE, true, "17"); }
    ResolveForm acceptedAction(Integer units) { return new ResolveForm("ACCEPTED", null, units, EVIDENCE, true); }
    int ledgerRows() { return f.jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class); }
    void allowRefresh(String id) { f.jdbc.update("UPDATE service_order SET status_check_after=NULL WHERE id=?", id); }
    QuoteView action(String id, String action) { return f.service.quoteAction(id, new ActionForm(action, 0)); }
    void successfulTasks(int count) { for (int i = 0; i < count; i++) ((ObjectNode) tasks.get(i)).put("status_display", "成功"); }

    @ParameterizedTest @ValueSource(strings = {"keep", "bdlp"})
    void checkoutFreezesAccountBindingAndPerTaskCentChargeWithOneDebitAndBothReceipts(String selected) throws Exception {
        configureProject(selected); publish(); calls.clear();
        var q = quote(); String amount = "keep".equals(project) ? "0.06" : "0.03";
        assertEquals(amount, q.amount()); assertEquals("keep".equals(project) ? "0.02000000" : "0.01000000", q.unitCharge());
        assertEquals(0, writes.get()); assertEquals(0, ledgerRows()); f.money("100");
        var savedQuote = f.operations.selectById(q.id());
        assertTrue(json.readValue(savedQuote.getScheduleJson(), ServiceAccountFingerprint.class).matches(account()));
        assertFalse(savedQuote.getPayloadEncrypted().contains(account())); assertFalse(savedQuote.getPayloadEncrypted().contains(PASSWORD));
        assertFalse(savedQuote.getScheduleJson().contains(account()));
        var done = f.service.confirm(q.id()); assertEquals("SUCCEEDED", done.state());
        var order = f.orders.selectById(done.orderId());
        assertEquals(project + "-451", order.getExternalOrderNo()); assertEquals("17", order.getExternalSubOrderNo());
        assertEquals(savedQuote.getScheduleJson(), order.getScheduleJson()); assertNull(f.operations.selectById(q.id()).getPayloadEncrypted());
        assertNull(f.service.order(order.getId()).schedule()); assertNotNull(f.service.order(order.getId()).statusCheck());
        assertEquals("SUCCEEDED", f.service.confirm(q.id()).state()); assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
        f.money("keep".equals(project) ? "99.94" : "99.97");
        f.auth(7, "api-provider:update");
        assertEquals("keep".equals(project) ? "元/次·公里" : "元/次", f.service.products(1, 20, true, "jingyu").getRecords().get(0).priceUnit());
    }

    @ParameterizedTest @ValueSource(strings = {"keep", "bdlp"})
    void oneHundredKilometersUsesJingyuLimitsNotOtherProtocols(String selected) {
        configureProject(selected); publish(); remote.put("distance", "100");
        var q = f.service.quote(productId, new OrderForm(3, new BigDecimal("100"), fields(), form().taskTimes(), true));
        assertEquals("keep".equals(project) ? "3.00" : "0.03", q.amount());
        assertEquals("SUCCEEDED", f.service.confirm(q.id()).state());
    }

    @ParameterizedTest @ValueSource(strings = {"0.9", "100.1", "1.55", "0", "-1"})
    void invalidDistancesNeverReserveFunds(String value) {
        clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.quote(productId, new OrderForm(3, new BigDecimal(value), fields(), form().taskTimes(), true)));
        verifyNoInteractions(http); assertEquals(0, ledgerRows());
    }

    @ParameterizedTest @ValueSource(strings = {"ymty", "bad", ""})
    void unimplementedProjectsCannotBePublished(String value) {
        f.auth(7, "api-provider:update"); clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.saveProduct(null, new ProductCommand(9L, value, value, "测试", "", BigDecimal.ONE, true, null)));
        verifyNoInteractions(http);
    }

    @Test void quoteRequiresThePreparedBindingToMatchTheSubmittedAccount() {
        var prepared = gateway.prepare(f.provider, f.products.selectById(productId), form());
        doReturn(new PreparedOrder(prepared.fields(), 3, new BigDecimal("1.5"), new BigDecimal("1.5"), "脱敏账号", null, null,
                ServiceAccountFingerprint.create("other-account"))).when(gateway).prepare(any(), any(), any());
        assertThrows(BusinessException.class, this::quote); f.money("100"); assertEquals(0, ledgerRows());
    }

    @Test void quoteExpiresAtItsEarliestExplicitTaskTimeAndDoesNotDispatchExpiredPlan() {
        var now = ServiceTime.now().withNano(0);
        try (var clock = mockStatic(ServiceTime.class)) {
            clock.when(ServiceTime::now).thenReturn(now);
            String near = now.plusSeconds(20).format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
            var q = f.service.quote(productId, new OrderForm(3, new BigDecimal("1.5"), fields(), List.of(time(1), near, time(3)), true));
            assertEquals(now.plusSeconds(20), q.expiresAt());
            clock.when(ServiceTime::now).thenReturn(q.expiresAt());
            assertThrows(BusinessException.class, () -> f.service.confirm(q.id()));
            assertEquals(0, writes.get()); assertEquals(0, ledgerRows());
        }
    }

    @Test void concurrentConfirmationReservesAndDispatchesOnce() throws Exception {
        var q = quote(); entered = new CountDownLatch(1); release = new CountDownLatch(1);
        var first = f.threads.submit(() -> { f.auth(7, "ROLE_USER"); return f.service.confirm(q.id()); });
        assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals("DISPATCHING", f.service.confirm(q.id()).state());
        release.countDown(); assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).state());
        f.money("99.94"); assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test void changedCostOrConfigurationCannotCauseAReplayOrAutomaticCredit() {
        var q = quote(); cost = "0.03";
        assertEquals("UNKNOWN", f.service.confirm(q.id()).state()); assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertEquals(0, writes.get()); assertEquals(1, ledgerRows()); f.money("99.94");
    }

    @ParameterizedTest @ValueSource(strings = {"正常", "部分失败", "全部完成", "已退款"})
    void createKeepsItsVerifiedRemoteStateInsteadOfInventingActive(String raw) {
        remote.put("status_display", raw); if ("全部完成".equals(raw)) successfulTasks(3);
        var done = create();
        assertEquals(switch (raw) { case "部分失败" -> "ATTENTION"; case "全部完成" -> "COMPLETED"; case "已退款" -> "REFUND_REVIEW"; default -> "ACTIVE"; }, f.orders.selectById(done.orderId()).getStatus());
        assertEquals("全部完成".equals(raw) ? 3 : 0, f.orders.selectById(done.orderId()).getCompleted());
        assertEquals(1, ledgerRows()); f.money("99.94");
    }

    @Test void lostCreationIsRecoveredByTheTwoIdentifiersWithoutNewDebitOrWrite() {
        var q = unknownCreate(); assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertNotNull(f.operations.selectById(q.id()).getPayloadEncrypted());
        admin(); var done = f.service.resolve(q.id(), acceptedCreate()); assertEquals("SUCCEEDED", done.state());
        var order = f.orders.selectById(q.orderId()); assertEquals("17", order.getExternalSubOrderNo()); assertEquals("keep-451", order.getExternalOrderNo());
        assertNull(order.getPendingOperationId()); assertNull(f.operations.selectById(q.id()).getPayloadEncrypted());
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedCreate()));
        assertEquals(1, writes.get()); assertEquals(1, ledgerRows()); f.money("99.94");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", "-1", "abc", "17 ", "99999999999999999999"})
    void missingOrInvalidSecondIdCannotRecoverALostCreate(String id) {
        var q = unknownCreate(); admin(); clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), new ResolveForm("ACCEPTED", "keep-451", null, EVIDENCE, true, id)));
        verifyNoInteractions(http); assertEquals("UNKNOWN", f.operations.selectById(q.id()).getState());
    }

    @ParameterizedTest @CsvSource({"id,18", "keep_order_id,other-receipt", "uid,43", "user,13800138001", "num,4", "distance,2"})
    void crossIdentityOrQuantityMismatchCannotBindALostReceipt(String field, String value) {
        var q = unknownCreate(); remote.put(field, value); admin();
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedCreate()));
        assertNull(f.orders.selectById(q.orderId()).getExternalOrderNo()); assertEquals(1, ledgerRows()); assertEquals(1, writes.get());
    }

    @ParameterizedTest @ValueSource(strings = {"api-provider:update", "payment:reconcile", "ROLE_ADMIN", "ROLE_USER"})
    void manualRecoveryRequiresBothPermissionsBeforeAnyIdentityRead(String permission) {
        var q = unknownCreate(); f.auth(8, permission); clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedCreate())); verifyNoInteractions(http);
    }

    @Test void identityReadIsDiscardedIfProviderChangesBeforeCommit() {
        var q = unknownCreate(); admin(); onIdentityRead = () -> f.provider.setConfigVersion(f.provider.getConfigVersion() + 1);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedCreate()));
        assertEquals("UNKNOWN", f.operations.selectById(q.id()).getState()); assertNull(f.orders.selectById(q.orderId()).getExternalOrderNo());
        assertEquals(1, ledgerRows());
    }

    @Test void identityReadIsDiscardedIfOrderChangesBeforeCommit() {
        var q = unknownCreate(); admin(); onIdentityRead = () -> f.jdbc.update("UPDATE service_order SET version=version+1 WHERE id=?", q.orderId());
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedCreate()));
        assertEquals("UNKNOWN", f.operations.selectById(q.id()).getState()); assertEquals(1, ledgerRows());
    }

    @Test void pauseAndResumeRetainPartialFailureAndRemainAvailableForAttentionOrders() {
        var created = create(); remote.put("status_display", "部分失败");
        assertEquals("SUCCEEDED", f.service.confirm(action(created.orderId(), "PAUSE").id()).state());
        assertEquals("ATTENTION", f.orders.selectById(created.orderId()).getStatus());
        assertTrue(f.service.order(created.orderId()).actions().contains("RESUME"));
        assertEquals("SUCCEEDED", f.service.confirm(action(created.orderId(), "RESUME").id()).state());
        assertEquals("ATTENTION", f.orders.selectById(created.orderId()).getStatus());
        assertEquals(3, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test void delayedTasksCannotConcealExpiredAuthorization() {
        configureProject("bdlp"); publish(); var created = create(); remote.put("status_display", "部分失败").put("is_auth", "0");
        assertEquals("SUCCEEDED", f.service.confirm(action(created.orderId(), "DELAY").id()).state());
        assertEquals("ATTENTION", f.orders.selectById(created.orderId()).getStatus());
        assertEquals(2, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test void unknownPauseRecoveryUsesExistingReceiptsAndVerifiedStateWithoutRepeatingToggle() {
        var created = create(); uncertainAction = "change_run_status";
        var q = f.service.confirm(action(created.orderId(), "PAUSE").id()); assertEquals("UNKNOWN", q.state());
        admin(); assertEquals("SUCCEEDED", f.service.resolve(q.id(), acceptedAction(null)).state());
        assertEquals("PAUSED", f.orders.selectById(created.orderId()).getStatus()); assertEquals(2, writes.get()); assertEquals(1, ledgerRows());
    }

    @Test void taskChangeUsesExplicitPageAndPreservesAttentionWithOneDispatch() {
        var created = create(); remote.put("status_display", "部分失败");
        var q = f.service.quoteAction(created.orderId(), new ActionForm("CHANGE_TIME", 0, Map.of("taskId", "task-1", "page", "1", "time", time(4))));
        assertEquals("0.00", q.amount()); assertEquals("SUCCEEDED", f.service.confirm(q.id()).state());
        assertEquals("ATTENTION", f.orders.selectById(created.orderId()).getStatus()); assertEquals(2, writes.get());
    }

    @Test void refundWithoutAtomicCountRemainsUnknownAndNeverCreditsTheEstimatedCount() {
        var created = create(); var q = f.service.confirm(action(created.orderId(), "REFUND").id());
        assertEquals("UNKNOWN", q.state()); assertEquals("UNKNOWN", f.service.confirm(q.id()).state());
        assertEquals(q.id(), f.orders.selectById(created.orderId()).getPendingOperationId());
        assertEquals(new BigDecimal("0.00"), f.orders.selectById(created.orderId()).getRefundedAmount());
        assertEquals(2, writes.get()); assertEquals(1, ledgerRows()); f.money("99.94");
    }

    @Test void evenAnUnexpectedNumericGatewayRefundCannotAutoCreditWithoutManualAttestation() {
        var created = create(); var q = action(created.orderId(), "REFUND");
        doReturn(new RemoteResult("keep-451", "REFUND_REVIEW", 0, 3, "17")).when(gateway).execute(any(), any(), any(), eq("REFUND"), anyMap());
        assertEquals("UNKNOWN", f.service.confirm(q.id()).state()); assertEquals(1, ledgerRows()); f.money("99.94");
    }

    @Test void manualRefundRechecksReceiptAndCreditsOnlyAttestedUnitsToTheOwnerOnce() {
        var created = create(); var q = f.service.confirm(action(created.orderId(), "REFUND").id());
        assertEquals("UNKNOWN", q.state()); admin(); var done = f.service.resolve(q.id(), acceptedAction(2));
        assertEquals("SUCCEEDED", done.state()); assertEquals("0.04", done.amount()); assertEquals(2, done.quantity());
        assertEquals("REFUNDED", f.orders.selectById(created.orderId()).getStatus());
        assertEquals(8L, f.operations.selectById(q.id()).getResolvedBy()); assertEquals(EVIDENCE, f.operations.selectById(q.id()).getResolutionNote());
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedAction(2)));
        f.money("99.98"); assertEquals(new BigDecimal("100.00"), f.jdbc.queryForObject("SELECT balance FROM sys_user WHERE id=8", BigDecimal.class));
        assertEquals(2, writes.get()); assertEquals(2, ledgerRows());
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {-1, 4})
    void refundCannotUseMissingNegativeOrOverQuoteUnits(Integer units) {
        var created = create(); var q = f.service.confirm(action(created.orderId(), "REFUND").id()); admin(); clearInvocations(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedAction(units)));
        verifyNoInteractions(http); assertEquals(1, ledgerRows()); f.money("99.94");
    }

    @Test void refundCannotUseActiveStatusOrUnitsThatFinishedAfterThePreview() {
        var created = create(); var q = f.service.confirm(action(created.orderId(), "REFUND").id()); admin();
        remote.put("status_display", "正常"); assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedAction(2)));
        remote.put("status_display", "已退款"); successfulTasks(2);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), acceptedAction(2)));
        assertEquals("SUCCEEDED", f.service.resolve(q.id(), acceptedAction(1)).state());
        f.money("99.96"); assertEquals(2, f.orders.selectById(created.orderId()).getCompleted()); assertEquals(2, ledgerRows());
    }

    @Test void confirmedNotAcceptedCreationReturnsItsSingleDebitOnceWithoutNewRequests() {
        var q = unknownCreate(); admin(); clearInvocations(http);
        var rejected = new ResolveForm("NOT_ACCEPTED", null, null, EVIDENCE, true);
        assertEquals("NOT_ACCEPTED", f.service.resolve(q.id(), rejected).state()); verifyNoInteractions(http);
        assertThrows(BusinessException.class, () -> f.service.resolve(q.id(), rejected));
        assertEquals("CANCELLED", f.orders.selectById(q.orderId()).getStatus()); f.money("100"); assertEquals(2, ledgerRows()); assertEquals(1, writes.get());
    }

    @Test void jingyuOrdersParticipateInLocalFilteringAndPeriodicReadOnlyStatusRefresh() {
        var created = create(); successfulTasks(1); ((ObjectNode) tasks.get(1)).put("status_display", "退款");
        ReflectionTestUtils.setField(f.service, "statusRefreshEnabled", true); allowRefresh(created.orderId());
        assertTrue(f.orders.dueStatusChecks(ServiceTime.now(), 10).contains(created.orderId()));
        assertEquals(1, f.service.refreshDueStatuses());
        assertEquals(1, f.orders.selectById(created.orderId()).getCompleted());
        assertEquals(1, writes.get()); assertEquals(1, ledgerRows());
        var filter = new ServiceOrderFilter(null, "jingyu", null, null, null, null, null);
        assertEquals(1, f.service.orders(1, 20, false, filter).getTotal());
    }
}
