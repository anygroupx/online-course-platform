package com.course.platform.infra.servicecommerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

class WuxinNativeServiceGatewayTest {
    ApiHttpClient http;
    WuxinNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;
    static final String SCHOOL =
            """
{"run_plans":[{"code":"plan-1","name":"计划一"},{"code":"plan-2","name":"计划二"}],
 "areas":[{"code":"fence-1","name":"区域一"},{"code":"fence-2","name":"区域二"}],"auth_code":"private-code"}
""";
    static final String CONFIG =
            """
{"code":1,"data":{"order":{"order_number":"UP-10","order_status":2,"completed_quantity":3,
  "residue_num":7,"run_type":2,"run_time":"06:00-08:00","mark":"本人备注","auth_code":"private-code"},
  "schedule_config":{"run_plan_code":"plan-2","fence_code":"fence-2","start_time":"07:30","end_time":"09:00",
  "distance":2400,"weekday":"mon,wed,fri","pace":390},"school_info":%s}}
"""
                    .formatted(SCHOOL);

    @BeforeEach
    void setup() {
        http = mock(ApiHttpClient.class);
        gateway = new WuxinNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider();
        provider.setProviderType("wuxin");
        provider.setApiUrl("https://authorized.example/site");
        provider.setUsername("saved uid");
        provider.setApiKey("saved+key&value");
        product = new ServiceProduct();
        product.setProviderType("wuxin");
        product.setProject("sdxy");
        product.setRemoteProductId("sdxy");
        order = new ServiceOrder();
        order.setExternalOrderNo("UP-10");
        order.setUnitCharge(new BigDecimal("0.25"));
        order.setProviderType("wuxin");
        order.setProject("sdxy");
    }

    Map<String, String> input() {
        Map<String, String> fields =
                new LinkedHashMap<>(
                        Map.of(
                                "authCode",
                                "my-private-auth-code",
                                "runPlanCode",
                                "plan-2",
                                "fenceCode",
                                "fence-2",
                                "runType",
                                "2",
                                "runTime",
                                "07:30",
                                "endTime",
                                "09:00",
                                "weekdays",
                                "1,3,5",
                                "pace",
                                "6.5",
                                "startDate",
                                ServiceTime.now().toLocalDate().plusDays(1).toString()));
        fields.put("message", "本人备注");
        return fields;
    }

    OrderForm form(Map<String, String> fields) {
        return new OrderForm(10, new BigDecimal("2.4"), fields, List.of(), true);
    }

