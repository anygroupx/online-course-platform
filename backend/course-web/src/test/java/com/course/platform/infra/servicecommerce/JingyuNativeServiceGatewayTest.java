package com.course.platform.infra.servicecommerce;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.http.SafeHttpException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JingyuNativeServiceGatewayTest {
    private static final String PHONE = "13800138000";
    private static final String UID = "150123";
    private static final String PASSWORD = " fixture +&= 密码 ";
    private static final String ZONE = "操场 A&B + C=一层";
    private static final Set<String> WRITES = Set.of("keep_add", "bdlp_add", "refund", "change_run_status",
            "edit_task", "delay_task", "fast_delay_task");
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> replies = new HashMap<>();
    private final List<String> actions = new ArrayList<>(), urls = new ArrayList<>();
    private final List<Map<String, Object>> bodies = new ArrayList<>();
    private ApiHttpClient http;
    private JingyuNativeServiceGateway gateway;
    private ApiProvider provider;
    private ServiceProduct product;
    private ServiceOrder order;
    private ObjectNode remote, student;
    private ArrayNode taskRows, zones;
    private Map<String, String> fields;
    private List<String> times;

    @BeforeEach
    void setup() throws Exception {
        http = mock(ApiHttpClient.class);
        gateway = new JingyuNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider(); provider.setProviderType("jingyu"); provider.setUsername("42");
        provider.setApiKey("fixture-private-key"); provider.setApiUrl("https://service.example/install");
        product = new ServiceProduct(); product.setProviderType("jingyu"); product.setUnitPrice(new BigDecimal("0.50"));
        order = new ServiceOrder(); order.setProviderType("jingyu"); order.setExternalSubOrderNo("17");
        order.setQuantity(3); order.setCompleted(0); order.setDistance(new BigDecimal("1.5"));
        order.setUnitCharge(new BigDecimal("0.75"));
        times = List.of(time(1), time(2), time(3));
        zones = json.createArrayNode().add(json.createObjectNode().put("zone_id", 7).put("name", ZONE));
        taskRows = json.createArrayNode();
        for (int i = 0; i < 3; i++) taskRows.add(task("task-" + (i + 1), times.get(i), "未开始"));
        taskRows.get(1).withObject("").put("status_display", "成功").put("end_time", time(2).replace("07:30", "08:00"));
        taskRows.get(2).withObject("").put("status_display", "退款");
        selectProject("keep");
        replies.put("get_price", "{\"code\":1,\"data\":\"0.01\"}");
        replies.put("get_remain_count", "{\"code\":1,\"data\":{\"refund_cnt\":2}}");
        for (String action : WRITES) replies.put(action, "{\"code\":1}");
        replies.put("keep_add", "{\"code\":1,\"data\":{\"id\":17,\"keep_order_id\":\"keep-451\"}}");
        replies.put("bdlp_add", "{\"code\":1,\"data\":{\"id\":17,\"bdlp_order_id\":\"bdlp-451\"}}");
        when(http.postForString(eq(provider), anyString(), anyMap())).thenAnswer(inv -> {
            String url = inv.getArgument(1); Map<String, Object> body = inv.getArgument(2);
            String action = url.substring(url.indexOf("&act=") + 5);
            urls.add(url); actions.add(action); bodies.add(new LinkedHashMap<>(body));
            if (replies.containsKey(action)) return replies.get(action);
            return switch (action) {
                case "orders" -> page(json.createArrayNode().add(remote), 1, 20, 1, 1);
                case "get_task_data" -> data(json.createObjectNode().set("list", taskRows));
                case "get_keep_user_info", "get_bdlp_user_info" -> data(json.createObjectNode().set("student", student));
                case "get_keep_zone_data", "get_bdlp_zone_data" -> data(json.createObjectNode().set("list", zones));
                default -> throw new AssertionError("Unexpected fixed action " + action);
            };
        });
    }

    private void selectProject(String project) throws Exception {
        boolean bdlp = "bdlp".equals(project);
        product.setProject(project); product.setRemoteProductId(project);
        order.setProject(project); order.setRemoteProductId(project); order.setExternalOrderNo(project + "-451");
        order.setScheduleJson(json.writeValueAsString(ServiceAccountFingerprint.create(bdlp ? UID : PHONE)));
        fields = new LinkedHashMap<>(bdlp ? Map.of("account", UID, "zoneId", "7", "runType", "1")
                : Map.of("account", PHONE, "password", PASSWORD, "zoneId", "7", "minMinute", "4", "maxMinute", "12"));
        remote = json.createObjectNode().put("id", "17").put(project + "_order_id", project + "-451")
                .put("uid", "42").put("user", bdlp ? UID : PHONE).put("num", "3").put("distance", "1.5")
                .put("zone_id", "7").put("zone_name", ZONE).put("status_display", "正常").put("pause", "1")
                .put("min_minute", "4").put("max_minute", "12").put("school_name", "示例学院")
                .put("run_type", "1").put("is_auth", "1").put("auth_type", "设备授权")
                .put("auth_time", "2026-01-01 08:00:00");
        student = json.createObjectNode();
        if (bdlp) {
            student.put("uid", UID); student.putObject("school").put("name", "示例学院");
            student.putObject("device").put("is_expired", false).put("login_type_display", "设备授权")
                    .put("refresh_at", "2026-01-01 08:00:00");
            student.putObject("run_rule").put("min_dis", "1.5");
        } else {
            student.put("student_id", "81").put("phone", PHONE).put("default_zone_id", "7");
            student.putObject("default_zone").put("zone_id", "7").put("name", "示例跑区");
        }
    }

    private ObjectNode task(String id, String time, String status) {
        return json.createObjectNode().put("run_task_id", id).put("start_time", time).put("status_display", status);
    }
    private String data(com.fasterxml.jackson.databind.JsonNode data) {
        return json.createObjectNode().put("code", 1).set("data", data).toString();
    }
    private String page(ArrayNode rows, int page, int limit, int lastPage, int total) {
        ObjectNode root = json.createObjectNode().put("code", 1); root.set("data", rows);
        root.putObject("pagination").put("page", page).put("limit", limit).put("last_page", lastPage).put("total", total);
        return root.toString();
    }
    private String time(int days) { return ServiceTime.now().toLocalDate().plusDays(days) + " 07:30:00"; }
    private OrderForm form() { return new OrderForm(order.getQuantity(), order.getDistance(), fields, times, true); }
    private long writes() { return actions.stream().filter(WRITES::contains).count(); }
    private Map<String, Object> body(String action) { return bodies.get(actions.lastIndexOf(action)); }
    private Map<String, String> lookupFields() {
        return "keep".equals(product.getProject()) ? Map.of("account", PHONE, "password", PASSWORD) : Map.of("account", UID);
    }

    @ParameterizedTest @ValueSource(strings = {"keep", "bdlp"})
    void preparesAnExplicitPlanAndCreatesOnceWithTwoSeparatelyVerifiedReceipts(String project) throws Exception {
        selectProject(project);
        PreparedOrder prepared = gateway.prepare(provider, product, form());
        assertTrue(prepared.accountFingerprint().matches(fields.get("account")));
        assertFalse(prepared.accountLabel().contains(fields.get("account")));
        assertFalse(prepared.toString().contains(PASSWORD));
        assertEquals(times.get(2), prepared.fields().get("form[task_list][2][start_time]"));
        assertEquals(ZONE, prepared.fields().get("form[zone_name]"));
        assertEquals("1.5", prepared.fields().get("form[dis]"));
        assertFalse(prepared.fields().containsKey("form"));
        assertFalse(prepared.fields().containsKey("login_key"));
        if ("keep".equals(project)) {
            assertEquals(PASSWORD, prepared.fields().get("form[password]"));
            assertEquals("81", prepared.fields().get("form[student_id]"));
            assertEquals("1", prepared.fields().get("form[run_type]"));
            assertEquals("示例跑区", body("get_keep_zone_data").get("school"));
        } else {
            assertEquals(UID, prepared.fields().get("form[student_id]"));
            assertEquals("false", prepared.fields().get("form[is_jrxy]"));
            assertEquals("1", prepared.fields().get("form[is_auth]"));
            assertEquals("设备授权", prepared.fields().get("form[auth_type]"));
            assertEquals("2026-01-01 08:00:00", prepared.fields().get("form[auth_time]"));
            assertFalse(prepared.fields().containsKey("form[password]"));
        }
        RemoteResult result = gateway.execute(provider, product, order, "CREATE", prepared.fields());
        assertEquals(project + "-451", result.externalOrderNo()); assertEquals("17", result.externalSubOrderNo());
        assertEquals("ACTIVE", result.status()); assertEquals(1, result.completed()); assertNull(result.refundedUnits());
        assertEquals(1, writes());
        assertEquals(Map.of("type", "1", "keywords", "17", "page", "1", "limit", "20",
                "login_uid", "42", "login_key", "fixture-private-key"), body("orders"));
        assertEquals(project + "-451", body("get_task_data").get(project + "_order_id"));
        for (int i = 0; i < urls.size(); i++) {
            assertEquals("https://service.example/install/jingyu/api.php?appId=" + project + "&act=" + actions.get(i), urls.get(i));
            assertFalse(urls.get(i).contains("fixture"));
            assertEquals("42", bodies.get(i).get("login_uid"));
            assertEquals("fixture-private-key", bodies.get(i).get("login_key"));
        }
    }

    @Test
    void catalogAndPricingDoNotApplyTheOtherAdaptersMileageCapOrEndOnlyRounding() {
        var catalog = gateway.fetchCatalog(provider, null);
        assertEquals(List.of("keep", "bdlp", "yyd"), catalog.stream().map(item -> item.id()).toList());
        assertEquals(List.of("元/次·公里", "元/次", "元/次·公里"), catalog.stream().map(item -> item.priceUnit()).toList());
        BigDecimal rate = new BigDecimal("0.01"), distance = new BigDecimal("1.5");
        assertEquals(new BigDecimal("0.02"), JingyuNativeServiceGateway.unitCharge(rate, "keep", distance));
        assertEquals(new BigDecimal("0.06"), JingyuNativeServiceGateway.unitCharge(rate, "keep", distance).multiply(new BigDecimal("3")));
        assertEquals(new BigDecimal("0.01"), JingyuNativeServiceGateway.unitCharge(rate, "bdlp", new BigDecimal("100")));
        assertEquals(new BigDecimal("1.00"), JingyuNativeServiceGateway.unitCharge(rate, "keep", new BigDecimal("100")));
        assertEquals(new BigDecimal("1.5"), JingyuNativeServiceGateway.billable("keep", distance));
        assertEquals(BigDecimal.ONE, JingyuNativeServiceGateway.billable("bdlp", distance));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"ymty", "KEEP", "unknown", "keep&act=refund"})
    void unimplementedProjectsCannotBeAdvertisedOrDispatched(String project) {
        assertFalse(JingyuNativeServiceGateway.supported(project, project));
        assertThrows(BusinessException.class, () -> gateway.fetchCatalog(provider, project));
        product.setProject(project); product.setRemoteProductId(project);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "0.9", "-1", "100.1", "1.01", "100000"})
    void invalidDistancesFailBeforeTransport(String distance) {
        order.setDistance(distance == null ? null : new BigDecimal(distance));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"-0.01", "10000", "0.0000001"})
    void invalidPricesAreRejected(String rate) {
        assertThrows(BusinessException.class, () -> JingyuNativeServiceGateway.unitCharge(new BigDecimal(rate), "keep", BigDecimal.ONE));
    }

    @ParameterizedTest @ValueSource(strings = {"price", "login_uid", "login_key", "student_id", "is_auth", "auth_time", "school_name", "zone_name", "action", "url"})
    void clientCannotForgeDerivedFieldsOrCredentials(String key) {
        fields.put(key, "forged");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @CsvSource({"account,138", "account,13800138000x", "zoneId,8", "minMinute,2", "minMinute,7", "minMinute,04", "maxMinute,7", "maxMinute,16"})
    void invalidInputsNeverDispatchAWrite(String key, String value) {
        fields.put(key, value);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "bad\npassword"})
    void invalidPasswordsFailClosedWithoutTransport(String password) {
        fields.put("password", password);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @Test
    void taskCountAuthorizationAndAlternativeSchedulingCannotBypassTheExplicitPlan() {
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, null));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product,
                new OrderForm(3, order.getDistance(), fields, times, false)));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product,
                new OrderForm(3, order.getDistance(), fields, times, true, null, UUID.randomUUID().toString())));
        for (int count : List.of(0, 2, 366)) assertThrows(BusinessException.class, () -> gateway.prepare(provider, product,
                new OrderForm(count, order.getDistance(), fields, times, true)));
        times = Arrays.asList(time(1), time(1), time(2));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        times = null;
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"2026-02-30 07:30:00", "2099-01-01 07:30:00", "2020-01-01 07:30:00", "2026-09-12T07:30:00", "2026-09-12 24:00:00"})
    void taskTimestampsAreStrictAndBounded(String time) {
        times = Arrays.asList(time, time(2), time(3));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "01", "1e2", "150123x", "99999999999999999999"})
    void bdlpUidMustBeCanonical(String account) throws Exception {
        selectProject("bdlp"); fields.put("account", account);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @Test
    void authorizationIsDerivedFromTheAccountAndExpirationCannotBeOverridden() throws Exception {
        selectProject("bdlp"); student.withObject("/device").put("is_expired", true);
        var lookup = gateway.lookup(provider, product, lookupFields());
        assertEquals("EXPIRED", lookup.suggested().get("authorizationState"));
        assertEquals("1.5", lookup.suggested().get("minDistance"));
        assertFalse(lookup.toString().contains(UID));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"keep", "bdlp"})
    void aMismatchedAccountResponseCannotAuthorizeThePlan(String project) throws Exception {
        selectProject(project); student.put("bdlp".equals(project) ? "uid" : "phone", "9999999");
        assertThrows(ProviderRequestException.class, () -> gateway.lookup(provider, product, lookupFields()));
        assertEquals(List.of("get_" + project + "_user_info"), actions);
    }

    @Test
    void duplicateZonesOrAnInconsistentDefaultZoneFailClosed() {
        zones.add(zones.get(0).deepCopy());
        assertThrows(ProviderRequestException.class, () -> gateway.prepare(provider, product, form()));
        zones.remove(1); student.put("default_zone_id", "8");
        assertThrows(ProviderRequestException.class, () -> gateway.lookup(provider, product, lookupFields()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"auth-time", "auth-type", "expiration", "zone-name"})
    void anAccountOrAuthorizationChangeAfterPreviewStopsCreation(String change) throws Exception {
        selectProject("bdlp"); var prepared = gateway.prepare(provider, product, form());
        switch (change) {
            case "auth-time" -> student.withObject("/device").put("refresh_at", "2026-01-02 08:00:00");
            case "auth-type" -> student.withObject("/device").put("login_type_display", "其他授权");
            case "expiration" -> student.withObject("/device").put("is_expired", true);
            case "zone-name" -> zones.get(0).withObject("").put("name", "新跑区");
        }
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(0, writes());
    }

    @Test
    void priceDriftIsRecheckedAtDispatchWithoutRequotingOrWriting() {
        var prepared = gateway.prepare(provider, product, form());
        replies.put("get_price", "{\"code\":1,\"data\":\"0.99\"}");
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"account", "extra", "distance", "quantity", "product", "binding", "charge", "completed"})
    void forgedOrIncompleteSnapshotsCannotBeDispatched(String change) {
        var prepared = gateway.prepare(provider, product, form());
        var snapshot = new LinkedHashMap<>(prepared.fields());
        switch (change) {
            case "account" -> snapshot.put("form[phone]", "13900139000");
            case "extra" -> snapshot.put("form[is_auth]", "1");
            case "distance" -> order.setDistance(null);
            case "quantity" -> order.setQuantity(null);
            case "product" -> order.setRemoteProductId("bdlp");
            case "binding" -> order.setScheduleJson(null);
            case "charge" -> order.setUnitCharge(BigDecimal.ZERO);
            case "completed" -> order.setCompleted(null);
        }
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> gateway.execute(provider, product, order, "CREATE", snapshot));
        assertTrue(error instanceof BusinessException || error instanceof ProviderRequestException);
        assertEquals(0, writes());
    }

    @ParameterizedTest @CsvSource({"id,18", "keep_order_id,other-451", "uid,43", "user,13900139000", "num,4", "distance,1.6"})
    void bothReceiptsPurchaserAccountQuantityAndDistanceMustMatchBeforeReadingTasksOrWriting(String key, String value) {
        remote.put(key, value);
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertThrows(ProviderRequestException.class, () -> gateway.logs(provider, order, 1));
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "PAUSE", Map.of()));
        assertEquals(0, writes()); assertFalse(actions.contains("get_task_data"));
    }

    @ParameterizedTest @ValueSource(strings = {"business-id", "row-id", "binding"})
    void aSingleReceiptOrAnAbsentAccountBindingDoesNotAuthorizeAnOrder(String change) {
        switch (change) {
            case "business-id" -> order.setExternalOrderNo(null);
            case "row-id" -> order.setExternalSubOrderNo(null);
            case "binding" -> order.setScheduleJson("{}");
        }
        RuntimeException error = assertThrows(RuntimeException.class, () -> gateway.sync(provider, order));
        assertTrue(error instanceof BusinessException || error instanceof ProviderRequestException);
        assertFalse(actions.contains("get_task_data")); assertEquals(0, writes());
    }

    @ParameterizedTest @CsvSource({"2,20,1,1", "1,10,1,1", "1,20,2,1", "1,20,1,2"})
    void inconsistentPaginationNeverBecomesAnExactMatch(int page, int limit, int last, int total) {
        replies.put("orders", page(json.createArrayNode().add(remote), page, limit, last, total));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertFalse(actions.contains("get_task_data"));
    }

    @Test
    void duplicateOrMissingRowsDoNotTriggerBroadAccountOrPasswordSearch() {
        replies.put("orders", page(json.createArrayNode().add(remote).add(remote.deepCopy()), 1, 20, 1, 2));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        replies.put("orders", page(json.createArrayNode(), 1, 20, 0, 0));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertEquals(List.of("orders", "orders"), actions);
        assertTrue(bodies.stream().allMatch(body -> "1".equals(body.get("type")) && "17".equals(body.get("keywords"))));
    }

    @ParameterizedTest @ValueSource(strings = {"id", "keep_order_id", "zone_id", "zone_name", "min_minute", "max_minute"})
    void anUncertainCreateReceiptDoesNotCauseASecondWrite(String field) {
        var prepared = gateway.prepare(provider, product, form());
        remote.remove(field);
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(1, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"id\":17}", "{\"keep_order_id\":\"keep-451\"}", "{\"id\":\"017\",\"keep_order_id\":\"keep-451\"}"})
    void missingOrMalformedCreateIdsStayUncertainWithoutResubmission(String data) {
        var prepared = gateway.prepare(provider, product, form());
        replies.put("keep_add", "{\"code\":1,\"data\":" + data + "}");
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(1, writes()); assertFalse(actions.contains("orders"));
    }

    @Test
    void aReadFailureAfterACreateStillNeverRetriesTheMutation() {
        var prepared = gateway.prepare(provider, product, form()); replies.put("get_task_data", "invalid fixture response");
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(1, writes());
    }

    @ParameterizedTest @CsvSource({"正常,1,ACTIVE", "正常,0,PAUSED", "部分失败,1,ATTENTION", "全部完成,1,ATTENTION", "已退款,1,REFUND_REVIEW", "未识别状态,1,ATTENTION"})
    void syncUsesTaskSuccessEvidenceAndDoesNotEquateRefundsToCompletion(String display, String pause, String state) {
        remote.put("status_display", display).put("pause", pause).put("refund_cnt", 3);
        var result = gateway.sync(provider, order);
        assertEquals(state, result.status()); assertEquals(1, result.completed()); assertNull(result.refundedUnits());
        assertFalse(actions.contains("get_remain_count"));
        order.setCompleted(2);
        assertEquals(2, gateway.sync(provider, order).completed());
    }

    @Test
    void allCompletedRequiresEnoughSuccessfulTasksAndUnknownTaskStatesStayConservative() {
        remote.put("status_display", "全部完成");
        taskRows.forEach(task -> task.withObject("").put("status_display", "成功"));
        assertEquals("COMPLETED", gateway.sync(provider, order).status());
        taskRows.get(2).withObject("").put("status_display", "完成");
        assertEquals("ATTENTION", gateway.sync(provider, order).status());
        var logs = gateway.logs(provider, order, 1);
        assertEquals("需要关注", logs.items().get(2).status()); assertFalse(logs.items().get(2).editable());
    }

    @ParameterizedTest @ValueSource(strings = {"duplicate", "overflow", "bad-time", "bad-id"})
    void ambiguousTaskListsFailClosed(String change) {
        switch (change) {
            case "duplicate" -> taskRows.get(2).withObject("").put("run_task_id", "task-1");
            case "overflow" -> taskRows.add(task("task-4", time(4), "未开始"));
            case "bad-time" -> taskRows.get(0).withObject("").put("start_time", "2026-02-30 08:00:00");
            case "bad-id" -> taskRows.get(0).withObject("").put("run_task_id", "../other");
        }
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "PAUSE", Map.of()));
        assertEquals(0, writes());
    }

    @Test
    void editableTasksMustBelongToTheRequestedPageAndAreRecheckedAtDispatch() {
        order.setQuantity(21); remote.put("num", 21); taskRows.removeAll();
        for (int i = 1; i <= 21; i++) taskRows.add(task("task-" + i, time(i), "未开始"));
        var first = gateway.logs(provider, order, 1); var second = gateway.logs(provider, order, 2);
        assertEquals(20, first.items().size()); assertTrue(first.hasMore());
        assertEquals(1, second.items().size()); assertFalse(second.hasMore());
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "DELAY_TASK", Map.of("taskId", "task-21", "page", "1")));
        var prepared = gateway.prepareAction(provider, order, "DELAY_TASK", Map.of("taskId", "task-21", "page", "2"));
        taskRows.get(20).withObject("").put("status_display", "成功");
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "DELAY_TASK", prepared));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"CHANGE_TIME", "DELAY_TASK"})
    void singleTaskActionsUseTheBusinessReceiptAndNeverAcceptClientChosenParameters(String action) {
        var input = new LinkedHashMap<>(Map.of("taskId", "task-1", "page", "1"));
        if ("CHANGE_TIME".equals(action)) input.put("time", time(4));
        var prepared = gateway.prepareAction(provider, order, action, input);
        assertFalse(prepared.containsKey("page"));
        gateway.execute(provider, product, order, action, prepared);
        var sent = body("CHANGE_TIME".equals(action) ? "edit_task" : "delay_task");
        assertEquals("keep-451", sent.get("keep_order_id")); assertFalse(sent.containsKey("id"));
        if ("CHANGE_TIME".equals(action)) {
            assertEquals("task-1", sent.get("form[run_task_id]")); assertEquals(time(4), sent.get("form[start_time]"));
        } else assertEquals("task-1", sent.get("run_task_id"));
        assertEquals(1, writes());
        input.put("id", "other");
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, action, input));
        var forged = new LinkedHashMap<>(prepared); forged.put("id", "other");
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, action, forged));
        assertEquals(1, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "20", "01", "2.0", "-1"})
    void invalidTaskPagesNeverDispatchAWritingAction(String page) {
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "DELAY_TASK", Map.of("taskId", "task-1", "page", page)));
        assertEquals(0, writes());
    }

    @Test
    void pauseAndResumeUseTheLocalRowIdWithOneAndZeroInTheCorrectDirection() {
        assertEquals("PAUSED", gateway.execute(provider, product, order, "PAUSE", Map.of()).status());
        assertEquals("0", body("change_run_status").get("status")); assertEquals("17", body("change_run_status").get("id"));
        remote.put("pause", "0");
        assertEquals("ACTIVE", gateway.execute(provider, product, order, "RESUME", Map.of()).status());
        assertEquals("1", body("change_run_status").get("status"));
        assertEquals(2, writes());
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "PAUSE", Map.of()));
        assertEquals(2, writes());
    }

    @ParameterizedTest @CsvSource({"PAUSE,1", "RESUME,0"})
    void changingRunStateDoesNotHideAnExistingPartialFailure(String action, String pause) {
        remote.put("status_display", "部分失败").put("pause", pause);
        assertEquals("ATTENTION", gateway.execute(provider, product, order, action, Map.of()).status());
        assertEquals(1, writes());
    }

    @ParameterizedTest @CsvSource({"PAUSE,1,正常", "RESUME,0,正常", "DELAY,1,部分失败", "DELAY,0,部分失败"})
    void acknowledgingAnActionDoesNotMakeAnExpiredBdlpAuthorizationLookValid(String action, String pause, String display) throws Exception {
        selectProject("bdlp"); remote.put("pause", pause).put("status_display", display).put("is_auth", "0");
        assertEquals("ATTENTION", gateway.sync(provider, order).status());
        assertEquals("ATTENTION", gateway.execute(provider, product, order, action, Map.of()).status());
        assertEquals(1, writes());
    }

    @ParameterizedTest @CsvSource({"0,PAUSED", "1,ACTIVE"})
    void batchDelayRequiresPartialFailureAndPreservesPauseState(String pause, String state) {
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "DELAY", Map.of()));
        remote.put("status_display", "部分失败").put("pause", pause);
        assertEquals(Map.of(), gateway.prepareAction(provider, order, "DELAY", Map.of()));
        assertEquals(state, gateway.execute(provider, product, order, "DELAY", Map.of()).status());
        assertEquals("17", body("fast_delay_task").get("id")); assertEquals(1, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"全部完成", "已退款", "未知"})
    void terminalOrUnknownStatesCannotBeMutated(String state) {
        remote.put("status_display", state);
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "REFUND", Map.of()));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        assertEquals(0, writes());
    }

    @Test
    void refundPrequeryIsNotAnAtomicReceiptAndCannotAuthorizeAutomaticCredit() {
        assertEquals(2, gateway.refundRemaining(provider, order));
        var result = gateway.execute(provider, product, order, "REFUND", Map.of());
        assertEquals("REFUND_REVIEW", result.status()); assertEquals(1, result.completed());
        assertNull(result.refundedUnits()); assertEquals("17", body("refund").get("id"));
        assertFalse(body("refund").containsKey("quantity")); assertEquals(1, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "4", "1.5", "false", "null", "\"02\""})
    void remainingCountMustBeAnExactBoundedInteger(String value) {
        replies.put("get_remain_count", "{\"code\":1,\"data\":{\"refund_cnt\":" + value + "}}");
        assertThrows(ProviderRequestException.class, () -> gateway.refundRemaining(provider, order));
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        assertEquals(0, writes());
    }

    @Test
    void zeroRemainingAtDispatchMustNotSendAnUnnecessaryRefund() {
        replies.put("get_remain_count", "{\"code\":1,\"data\":{\"refund_cnt\":0}}");
        assertEquals(0, gateway.refundRemaining(provider, order));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        assertEquals(0, writes());
    }

    @ParameterizedTest @ValueSource(strings = {"ADD_TIMES", "EDIT_PLAN", "CANCEL", "REPORT", "unknown"})
    void unimplementedActionsDoNotProbeOrWrite(String action) {
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, action, Map.of()));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, action, Map.of()));
        assertThrows(BusinessException.class, () -> gateway.checkAddTimes(provider, order, 1));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "[]", "null", "{\"code\":true}", "{\"code\":1.0}", "{\"code\":0}", "{\"code\":200}", "{\"code\":\"01\"}", "{\"code\":1,\"code\":1,\"data\":0.01}", "{\"code\":1,\"data\":0.01} {}"})
    void malformedOrAmbiguousEnvelopesAreNotSuccess(String response) {
        replies.put("get_price", response);
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, "keep"));
    }

    @Test
    void responseLimitsAndErrorsNeverLeakRawSupplierContent() {
        for (String response : List.of("{\"code\":-1,\"msg\":\"fixture-private-key 密码\"}",
                "{\"code\":1,\"data\":\"" + "密".repeat(90000) + "\"}",
                "{\"code\":1,\"data\":" + "[".repeat(17) + "0" + "]".repeat(17) + "}")) {
            replies.put("get_price", response);
            var error = assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, "keep"));
            assertFalse(error.getMessage().contains("fixture-private-key")); assertFalse(error.getMessage().contains("密码"));
        }
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"0", "01", "name", "42\n", "1e2"})
    void invalidPurchaserIdentifiersNeverReachTransport(String user) {
        provider.setUsername(user);
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, "keep"));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"/jingyu", "/JINGYU/", "/jingyu/api.php", "/install/api.php", "/install/jingyu/nested"})
    void aSavedPluginEndpointCannotBeMistakenForAnInstallationRoot(String path) {
        provider.setApiUrl("https://service.example" + path);
        assertThrows(SafeHttpException.class, () -> gateway.fetchCatalog(provider, "keep"));
        verifyNoInteractions(http);
    }
}
