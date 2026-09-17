package com.course.platform.infra.servicecommerce;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic protocol fixtures only; no archive code or external service is executed. */
class JingyuSchoolGatewayTest {
    static final String SCHOOL_ID = "9007199254740993", RULE_ID = "9007199254740995";
    static final String ACCOUNT = "001_test-25", PASSWORD = "  exact +&= password  ", SCHOOL = "测试运动学院", ZONE = "东校区";
    final ObjectMapper json = new ObjectMapper();
    final List<String> actions = new ArrayList<>();
    final Map<String, Map<String, Object>> bodies = new HashMap<>();
    ApiHttpClient http;
    JingyuNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;
    ObjectNode student, row;
    ArrayNode schools, rules, tasks;
    Map<String, String> fields;
    List<String> times;
    int writes;

    @BeforeEach
    void setup() throws Exception {
        http = mock(ApiHttpClient.class);
        gateway = new JingyuNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider(); provider.setProviderType("jingyu"); provider.setUsername("42");
        provider.setApiKey("synthetic-secret"); provider.setApiUrl("https://service.example/install");
        product = new ServiceProduct(); product.setProviderType("jingyu"); product.setProject("yyd");
        product.setRemoteProductId("yyd"); product.setUnitPrice(new BigDecimal("0.01"));
        fields = new LinkedHashMap<>(Map.of("account", ACCOUNT, "password", PASSWORD, "schoolId", SCHOOL_ID,
                "schoolName", SCHOOL, "runRuleId", RULE_ID));
        times = List.of(time(1), time(2), time(3));
        schools = json.createArrayNode().add(school(SCHOOL_ID, SCHOOL));
        rules = json.createArrayNode().add(rule(RULE_ID, "7", ZONE, "1.5"));
        student = json.createObjectNode().put("student_id", "91").put("school_id", SCHOOL_ID).put("number", ACCOUNT);
        student.set("run_rule_items", rules);
        row = json.createObjectNode().put("id", "17").put("yyd_order_id", "yyd-451").put("uid", "42")
                .put("user", ACCOUNT).put("pass", PASSWORD).put("school_name", SCHOOL).put("run_rule_item_id", RULE_ID)
                .put("zone_name", ZONE).put("num", "3").put("distance", "1.5").put("status_display", "正常").put("pause", "1");
        tasks = json.createArrayNode();
        for (int i = 0; i < 3; i++) tasks.add(json.createObjectNode().put("run_task_id", "task-" + i)
                .put("start_time", times.get(i)).put("status_display", i == 0 ? "成功" : "未开始"));
        order = new ServiceOrder(); order.setProviderType("jingyu"); order.setProject("yyd"); order.setRemoteProductId("yyd");
        order.setExternalOrderNo("yyd-451"); order.setExternalSubOrderNo("17"); order.setQuantity(3); order.setCompleted(0);
        order.setDistance(new BigDecimal("1.5")); order.setUnitCharge(new BigDecimal("0.02"));
        when(http.postForString(eq(provider), anyString(), anyMap())).thenAnswer(inv -> {
            String url = inv.getArgument(1);
            assertTrue(url.startsWith("https://service.example/install/jingyu/api.php?appId=yyd&act="));
            assertFalse(url.contains(PASSWORD)); assertFalse(url.contains("synthetic-secret"));
            String action = url.substring(url.indexOf("&act=") + 5);
            Map<String, Object> body = inv.getArgument(2); actions.add(action); bodies.put(action, new LinkedHashMap<>(body));
            assertEquals("42", body.get("login_uid")); assertEquals("synthetic-secret", body.get("login_key"));
            return switch (action) {
                case "get_school_data" -> data(json.createObjectNode().set("list", schools));
                case "get_yyd_user_info" -> data(json.createObjectNode().set("student", student));
                case "get_price" -> "{\"code\":1,\"data\":\"0.01\"}";
                case "orders" -> {
                    ObjectNode response = json.createObjectNode().put("code", 1);
                    response.putArray("data").add(row);
                    response.putObject("pagination").put("page", 1).put("limit", 20).put("last_page", 1).put("total", 1);
                    yield response.toString();
                }
                case "get_task_data" -> data(json.createObjectNode().set("list", tasks));
                case "get_remain_count" -> "{\"code\":1,\"data\":{\"refund_cnt\":2}}";
                case "yyd_add" -> { writes++; yield "{\"code\":1,\"data\":{\"id\":17,\"yyd_order_id\":\"yyd-451\"}}"; }
                case "refund", "change_run_status", "edit_task", "delay_task", "fast_delay_task" -> { writes++; yield "{\"code\":1}"; }
                default -> throw new AssertionError("Unexpected action: " + action);
            };
        });
    }