    @Test
    void catalogAndProbeHaveDifferentSuccessCodesAndOnlySavedQueryCredentials() {
        when(http.postForString(any(), contains("act=getWuxinSdxyPrice&"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"price\":\"0.123456\"}}");
        var catalog = gateway.fetchCatalog(provider, null);
        assertEquals("0.123456", catalog.get(0).unitPrice().toPlainString());
        assertEquals("元/次", catalog.get(0).priceUnit());
        verify(http)
                .postForString(
                        eq(provider),
                        eq(
                                "https://authorized.example/site/wuxin/api.php?act=getWuxinSdxyPrice&u_uid=saved+uid&key=saved%2Bkey%26value"),
                        eq(Map.of()));
        when(http.postForString(any(), contains("act=getWuxinSdxyOrdersList&"), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"list\":[]}}");
        gateway.testConnection(provider);
        assertThrows(BusinessException.class, () -> gateway.fetchCatalog(provider, "other"));
    }

    @Test
    void lookupAndCreateRecheckChoicesWithoutExposingAuthorizationOrMultiplyingDistance() {
        when(http.postForString(any(), contains("act=getWuxinSdxySchoolInfo&"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":" + SCHOOL + "}");
        Lookup lookup =
                gateway.lookup(provider, product, Map.of("authCode", "my-private-auth-code"));
        assertFalse(lookup.toString().contains("private"));
        PreparedOrder prepared = gateway.prepare(provider, product, form(input()));
        assertEquals(BigDecimal.ONE, prepared.billablePerUnit());
        assertEquals("授权账号", prepared.accountLabel());
        assertEquals("[1,3,5]", prepared.fields().get("run_week"));
        assertEquals("6.5", prepared.fields().get("run_speed"));
        assertEquals("2.4", prepared.fields().get("run_meter"));
        assertEquals("区域二", prepared.fields().get("zone_name"));
        assertEquals("07:30-09:00", prepared.fields().get("run_time"));
        assertEquals(10, prepared.fields().get("order_num"));
        assertFalse(prepared.toString().contains("private"));
        when(http.postForString(any(), contains("act=addWuxinSdxyOrder&"), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"order_number\":\"UP-10\"}}");
        assertEquals(
                "UP-10",
                gateway.execute(provider, product, order, "CREATE", prepared.fields())
                        .externalOrderNo());
        verify(http, times(2))
                .postForString(any(), contains("act=getWuxinSdxySchoolInfo&"), anyMap());
        verify(http)
                .postForString(
                        any(),
                        contains("act=addWuxinSdxyOrder&"),
                        argThat(
                                m ->
                                        m.get("auth_code").equals("my-private-auth-code")
                                                && !m.containsKey("login_key")
                                                && !m.containsKey("key")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"code\":1,\"data\":{\"order_number\":\"wrong-code\"}}",
                "{\"code\":0,\"data\":{}}",
                "{\"code\":0,\"code\":0,\"data\":{\"order_number\":\"duplicate\"}}",
                "{\"code\":-1,\"msg\":\"private-code\"}",
                "<html>private-code</html>",
                "{} trailing"
            })
    void invalidMutationRepliesAreNotRetriedOrConvertedIntoSuccess(String body) {
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(body);
        var error =
                assertThrows(
                        ProviderRequestException.class,
                        () -> gateway.execute(provider, product, order, "CREATE", Map.of()));
        assertFalse(error.getMessage().contains("private"));
        assertNull(error.getCause());
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @Test
    void currentPlanUsesExistingConfigurationInsteadOfFirstChoiceDefaults() {
        when(http.postForString(any(), contains("act=getWuxinSdxyOrderConfig&"), anyMap()))
                .thenReturn(CONFIG);
        Lookup view = gateway.orderOptions(provider, order);
        assertEquals("plan-2", view.suggested().get("runPlanCode"));
        assertEquals("fence-2", view.suggested().get("fenceCode"));
        assertEquals("2.4", view.suggested().get("distance"));
        assertEquals("6.5", view.suggested().get("pace"));
        assertEquals("1,3,5", view.suggested().get("weekdays"));
        assertEquals("07:30", view.suggested().get("runTime"));
        assertFalse(view.toString().contains("private"));
        var prepared = gateway.prepareAction(provider, order, "EDIT_PLAN", view.suggested());
        assertEquals("plan-2", prepared.get("run_plan_code"));
        assertFalse(prepared.containsKey("auth_code"));
        when(http.postForString(any(), contains("act=editWuxinSdxyOrder&"), anyMap()))
                .thenReturn("{\"code\":1}");
        gateway.execute(provider, product, order, "EDIT_PLAN", prepared);
        verify(http)
                .postForString(
                        any(),
                        contains("act=editWuxinSdxyOrder&"),
                        argThat(
                                m ->
                                        "UP-10".equals(m.get("order_number"))
                                                && !m.containsKey("start_date")
                                                && !m.containsKey("auth_code")));
    }

    @Test
    void missingPlanFieldsAreNotSilentlyReplacedAndMismatchedOrdersFailClosed() {
        String partial =
                "{\"code\":1,\"data\":{\"order\":{\"order_number\":\"UP-10\"},\"school_info\":"
                        + SCHOOL
                        + "}}";
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(partial);
        assertTrue(gateway.orderOptions(provider, order).suggested().isEmpty());
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(CONFIG.replace("UP-10", "OTHER"));
        assertThrows(ProviderRequestException.class, () -> gateway.orderOptions(provider, order));
        assertThrows(
                ProviderRequestException.class, () -> gateway.refundRemaining(provider, order));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
    }

    @Test
    void malformedFieldsUnknownSelectionsAndCredentialInjectionCannotBecomeMutations() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":1,\"data\":" + SCHOOL + "}");
        for (var entry :
                Map.of(
                                "runPlanCode",
                                "someone-elses-plan",
                                "runTime",
                                "25:00",
                                "weekdays",
                                "1,1",
                                "pace",
                                "99",
                                "endTime",
                                "06:00")
                        .entrySet()) {
            var fields = input();
            fields.put(entry.getKey(), entry.getValue());
            assertThrows(
                    BusinessException.class,
                    () -> gateway.prepare(provider, product, form(fields)));
        }
        var injected = input();
        injected.put("key", "forged");
        assertThrows(
                BusinessException.class, () -> gateway.prepare(provider, product, form(injected)));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAction(
                                provider,
                                order,
                                "EDIT_PLAN",
                                Map.of("authCode", "private", "distance", "2")));
        verify(http, never()).postForString(any(), contains("act=addWuxinSdxyOrder&"), anyMap());
    }

    @Test
    void refundsRequireAnExplicitCountAndAddTimesUsesQuantityNotOrderNum() {
        when(http.postForString(any(), contains("act=getWuxinSdxyOrderConfig&"), anyMap()))
                .thenReturn(CONFIG);
        assertEquals(7, gateway.refundRemaining(provider, order));
        when(http.postForString(any(), contains("act=deleteWuxinSdxyOrder&"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"refund_count\":6,\"refund_amount\":\"0.6\"}}");
        assertEquals(
                6, gateway.execute(provider, product, order, "REFUND", Map.of()).refundedUnits());
        when(http.postForString(any(), contains("act=deleteWuxinSdxyOrder&"), anyMap()))
                .thenReturn("{\"code\":1}");
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        when(http.postForString(any(), contains("act=getWuxinSdxyPrice&"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"price\":0.20}}");
        gateway.checkAddTimes(provider, order, 2);
        when(http.postForString(any(), contains("act=increaseWuxinSdxyOrder&"), anyMap()))
                .thenReturn("{\"code\":1}");
        gateway.execute(provider, product, order, "ADD_TIMES", Map.of("delta", 2));
        verify(http)
                .postForString(
                        any(),
                        contains("act=increaseWuxinSdxyOrder&"),
                        eq(Map.of("order_number", "UP-10", "quantity", 2)));
        when(http.postForString(any(), contains("act=getWuxinSdxyPrice&"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"price\":0.30}}");
        assertThrows(BusinessException.class, () -> gateway.checkAddTimes(provider, order, 2));
    }

    @Test
    void reassignAndSanitizedLogsMatchThePlaintextUiContract() {
        when(http.postForString(any(), contains("act=reassignWuxinSdxyOrder&"), anyMap()))
                .thenReturn("{\"code\":1}");
        gateway.execute(provider, product, order, "REASSIGN", Map.of());
        verify(http)
                .postForString(
                        any(),
                        contains("act=reassignWuxinSdxyOrder&"),
                        eq(Map.of("order_number", "UP-10")));
        when(http.postForString(any(), contains("act=getWuxinSdxyOrderRecords&"), anyMap()))
                .thenReturn(
                        "{\"code\":1,\"data\":{\"list\":[{\"id\":1,\"status\":3,\"scheduled_time\":\"2026-09-08"
                            + " 07:30:00\",\"result\":\"private-code\"}]}}");
        var logs = gateway.logs(provider, order, 1);
        assertEquals("执行失败", logs.items().get(0).status());
        assertFalse(logs.toString().contains("private-code"));
        assertFalse(logs.hasMore());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void syncRecognizesWaitingStateButRefundsStillRequireLocalLedgerSettlement(int status) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(CONFIG.replace("\"order_status\":2", "\"order_status\":" + status));
        var result = gateway.sync(provider, order);
        assertEquals(
                status <= 2
                        ? "ACTIVE"
                        : status == 3 ? "COMPLETED" : status == 4 ? "REFUND_REVIEW" : "ATTENTION",
                result.status());
        assertEquals(3, result.completed());
        assertNull(result.refundedUnits());
    }
}
