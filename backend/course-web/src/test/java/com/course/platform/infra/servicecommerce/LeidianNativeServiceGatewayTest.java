package com.course.platform.infra.servicecommerce;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.math.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeidianNativeServiceGatewayTest {
    final ObjectMapper json = new ObjectMapper();
    ApiHttpClient http;
    LeidianNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;
    ObjectNode remote;
    Map<String, String> fields;
    final Map<String, String> replies = new HashMap<>();
    final List<String> actions = new ArrayList<>(), urls = new ArrayList<>();
    final List<Map<String, Object>> bodies = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        http = mock(ApiHttpClient.class);
        gateway = new LeidianNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider(); provider.setProviderType("leidian"); provider.setUsername("42");
        provider.setApiKey("fixture-private-key"); provider.setApiUrl("https://service.example/install");
        product = new ServiceProduct(); product.setProviderType("leidian"); product.setProject("1");
        product.setRemoteProductId("1"); product.setUnitPrice(new BigDecimal("0.50"));
        order = new ServiceOrder(); order.setProviderType("leidian"); order.setProject("1"); order.setRemoteProductId("1");
        order.setExternalOrderNo("yid-451"); order.setExternalSubOrderNo("17"); order.setQuantity(10); order.setCompleted(0);
        order.setDistance(new BigDecimal("3.2")); order.setUnitCharge(new BigDecimal("1.00"));
        order.setScheduleJson(json.writeValueAsString(ServiceAccountFingerprint.create("student-001")));
        String date = ServiceTime.now().toLocalDate().plusDays(1).toString();
        fields = new LinkedHashMap<>(Map.of("account", "student-001", "zoneId", "7", "startDate", date,
                "startTime", "07:30", "endTime", "08:30", "weekdays", "1,3,5"));
        remote = json.createObjectNode().put("id", "17").put("yid", "yid-451").put("user_id", "42")
                .put("app_id", "1").put("uid", "student-001").put("days", "10").put("mile", "3.2")
                .put("zone_id", "7").put("zone_name", "操场 A&B").put("start_date", date)
                .put("run_time", "07:30:00 - 08:30:00").put("status", "1");
        remote.set("run_week", json.readTree("[1,3,5]"));
        replies.put("get_price", "{\"code\":1,\"data\":\"0.12\"}");
        replies.put("get_rule", """
                {"code":1,"school":"测试大学","rule":[{"mile":3.2,"start_time":"07:30","end_time":"08:30"}],
                 "run_zones":[{"id":7,"name":"操场 A&B"}]}
                """);
        replies.put("add_order", "{\"code\":1,\"id\":\"yid-451\"}");
        replies.put("get_residue_num", "{\"code\":1,\"residue_num\":10}");
        replies.put("get_log", """
                {"code":1,"data":{"runTask":[{"id":"task-1","runTime":"2099-01-01 07:30:00","endTime":null,"statusCode":0,"info":"private <script>"},
                {"id":"task-2","runTime":"2099-01-02 07:30:00","endTime":"2099-01-02 08:00:00","statusCode":1},
                {"id":"task-3","runTime":"2099-01-03 07:30:00","statusCode":-1},
                {"id":"task-4","runTime":"2099-01-04 07:30:00","statusCode":99}]}}
                """);
        replies.put("get_auth_link", "{\"code\":1,\"msg\":\"成绩信息\\nhttps://score.example/result\"}");
        for (String action : List.of("cancel_order", "edit_run_time", "batch_edit_time")) replies.put(action, "{\"code\":1}");
        when(http.postForString(eq(provider), anyString(), anyMap())).thenAnswer(inv -> {
            String url = inv.getArgument(1); Map<String, Object> body = inv.getArgument(2);
            String action = url.substring(url.indexOf("act=") + 4).split("&")[0];
            urls.add(url); actions.add(action); bodies.add(new LinkedHashMap<>(body));
            if (replies.containsKey(action)) return replies.get(action);
            if (!"orders".equals(action)) throw new AssertionError("Unexpected fixed action " + action);
            return page(json.createArrayNode().add(remote), 1, 1);
        });
    }

    String page(ArrayNode rows, int page, int total) {
        ObjectNode root = json.createObjectNode().put("code", 1); root.set("data", rows);
        root.putObject("pagination").put("page", page).put("limit", 100).put("last_page", (total + 99) / 100).put("total", total);
        return root.toString();
    }
    OrderForm form() { return new OrderForm(10, order.getDistance(), fields, List.of(), true); }
    String tomorrow() { return ServiceTime.now().toLocalDate().plusDays(1) + " 07:31:00"; }
    long writes() { return actions.stream().filter(a -> Set.of("add_order", "cancel_order", "edit_run_time", "batch_edit_time").contains(a)).count(); }

    @Test void createsOnceThenFindsAndBindsTheTwoDifferentReceiptNumbers() {
        PreparedOrder prepared = gateway.prepare(provider, product, form());
        assertEquals(0, prepared.billablePerUnit().compareTo(new BigDecimal("2")));
        assertTrue(prepared.accountFingerprint().matches("student-001"));
        assertFalse(prepared.accountLabel().contains("student-001"));
        assertEquals("测试大学", prepared.fields().get("form[schoolName]"));
        assertEquals(1, prepared.fields().get("form[weeks][0]"));
        assertFalse(prepared.fields().containsKey("form"));
        RemoteResult result = gateway.execute(provider, product, order, "CREATE", prepared.fields());
        assertEquals("yid-451", result.externalOrderNo()); assertEquals("17", result.externalSubOrderNo());
        assertEquals(1, writes()); assertEquals("orders", actions.get(actions.size() - 1));
        Map<String, Object> discovery = bodies.get(bodies.size() - 1);
        assertEquals(2, discovery.get("type")); assertEquals("student-001", discovery.get("keywords"));
        for (int i = 0; i < urls.size(); i++) {
            assertTrue(urls.get(i).startsWith("https://service.example/install/ldrun/api.php?act="));
            assertFalse(urls.get(i).contains("fixture-private-key"));
            assertEquals("42", bodies.get(i).get("login_uid"));
            assertEquals("fixture-private-key", bodies.get(i).get("login_key"));
            if ("get_price".equals(actions.get(i))) {
                assertTrue(urls.get(i).endsWith("&appId=1")); assertFalse(bodies.get(i).containsKey("appId"));
            }
        }
        assertEquals("ACTIVE", gateway.sync(provider, order).status());
        int index = actions.lastIndexOf("orders");
        assertEquals(1, bodies.get(index).get("type")); assertEquals("17", bodies.get(index).get("keywords"));
        assertEquals("yid-451", bodies.get(bodies.size() - 1).get("orderId"));
    }

    @ParameterizedTest @ValueSource(strings = {"1", "2", "3", "4"})
    void fourProductsHaveAConservativeCatalogAndTheCorrectMileageCap(String project) throws Exception {
        product.setProject(project); product.setRemoteProductId(project);
        if ("4".equals(project)) { fields.put("account", "13800138000"); fields.remove("zoneId"); }
        var prepared = gateway.prepare(provider, product, form());
        assertEquals(0, prepared.billablePerUnit().compareTo(new BigDecimal("4".equals(project) ? "3.2" : "2")));
        var catalog = gateway.fetchCatalog(provider, project);
        assertEquals(1, catalog.size()); assertEquals(project, catalog.get(0).id());
        assertTrue(catalog.get(0).unitPrice().compareTo(new BigDecimal("0.12")) > 0);
        if ("4".equals(project)) {
            assertFalse(actions.contains("get_rule")); assertEquals("1", prepared.fields().get("form[zoneId]"));
            assertEquals("-", prepared.fields().get("form[zoneName]")); assertEquals("", prepared.fields().get("form[schoolName]"));
            assertThrows(BusinessException.class, () -> gateway.lookup(provider, product, fields));
        }
    }

    @Test void centRoundingIsPerRunAndThePublishedRateCoversAllAdmissibleDistances() {
        for (String base : List.of("0", "0.0049", "0.005", "0.1049", "0.125", "1.2349", "99.9999")) {
            BigDecimal actual = new BigDecimal(base), rounded = actual.setScale(2, RoundingMode.HALF_UP);
            for (String project : List.of("1", "2", "3", "4")) {
                BigDecimal rate = LeidianNativeServiceGateway.conservativeUnitCost(rounded, project);
                for (int tenths = 10; tenths <= 100; tenths++) {
                    BigDecimal distance = BigDecimal.valueOf(tenths, 1);
                    if (!"4".equals(project)) distance = distance.min(new BigDecimal("2"));
                    BigDecimal perRun = actual.multiply(distance).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal upper = LeidianNativeServiceGateway.costUpperBound(rounded, distance);
                    assertTrue(upper.compareTo(perRun) >= 0, base + " / " + distance);
                    assertTrue(rate.multiply(distance).compareTo(perRun) >= 0);
                    assertTrue(rate.multiply(distance).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                            .compareTo(perRun.multiply(new BigDecimal("100"))) >= 0);
                }
            }
        }
        assertEquals(new BigDecimal("0.13"), LeidianNativeServiceGateway.costUpperBound(new BigDecimal("0.13"), BigDecimal.ONE));
        assertEquals(new BigDecimal("0.27"), LeidianNativeServiceGateway.costUpperBound(new BigDecimal("0.13"), new BigDecimal("2")));
    }

    @Test void customTimeIsAllowedButCurrentSchoolAndZoneAreAlwaysDerivedAgain() {
        var lookup = gateway.lookup(provider, product, Map.of("account", "student-001"));
        assertEquals("测试大学", lookup.suggested().get("schoolName")); assertEquals("3.2", lookup.runRules().get(0).distance());
        fields.put("startTime", "10:31:05"); fields.put("endTime", "11:31:06");
        var prepared = gateway.prepare(provider, product, form());
        assertEquals("10:31:05 - 11:31:06", prepared.fields().get("form[runTime]"));
        replies.put("get_rule", replies.get("get_rule").replace("操场 A&B", "变更后的跑区"));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"0.9", "0", "-1", "10.1", "1.01"})
    void invalidDistanceNeverMakesRequests(String distance) {
        order.setDistance(new BigDecimal(distance));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @CsvSource({"startDate,2020-02-30", "startDate,2099-01-01", "startTime,24:00", "endTime,07:00", "weekdays,0", "weekdays,1;2", "zoneId,8", "account,bad+uid"})
    void invalidPlansFailBeforeAnyWrite(String key, String value) {
        fields.put(key, value);
        assertThrows(RuntimeException.class, () -> gateway.prepare(provider, product, form()));
        assertEquals(0, writes());
    }

    @Test void callerCannotSupplySchoolZoneNamePriceOrArbitraryAction() {
        for (String field : List.of("schoolName", "zoneName", "price", "login_uid", "act", "url")) {
            fields.put(field, "untrusted");
            assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form())); fields.remove(field);
        }
        for (String action : List.of("REFUND", "ADD_TIMES", "get_auth_link", "../cancel_order"))
            assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, action, Map.of()));
        assertEquals(0, writes());
    }

    @Test void pricingDriftPreventsDispatchAndDoesNotResubmit() {
        var prepared = gateway.prepare(provider, product, form());
        replies.put("get_price", "{\"code\":1,\"data\":\"9.99\"}");
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @CsvSource({"id,18", "yid,other-receipt", "user_id,43", "uid,another-student", "app_id,2", "days,11", "mile,3.1"})
    void exactOrderIdentityMismatchStopsAllSensitiveReadsAndWrites(String key, String value) {
        remote.put(key, value);
        assertThrows(RuntimeException.class, () -> gateway.sync(provider, order));
        assertThrows(RuntimeException.class, () -> gateway.logs(provider, order, 1));
        assertThrows(RuntimeException.class, () -> gateway.scoreInfo(provider, order));
        assertThrows(RuntimeException.class, () -> gateway.execute(provider, product, order, "CANCEL", Map.of()));
        assertFalse(actions.contains("get_auth_link")); assertFalse(actions.contains("get_log")); assertEquals(0, writes());
    }

    @Test void aSingleReceiptCannotAuthorizeOrdersAndMissingAccountBindingFailsClosed() {
        order.setExternalSubOrderNo(null);
        assertThrows(BusinessException.class, () -> gateway.sync(provider, order)); verifyNoInteractions(http);
        order.setExternalSubOrderNo("17"); order.setScheduleJson(null);
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertFalse(actions.contains("get_residue_num"));
    }

    @ParameterizedTest @ValueSource(strings = {"uid", "user_id", "days", "mile", "zone_id", "zone_name", "start_date", "run_time", "run_week", "yid"})
    void uncertainCreationDiscoveryNeverTriggersASecondBusinessWrite(String key) {
        var prepared = gateway.prepare(provider, product, form()); remote.put(key, "mismatch");
        assertThrows(RuntimeException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(1, writes());
    }

    @Test void duplicateReceiptsAndInconsistentPaginationRemainUncertain() {
        var prepared = gateway.prepare(provider, product, form());
        replies.put("orders", page(json.createArrayNode().add(remote).add(remote.deepCopy().put("id", "18")), 1, 2));
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(1, writes());
        replies.put("orders", page(json.createArrayNode().add(remote), 2, 1));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
    }

    @Test void creationDiscoveryIsBoundedAndChecksEveryPageForDuplicateReceipts() throws Exception {
        var prepared = gateway.prepare(provider, product, form());
        ArrayNode first = json.createArrayNode();
        for (int i = 1; i <= 100; i++) first.add(remote.deepCopy().put("id", String.valueOf(i)).put("yid", "old-" + i));
        when(http.postForString(eq(provider), contains("act=orders"), anyMap())).thenAnswer(inv -> {
            Map<String,Object> body = inv.getArgument(2);
            return Integer.valueOf(1).equals(body.get("page")) ? page(first, 1, 101)
                    : page(json.createArrayNode().add(remote.deepCopy().put("id", "101")), 2, 101);
        });
        assertEquals("101", gateway.execute(provider, product, order, "CREATE", prepared.fields()).externalSubOrderNo());
        assertEquals(1, writes());
        when(http.postForString(eq(provider), contains("act=orders"), anyMap())).thenReturn(page(first, 1, 501));
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        verify(http, times(2)).postForString(eq(provider), contains("act=add_order"), anyMap());
        verify(http, times(3)).postForString(eq(provider), contains("act=orders"), anyMap());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "11", "1.5", "false", "null", "\"02\""})
    void remainingMustBeAnExactMonotoneBoundedCount(String value) {
        replies.put("get_residue_num", "{\"code\":1,\"residue_num\":" + value + "}");
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertEquals(0, writes());
    }

    @Test void consumedRunsAreNotEvidenceOfCompletedScoresOrAutomaticRefunds() {
        replies.put("get_residue_num", "{\"code\":1,\"residue_num\":0}");
        var result = gateway.sync(provider, order); assertEquals(10, result.completed()); assertEquals("ACTIVE", result.status());
        remote.put("status", "-1"); assertEquals("ATTENTION", gateway.sync(provider, order).status());
        remote.put("status", "99"); assertEquals("ATTENTION", gateway.sync(provider, order).status());
        remote.put("status", "1"); order.setCompleted(5);
        replies.put("get_residue_num", "{\"code\":1,\"residue_num\":6}");
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertThrows(BusinessException.class, () -> gateway.refundRemaining(provider, order));
    }

    @Test void cancellationUsesTheBusinessIdAndOnlyRequestsManualRefundReview() {
        replies.put("get_residue_num", "{\"code\":1,\"residue_num\":7}");
        assertTrue(gateway.prepareAction(provider, order, "CANCEL", Map.of()).isEmpty());
        var result = gateway.execute(provider, product, order, "CANCEL", Map.of());
        assertEquals("REFUND_REVIEW", result.status()); assertEquals(3, result.completed()); assertNull(result.refundedUnits());
        assertEquals("yid-451", bodies.get(bodies.size()-1).get("orderId")); assertEquals(1, writes());
    }

    @Test void logsUseFixedStatusProjectionAndOnlyOwnedMutableTasksMayChange() {
        var logs = gateway.logs(provider, order, 1);
        assertEquals(List.of("待执行", "跑步结束", "需要关注", "待核对"), logs.items().stream().map(RunLog::status).toList());
        assertEquals(List.of(true, false, true, false), logs.items().stream().map(RunLog::editable).toList());
        assertEquals("2099-01-02 08:00:00", logs.items().get(1).endTime());
        assertFalse(logs.toString().contains("private"));
        Map<String,Object> prepared = gateway.prepareAction(provider, order, "CHANGE_TIME",
                Map.of("taskId", "task-1", "time", tomorrow(), "page", "1"));
        gateway.execute(provider, product, order, "CHANGE_TIME", prepared);
        assertEquals("task-1", bodies.get(bodies.size()-1).get("id"));
        assertEquals(tomorrow(), bodies.get(bodies.size()-1).get("runTime"));
        for (String task : List.of("task-2", "task-4", "foreign-task"))
            assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "CHANGE_TIME",
                    Map.of("taskId", task, "time", tomorrow(), "page", "1")));
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "CHANGE_TIME",
                Map.of("taskId", "task-1", "time", tomorrow(), "page", "2")));
        assertEquals(1, writes());
    }

    @Test void mutableTaskIsRecheckedAtDispatchAndDuplicateTaskIdsFailClosed() {
        var prepared = gateway.prepareAction(provider, order, "CHANGE_TIME", Map.of("taskId", "task-1", "time", tomorrow(), "page", "1"));
        replies.put("get_log", replies.get("get_log").replace("\"statusCode\":0", "\"statusCode\":1"));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CHANGE_TIME", prepared));
        replies.put("get_log", replies.get("get_log").replace("task-2", "task-1"));
        assertThrows(ProviderRequestException.class, () -> gateway.logs(provider, order, 1)); assertEquals(0, writes());
    }

    @Test void batchTimeIsAnUnchargedArrangementNotClaimedToBeCurrentDatabaseState() {
        var options = gateway.orderOptions(provider, order); assertTrue(options.notice().contains("下单时"));
        var plan = new LinkedHashMap<>(fields); plan.remove("account"); plan.remove("zoneId"); plan.put("startTime", "11:30"); plan.put("endTime", "12:00");
        var prepared = gateway.prepareAction(provider, order, "EDIT_PLAN", plan);
        assertEquals(1, prepared.get("weeks[0]")); assertFalse(prepared.containsKey("form[weeks][0]"));
        gateway.execute(provider, product, order, "EDIT_PLAN", prepared);
        assertEquals("batch_edit_time", actions.get(actions.size()-1));
        assertEquals("07:30:00", gateway.orderOptions(provider, order).suggested().get("startTime"));
    }

    @Test void scoreInformationIsBoundedPlainTextAndItsStringRepresentationIsRedacted() {
        var info = gateway.scoreInfo(provider, order); assertTrue(info.text().contains("\nhttps://score.example"));
        assertFalse(info.toString().contains("score.example"));
        for (String value : List.of("fixture-private-key", " ", "x".repeat(2001), "unsafe\u0000value")) {
            replies.put("get_auth_link", json.createObjectNode().put("code",1).put("msg", value).toString());
            assertThrows(ProviderRequestException.class, () -> gateway.scoreInfo(provider, order));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "[]", "null", "{\"code\":true}", "{\"code\":1.0}", "{\"code\":0}", "{\"code\":200}", "{\"code\":\"01\"}", "{\"code\":1,\"code\":1,\"data\":0.1}", "{\"code\":1,\"data\":0.1} {}"})
    void malformedResponsesAreNeverTreatedAsSuccess(String response) {
        replies.put("get_price", response);
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, "1"));
    }

    @Test void failureTextDepthAndByteLimitsCannotLeakSecretsOrSelectAnAlternateEndpoint() {
        for (String response : List.of("{\"code\":-1,\"msg\":\"fixture-private-key https://internal.invalid\"}", "x".repeat(262145),
                "{\"code\":1,\"data\":0.1,\"extra\":" + "[".repeat(30) + "0" + "]".repeat(30) + "}")) {
            replies.put("get_price", response);
            var failure = assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, "1"));
            assertFalse(failure.toString().contains("fixture-private-key")); assertFalse(failure.toString().contains("internal.invalid"));
        }
        assertEquals(3, actions.size());
        for (String endpoint : List.of("https://service.example/ldrun", "https://service.example/install/ldrun/api.php", "https://service.example/api.php", "https://service.example/install?act=add_order")) {
            provider.setApiUrl(endpoint);
            assertThrows(RuntimeException.class, () -> gateway.fetchCatalog(provider, "1"));
        }
        assertEquals(3, actions.size());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", "01", "name", "42\n", "1e2"})
    void invalidPurchaserIdentifiersCannotReachTransport(String user) {
        provider.setUsername(user);
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, "1")); verifyNoInteractions(http);
    }
    @ParameterizedTest @ValueSource(strings = {"remote-product", "quantity", "distance", "charge", "fields", "null-binding", "malformed-distance"})
    void incompleteCreateSnapshotsFailWithAControlledErrorBeforeHttp(String invalid) {
        var prepared = gateway.prepare(provider, product, form());
        Map<String,Object> body = prepared.fields();
        switch (invalid) {
            case "remote-product" -> order.setRemoteProductId("2");
            case "quantity" -> order.setQuantity(null);
            case "distance" -> order.setDistance(null);
            case "charge" -> order.setUnitCharge(null);
            case "fields" -> body = null;
            case "null-binding" -> order.setScheduleJson("null");
            case "malformed-distance" -> { body = new LinkedHashMap<>(body); body.put("form[mile]", "not-a-number"); }
            default -> throw new AssertionError(invalid);
        }
        Map<String,Object> snapshot = body;
        clearInvocations(http);
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", snapshot));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "99"})
    void creationPreservesAnObservedAttentionStateInsteadOfClaimingNormalExecution(String state) {
        remote.put("status", state);
        var prepared = gateway.prepare(provider, product, form());
        var result = gateway.execute(provider, product, order, "CREATE", prepared.fields());
        assertEquals("ATTENTION", result.status()); assertEquals(1, writes());
    }

    @Test
    void aMissingQuantityFailsClosedBeforeAnySensitiveRead() {
        order.setQuantity(null);
        assertThrows(BusinessException.class, () -> gateway.scoreInfo(provider, order));
        verifyNoInteractions(http);
    }

}