    String time(int days) { return ServiceTime.now().toLocalDate().plusDays(days) + " 08:01:02"; }
    ObjectNode school(String id, String name) { return json.createObjectNode().put("school_id", id).put("name", name); }
    ObjectNode rule(String id, String zone, String name, String min) {
        ObjectNode result = json.createObjectNode().put("run_rule_item_id", id).put("min_dis", min);
        result.putObject("zone").put("zone_id", zone).put("name", name); return result;
    }
    String data(JsonNode data) { return json.createObjectNode().put("code", 1).set("data", data).toString(); }
    Map<String, String> lookupFields() { Map<String, String> result = new LinkedHashMap<>(fields); result.remove("runRuleId"); return result; }
    OrderForm form() { return new OrderForm(3, order.getDistance(), fields, times, true); }
    PreparedOrder prepare() throws Exception {
        PreparedOrder prepared = gateway.prepare(provider, product, form());
        order.setScheduleJson(json.writeValueAsString(prepared.accountFingerprint())); return prepared;
    }

    @Test
    void schoolSearchPagesTheBoundedResponseAndNeverGuessesAnAccountOrMakesAWritingRequest() {
        schools.removeAll();
        for (int i = 1; i <= 21; i++) schools.add(school(String.valueOf(i), "测试学院" + i));
        var first = gateway.schools(provider, product, 1, " 测试 ");
        assertEquals(20, first.items().size()); assertTrue(first.hasMore()); assertEquals(20, first.pageSize());
        assertEquals("测试", bodies.get("get_school_data").get("school"));
        var second = gateway.schools(provider, product, 2, "测试");
        assertEquals("21", second.items().get(0).id()); assertFalse(second.hasMore());
        assertTrue(gateway.schools(provider, product, 3, "测试").items().isEmpty());
        assertEquals(List.of("get_school_data", "get_school_data", "get_school_data"), actions);
        assertEquals(0, writes);
    }

    @Test
    void emptySearchAndUnsupportedPagesFailBeforeTransport() {
        for (String keyword : Arrays.asList(null, "", " ", "学\n院", "学".repeat(81)))
            assertThrows(BusinessException.class, () -> gateway.schools(provider, product, 1, keyword));
        for (int page : new int[]{0, -1, 11})
            assertThrows(BusinessException.class, () -> gateway.schools(provider, product, page, SCHOOL));
        verifyNoInteractions(http);
    }

    @Test
    void lookupProjectsAllDistinctRulesWithoutPickingTheFirstOrEchoingSensitiveStudentData() {
        rules.add(rule("22", "7", ZONE, "2"));
        student.put("password", "never-echo").put("name", "private-student");
        var lookup = gateway.lookup(provider, product, lookupFields());
        assertEquals(Map.of("schoolId", SCHOOL_ID, "schoolName", SCHOOL), lookup.suggested());
        assertEquals(List.of(RULE_ID, "22"), lookup.choices().stream().map(Choice::value).toList());
        assertEquals("runRuleId", lookup.choices().get(0).field());
        assertEquals(List.of(new SchoolRunRule(RULE_ID, "7", ZONE, "1.5"), new SchoolRunRule("22", "7", ZONE, "2")), lookup.schoolRules());
        assertEquals(PASSWORD, bodies.get("get_yyd_user_info").get("password"));
        assertEquals(SCHOOL_ID, bodies.get("get_yyd_user_info").get("school_id"));
        assertFalse(lookup.toString().contains(PASSWORD)); assertFalse(lookup.toString().contains("private-student"));
        assertEquals(0, writes);
    }

