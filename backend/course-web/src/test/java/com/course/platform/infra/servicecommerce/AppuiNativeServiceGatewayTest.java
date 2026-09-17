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

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppuiNativeServiceGatewayTest {
    final ObjectMapper json = new ObjectMapper();
    ApiHttpClient http;
    AppuiNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;
    ObjectNode remote;
    Map<String, String> fields;
    final Map<String, String> replies = new HashMap<>();
    final List<String> urls = new ArrayList<>();
    final List<Map<String, Object>> bodies = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        http = mock(ApiHttpClient.class);
        gateway = new AppuiNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider();
        provider.setProviderType("appui"); provider.setApiUrl("https://service.example/install");
        provider.setUsername("saved-id"); provider.setApiKey("saved-secret");
        product = new ServiceProduct(); product.setProviderType("appui"); product.setProject("1"); product.setRemoteProductId("1");
        order = new ServiceOrder(); order.setProviderType("appui"); order.setProject("1"); order.setRemoteProductId("1");
        order.setExternalOrderNo("451"); order.setQuantity(10); order.setCompleted(2); order.setUnitCharge(new BigDecimal("0.25"));
        order.setScheduleJson(json.writeValueAsString(ServiceAccountFingerprint.create("student-001")));
        fields = new LinkedHashMap<>(Map.of("account", "student-001", "password", " p&+=密碼 ",
                "address", "教学楼 A&B", "startTime", "07:30", "endTime", "18:10", "weekdays", "1,2,3,4,5", "reports", "1,2,3"));
        remote = json.createObjectNode().put("id", "451").put("pid", "1").put("user", "student-001")
                .put("pass", "current-private-password").put("total_day", "10").put("residue_day", "8")
                .put("status", "进行中").put("address", "当前地址").put("shangban_time", "07:30").put("xiaban_time", "18:10");
        remote.set("week", json.readTree("[1,2,3,4,5]")); remote.set("report", json.readTree("[1,2,3]"));
        replies.put("getCourse", "{\"code\":1,\"data\":[{\"pid\":1,\"name\":\"校友邦\",\"price\":\"0.123456\",\"yes_school\":0}]}");
        replies.put("query", "{\"code\":1,\"userName\":\"真实姓名\",\"address\":\"校验地址\",\"msg\":\"private-secret\"}");
        replies.put("getSchoolList", "{\"code\":1,\"data\":[\"示例学校\",\"第二学校\"]}");
        replies.put("add", "{\"code\":1,\"oid\":451}");
        replies.put("edit", "{\"code\":1}"); replies.put("renew", "{\"code\":1}"); replies.put("refund", "{\"code\":1,\"days2\":8}");
        when(http.postForString(eq(provider), anyString(), anyMap())).thenAnswer(inv -> {
            String url = inv.getArgument(1); Map<String, Object> body = inv.getArgument(2);
            urls.add(url); bodies.add(new LinkedHashMap<>(body));
            String act = url.substring(url.indexOf("act=") + 4).split("&")[0];
            return replies.containsKey(act) ? replies.get(act)
                    : json.createObjectNode().put("code", 1).set("data", json.createArrayNode().add(remote)).toString();
        });
    }

    OrderForm form() { return new OrderForm(10, null, fields, List.of(), true); }
    Map<String, String> editFields() {
        var f = new LinkedHashMap<>(fields); f.remove("account"); f.remove("schoolName"); return f;
    }

    @Test
    void protocolUsesInstallationRootAndBodyAuthenticationWithPhpBracketKeys() {
        PreparedOrder prepared = gateway.prepare(provider, product, form());
        assertEquals("https://service.example/install/appui/api.php?act=query", urls.get(0));
        assertFalse(urls.toString().contains("saved-secret"));
        assertEquals("saved-id", bodies.get(0).get("login_uid"));
        assertEquals("saved-secret", bodies.get(0).get("login_key"));
        assertEquals("student-001", bodies.get(0).get("form[user]"));
        assertEquals(" p&+=密碼 ", bodies.get(0).get("form[pass]"));
        assertEquals("真实姓名", prepared.fields().get("form[userName]"));
        assertEquals("教学楼 A&B", prepared.fields().get("form[address]"));
        assertEquals(10, prepared.fields().get("form[days1]"));
        assertEquals(1, prepared.fields().get("form[week][0]"));
        assertEquals(3, prepared.fields().get("form[report][2]"));
        assertFalse(prepared.fields().containsKey("form"));
        assertNull(prepared.distance()); assertNull(prepared.plan());
        assertEquals(BigDecimal.ONE, prepared.billablePerUnit());
        assertTrue(prepared.accountFingerprint().matches("student-001"));
        assertFalse(prepared.accountFingerprint().matches("student-002"));
        assertFalse(prepared.accountLabel().contains("student-001"));
        assertFalse(prepared.toString().contains("密碼"));
        assertEquals("451", gateway.execute(provider, product, order, "CREATE", prepared.fields()).externalOrderNo());
        assertEquals(2, urls.size());
    }

    @Test
    void registryPublishesNineCatalogProjectsAndOnlyImplementedActions() {
        var registry = new com.course.platform.infra.integration.PluginConnectorRegistry(List.of(gateway));
        var descriptor = new com.course.platform.infra.integration.PluginResearchCatalog(registry, org.mockito.Mockito.mock(com.course.platform.infra.servicecommerce.NativeServiceGatewayRouter.class)).find("P09");
        assertEquals("appui", descriptor.providerType());
        assertEquals("NATIVE_PARTIAL", descriptor.integrationStatus());
        assertEquals(List.of("CATALOG"), descriptor.availableCapabilities());
        assertEquals(9, descriptor.projects().size());
        assertEquals(List.of("LOOKUP","CREATE","SYNC","REFUND","ADD_TIMES","EDIT_PLAN"),
                PhpNativeServiceGateway.capabilities("appui"));
        gateway.testConnection(provider);
        assertEquals(1, urls.size()); assertTrue(urls.get(0).endsWith("act=getCourse"));
    }

    @Test
    void catalogUsesLiveExactPricesAndNeverExposesExtraFields() {
        var catalog = gateway.fetchCatalog(provider, null);
        assertEquals("0.123456", catalog.get(0).unitPrice().toPlainString());
        assertEquals("元/天", catalog.get(0).priceUnit());
        assertEquals("1", catalog.get(0).id());
        assertTrue(AppuiNativeServiceGateway.supported("9", "9"));
        assertFalse(AppuiNativeServiceGateway.supported("1", "2"));
    }

    @ParameterizedTest
    @ValueSource(strings={"0", "10", "01", "-1"})
    void unknownProjectRejected(String pid) {
        product.setProject(pid); product.setRemoteProductId(pid);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(strings={"-0.1", "NaN", "1e2", "0.0000001", "999999999", "true", ""})
    void badCatalogPriceRejected(String price) {
        replies.put("getCourse", "{\"code\":1,\"data\":[{\"pid\":1,\"name\":\"项目\",\"price\":\"" + price + "\",\"yes_school\":0}]}");
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, null));
    }

    @Test
    void duplicateUnknownAndChangedSchoolContractFailClosed() throws Exception {
        ObjectNode root = (ObjectNode) json.readTree(replies.get("getCourse"));
        ArrayNode list = (ArrayNode) root.path("data"); list.add(list.get(0).deepCopy());
        replies.put("getCourse", root.toString());
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, null));
        list.remove(1); ((ObjectNode)list.get(0)).put("pid", 10); replies.put("getCourse", root.toString());
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, null));
        ((ObjectNode)list.get(0)).put("pid", 1).put("yes_school", 1); replies.put("getCourse", root.toString());
        assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, null));
    }

    @ParameterizedTest
    @ValueSource(strings={"3", "6", "8"})
    void schoolNamesAreValuesAndQueryCarriesPidInUrlAndBody(String pid) {
        product.setProject(pid); product.setRemoteProductId(pid); fields.put("schoolName", "示例学校");
        var page = gateway.schools(provider, product, 1, "示例");
        assertEquals("示例学校", page.items().get(0).id()); assertFalse(page.hasMore());
        assertTrue(urls.get(0).endsWith("act=getSchoolList&pid=" + pid));
        assertEquals(pid, bodies.get(0).get("pid"));
        assertEquals("真实姓名", gateway.lookup(provider, product, fields).suggested().get("studentName"));
        assertEquals("示例学校", bodies.get(bodies.size()-1).get("form[school]"));
        fields.put("schoolName", "not-allowed");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
    }

    @Test
    void schoolsAreBoundedAndLocallyPaged() {
        product.setProject("3"); product.setRemoteProductId("3");
        ArrayNode list = json.createArrayNode(); for (int i=0;i<21;i++) list.add("学校"+i);
        replies.put("getSchoolList", json.createObjectNode().put("code", 1).set("data", list).toString());
        assertTrue(gateway.schools(provider, product, 1, "").hasMore());
        assertEquals(1, gateway.schools(provider, product, 2, "").items().size());
        assertEquals(0, gateway.schools(provider, product, 3, "").items().size());
        assertThrows(BusinessException.class, () -> gateway.schools(provider, product, 151, ""));
        assertThrows(BusinessException.class, () -> gateway.schools(provider, product, 1, "\n"));
        assertFalse(bodies.get(0).containsKey("page"));
    }

    @ParameterizedTest
    @CsvSource({"startTime,25:00", "startTime,18:10", "endTime,06:00", "weekdays,0", "weekdays,8", "weekdays,'1,1'", "reports,4", "reports,'1,1'", "reports,''", "address,''", "password,''"})
    void planValidationHappensBeforeQuery(String key, String value) {
        fields.put(key, value);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(ints={0,366,-1})
    void quantityBounded(int days) {
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, new OrderForm(days,null,fields,List.of(),true)));
        verifyNoInteractions(http);
    }

    @Test
    void clientNameAndHiddenCredentialsCannotBeInjected() {
        fields.put("studentName", "forged");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        fields.remove("studentName"); fields.put("login_uid", "other");
        assertThrows(BusinessException.class, () -> gateway.lookup(provider, product, fields));
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, new OrderForm(10,BigDecimal.ONE,Map.of(),List.of(),true)));
        verifyNoInteractions(http);
    }

    @Test
    void exactOrderSearchNeverSelectsFirstOrAccountOnlyAndHidesPasswords() {
        ObjectNode unrelated = remote.deepCopy().put("id", "452").put("user", "other-account");
        replies.put("orders", json.createObjectNode().put("code", 1).set("data", json.createArrayNode().add(unrelated).add(remote)).toString());
        assertEquals(8, gateway.refundRemaining(provider, order));
        assertEquals(1, bodies.get(0).get("type")); assertEquals("451", bodies.get(0).get("keywords"));
        var options = gateway.orderOptions(provider, order);
        assertEquals("1,2,3,4,5", options.suggested().get("weekdays"));
        assertFalse(options.toString().contains("current-private-password"));
        assertFalse(options.suggested().containsKey("password"));
    }

    @ParameterizedTest
    @CsvSource({"id,999", "pid,2", "user,other-account", "total_day,11", "residue_day,11", "residue_day,9", "residue_day,-1"})
    void identityOrQuotaMismatchCannotReachBusinessWrite(String field, String value) {
        remote.put(field,value);
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        assertEquals(1, urls.size()); assertTrue(urls.get(0).endsWith("act=orders"));
    }

    @Test
    void duplicateIdAndMissingFingerprintFailClosed() {
        replies.put("orders", json.createObjectNode().put("code",1).set("data",json.createArrayNode().add(remote).add(remote)).toString());
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        replies.remove("orders"); order.setScheduleJson(null);
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
    }

    @Test
    void editingPreservesEmptyPasswordAndCannotChangeAccountOrQuota() {
        var edit = editFields(); edit.put("password", "");
        Map<String,Object> prepared = gateway.prepareAction(provider, order, "EDIT_PLAN", edit);
        assertEquals("current-private-password", prepared.get("form[pass]"));
        assertFalse(prepared.containsKey("form[user]")); assertFalse(prepared.containsKey("form[days1]"));
        assertEquals("1", prepared.get("form[pid]"));
        gateway.execute(provider, product, order, "EDIT_PLAN", prepared);
        assertEquals("451", bodies.get(bodies.size()-1).get("id"));
        edit.put("account", "other");
        assertThrows(BusinessException.class, () -> gateway.prepareAction(provider, order, "EDIT_PLAN", edit));
    }

    @Test
    void renewChecksLiveCostAndUsesDaysNotCalendars() {
        gateway.checkAddTimes(provider, order, 20);
        gateway.execute(provider, product, order, "ADD_TIMES", Map.of("delta",20));
        assertEquals(20, bodies.get(bodies.size()-1).get("days1"));
        assertEquals("451", bodies.get(bodies.size()-1).get("id"));
        order.setUnitCharge(new BigDecimal("0.10"));
        assertThrows(BusinessException.class, () -> gateway.checkAddTimes(provider, order, 1));
        assertThrows(BusinessException.class, () -> gateway.checkAddTimes(provider, order, 0));
    }

    @Test
    void refundReceiptCannotExceedFreshQuotaEvenWhenLocalProgressIsStale() {
        remote.put("residue_day", 4);
        replies.put("refund", "{\"code\":1,\"days2\":5}");
        assertThrows(ProviderRequestException.class,
                () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        assertEquals(1, urls.stream().filter(s -> s.endsWith("act=refund")).count());
    }

    @Test
    void refundStatusDoesNotMeanAllQuotaWasConsumedOrCreditWasReceived() {
        remote.put("status", "已退款").put("residue_day",0);
        var state = gateway.sync(provider, order);
        assertEquals("REFUND_REVIEW", state.status()); assertEquals(2,state.completed()); assertNull(state.refundedUnits());
        assertThrows(BusinessException.class, () -> gateway.refundRemaining(provider, order));
    }

    @ParameterizedTest
    @ValueSource(strings={"null", "-1", "9", "1.5", "true", "\"8days\"", "\"08\"", "10000"})
    void explicitRefundDaysMustBeStrictAndBounded(String days) {
        replies.put("refund", "{\"code\":1,\"days2\":"+days+"}");
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        assertEquals(1, urls.stream().filter(s -> s.endsWith("act=refund")).count());
    }

    @ParameterizedTest
    @ValueSource(strings={"", "null", "[]", "{\"code\":1,\"code\":1,\"oid\":451}", "{\"code\":1,\"oid\":451}{}", "{\"code\":true,\"oid\":451}", "{\"code\":1.0,\"oid\":451}", "{\"code\":0,\"oid\":451}", "{\"code\":-1,\"msg\":\"private-password\"}", "{\"code\":1}"})
    void malformedOrNegativeWriteResultIsNotAcceptedAndNeverRetried(String reply) {
        replies.put("add",reply);
        var ex = assertThrows(ProviderRequestException.class, () -> gateway.execute(provider,product,order,"CREATE",Map.of()));
        assertFalse(ex.getMessage().contains("private-password"));
        assertEquals(1, urls.size());
    }

    @Test
    void utf8BodyLimitAndTimeoutAreBoundedWithoutRetry() {
        replies.put("add", "{\"code\":1,\"oid\":451,\"data\":\"" + "中".repeat(90000) + "\"}");
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider,product,order,"CREATE",Map.of()));
        reset(http); when(http.postForString(any(),anyString(),anyMap())).thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        assertThrows(ProviderRequestException.class, () -> gateway.execute(provider,product,order,"CREATE",Map.of()));
        verify(http,times(1)).postForString(any(),anyString(),anyMap());
    }

    @Test
    void logsOnlyProjectSafeStatusesAndTimesAndUseLocalPagination() {
        ArrayNode list = json.createArrayNode();
        for (int i=1;i<=21;i++) list.add(json.createObjectNode().put("id",i).put("addtime","2026-09-11 18:00:00")
                .put("qd_status","已签").put("qt_status","异常").put("qd_msg","sensitive-account").put("qt_msg","sensitive-password"));
        replies.put("detail",json.createObjectNode().put("code",1).set("data",list).toString());
        var first=gateway.logs(provider,order,1); assertEquals(20,first.items().size()); assertTrue(first.hasMore());
        assertEquals("签到：已签 · 签退：异常",first.items().get(0).status());
        assertFalse(first.toString().contains("sensitive"));
        assertEquals(1,gateway.logs(provider,order,2).items().size());
        assertFalse(bodies.get(1).containsKey("page"));
        ((ObjectNode)list.get(0)).put("addtime","2026-02-30 18:00:00");
        replies.put("detail",json.createObjectNode().put("code",1).set("data",list).toString());
        assertThrows(ProviderRequestException.class, () -> gateway.logs(provider,order,1));
    }

    @ParameterizedTest
    @ValueSource(strings={"https://service.example/appui", "https://service.example/appui/api.php", "https://service.example/api.php", "https://127.0.0.1", "https://service.example/?act=refund"})
    void unsafeOrDoublePluginRootsAreBlockedBeforeHttp(String url) {
        provider.setApiUrl(url);
        assertThrows(RuntimeException.class, () -> gateway.fetchCatalog(provider,null));
        verifyNoInteractions(http);
    }
}