    @Test
    void schoolIdentityCannotBeForgedByChangingTheNameOrReusingASameNameSchoolId() {
        fields.put("schoolId", "1");
        assertThrows(BusinessException.class, () -> gateway.lookup(provider, product, lookupFields()));
        assertFalse(actions.contains("get_yyd_user_info"));
        fields.put("schoolId", SCHOOL_ID); schools.add(school("2", SCHOOL));
        assertThrows(BusinessException.class, () -> gateway.lookup(provider, product, lookupFields()));
        assertFalse(actions.contains("get_yyd_user_info"));
        assertEquals(0, writes);
    }

    @ParameterizedTest @ValueSource(strings = {"school_id", "number"})
    void anExplicitStudentIdentityContradictionIsRejected(String key) {
        student.put(key, "123");
        assertThrows(ProviderRequestException.class, () -> gateway.lookup(provider, product, lookupFields()));
        assertEquals(0, writes);
    }

    @Test
    void duplicateMalformedAndMissingRulesFailClosed() {
        rules.add(rules.get(0));
        assertThrows(ProviderRequestException.class, () -> gateway.lookup(provider, product, lookupFields()));
        rules.removeAll();
        assertThrows(ProviderRequestException.class, () -> gateway.lookup(provider, product, lookupFields()));
        for (String min : List.of("0", "100.1", "1.55", "NaN")) {
            rules.removeAll(); rules.add(rule(RULE_ID, "7", ZONE, min));
            assertThrows(ProviderRequestException.class, () -> gateway.lookup(provider, product, lookupFields()));
        }
        assertEquals(0, writes);
    }

    @Test
    void quoteUsesTheChosenRuleAndExactSchoolAndFreezesAScopedPrivateFingerprint() throws Exception {
        rules.add(rule("22", "8", "西校区", "1"));
        fields.put("runRuleId", "22");
        var prepared = prepare();
        assertEquals("22", prepared.fields().get("form[run_rule_item_id]"));
        assertEquals("8", prepared.fields().get("form[zone_id]"));
        assertEquals("西校区", prepared.fields().get("form[zone_name]"));
        assertEquals(SCHOOL_ID, prepared.fields().get("form[school_id]"));
        assertEquals(PASSWORD, prepared.fields().get("form[password]"));
        assertFalse(prepared.fields().containsKey("form[phone]")); assertFalse(prepared.fields().containsKey("form[min_minute]"));
        assertEquals(new BigDecimal("1.5"), prepared.billablePerUnit());
        assertTrue(JingyuNativeServiceGateway.matchesPreparedAccount("yyd", fields, prepared));
        assertFalse(prepared.accountFingerprint().matches(ACCOUNT));
        for (String secret : List.of(ACCOUNT, PASSWORD, SCHOOL, RULE_ID)) assertFalse(order.getScheduleJson().contains(secret));
        assertFalse(prepared.toString().contains(PASSWORD)); assertEquals(0, writes);
    }

    @Test
    void unknownRuleTooShortDistanceOrForeignFieldsNeverDispatch() {
        fields.put("runRuleId", "unknown");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        fields.put("runRuleId", RULE_ID); order.setDistance(new BigDecimal("1.4"));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        order.setDistance(new BigDecimal("1.5")); fields.put("zoneId", "forged");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        assertEquals(0, writes);
    }

    @Test
    void createRevalidatesThenWritesExactlyOnceAndVerifiesBothReceiptsAndTheWholeSchoolTuple() throws Exception {
        var prepared = prepare(); actions.clear();
        var result = gateway.execute(provider, product, order, "CREATE", prepared.fields());
        assertEquals(1, writes); assertEquals("yyd-451", result.externalOrderNo()); assertEquals("17", result.externalSubOrderNo());
        assertEquals("ACTIVE", result.status()); assertEquals(1, result.completed());
        assertEquals(List.of("get_school_data", "get_yyd_user_info", "get_price", "yyd_add", "orders", "get_school_data", "get_task_data"), actions);
        assertEquals("17", bodies.get("orders").get("keywords")); assertEquals("1", bodies.get("orders").get("type"));
        assertEquals("yyd-451", bodies.get("get_task_data").get("yyd_order_id"));
    }

    @ParameterizedTest @ValueSource(strings = {"user", "pass", "school_name", "run_rule_item_id", "zone_name", "uid", "id", "yyd_order_id"})
    void recoveryAndTaskReadsRequireEveryFrozenIdentityField(String key) throws Exception {
        prepare(); row.put(key, "123"); actions.clear();
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertFalse(actions.contains("get_task_data"));
        assertThrows(ProviderRequestException.class, () -> gateway.logs(provider, order, 1));
        assertThrows(ProviderRequestException.class, () -> gateway.refundRemaining(provider, order));
        assertEquals(0, writes);
    }

    @Test
    void quoteCannotBeConfirmedAfterARuleOrSchoolChanged() throws Exception {
        var prepared = prepare(); ((ObjectNode) rules.get(0).path("zone")).put("name", "更新的跑区");
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        ((ObjectNode) rules.get(0).path("zone")).put("name", ZONE); schools.removeAll();
        schools.add(school("2", SCHOOL));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", prepared.fields()));
        assertEquals(0, writes);
    }

    @Test
    void recoveryRejectsACatalogThatReassignsOrDuplicatesTheFrozenSchoolName() throws Exception {
        prepare(); schools.removeAll(); schools.add(school("2", SCHOOL)); actions.clear();
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertFalse(actions.contains("get_task_data"));
        schools.add(school(SCHOOL_ID, SCHOOL));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertEquals(0, writes);
        schools.remove(0);
        assertEquals("ACTIVE", gateway.sync(provider, order).status());
    }

    @Test
    void refundIsOnlyAnApplicationAndDoesNotInventAnAtomicRefundQuantity() throws Exception {
        prepare();
        assertEquals(2, gateway.refundRemaining(provider, order));
        var result = gateway.execute(provider, product, order, "REFUND", Map.of());
        assertEquals("REFUND_REVIEW", result.status()); assertNull(result.refundedUnits());
        assertEquals("17", bodies.get("refund").get("id")); assertEquals(1, writes);
    }

    @Test
    void scopedFingerprintsAreSaltedAndLengthPrefixedWithNoCrossScopeOrPasswordNormalization() {
        var a = ServiceAccountFingerprint.createScoped("test:v1", "ab", "c", " pass ");
        var b = ServiceAccountFingerprint.createScoped("test:v1", "ab", "c", " pass ");
        assertNotEquals(a, b); assertTrue(a.matchesScoped("test:v1", "ab", "c", " pass "));
        assertFalse(a.matchesScoped("test:v1", "a", "bc", " pass "));
        assertFalse(a.matchesScoped("test:v2", "ab", "c", " pass "));
        assertFalse(a.matchesScoped("test:v1", "ab", "c", "pass"));
        var context = a.withContext("school", "123");
        assertTrue(context.matchesScoped("test:v1", "ab", "c", " pass "));
        assertTrue(context.matchesContext("school", "123"));
        assertFalse(context.matchesContext("school", "124"));
        assertFalse(a.matchesContext("school", "123"));
        assertThrows(IllegalArgumentException.class, () -> ServiceAccountFingerprint.createScoped("invalid scope", "x"));
        assertThrows(IllegalArgumentException.class, () -> ServiceAccountFingerprint.createScoped("test", " "));
    }
}
