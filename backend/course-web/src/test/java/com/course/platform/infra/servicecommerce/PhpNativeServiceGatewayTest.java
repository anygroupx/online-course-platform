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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

class PhpNativeServiceGatewayTest {
    ApiHttpClient http;
    PhpNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;

    @BeforeEach
    void setup() {
        http = mock(ApiHttpClient.class);
        gateway = new PhpNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider();
        provider.setProviderType("jiguang");
        provider.setApiUrl("https://authorized.example/site");
        provider.setUsername("saved-uid");
        provider.setApiKey("saved-key");
        product = new ServiceProduct();
        product.setProviderType("jiguang");
        product.setProject("default");
        product.setRemoteProductId("1");
        order = new ServiceOrder();
        order.setProviderType("jiguang");
        order.setProject("default");
        order.setExternalOrderNo("REMOTE-7");
        order.setQuantity(10);
        order.setCompleted(2);
        order.setUnitCharge(new BigDecimal("0.5"));
    }

    OrderForm form(Map<String, String> fields) {
        return new OrderForm(10, new BigDecimal("2"), fields, List.of(), true);
    }

    Map<String, String> jiguang() {
        return Map.of(
                "schoolName",
                "测试大学",
                "studentName",
                "测试姓名",
                "studentAccount",
                "2026001",
                "message",
                "仅本人授权");
    }

    @Test
    void jiguangProducesCorrectNativeFormAndDoesNotExposePersonalData() {
        var prepared = gateway.prepare(provider, product, form(jiguang()));
        assertEquals("2026001", prepared.fields().get("student_account"));
        assertEquals("1", prepared.fields().get("product_id"));
        assertEquals("2", prepared.fields().get("km_per_day"));
        assertEquals(10, prepared.fields().get("times"));
        assertEquals("20***01", prepared.accountLabel());
        verifyNoInteractions(http);
    }

    @Test
    void explicitConsentAndAllInputBoundsAreEnforcedBeforeHttp() {
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                new OrderForm(
                                        10, new BigDecimal("2"), jiguang(), List.of(), false)));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                new OrderForm(0, new BigDecimal("2"), jiguang(), List.of(), true)));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                new OrderForm(
                                        10, new BigDecimal("4"), jiguang(), List.of(), true)));
        assertThrows(
                BusinessException.class,
                () -> gateway.prepare(provider, product, form(Map.of("login_key", "attacker"))));
        verifyNoInteractions(http);
    }

    @Test
    void credentialedCreateUsesSavedOriginAndNeverForwardsUserControlledUrlOrSecrets() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":1,\"data\":{\"order_no\":\"UP-9\",\"password\":\"must-not-return\"}}");
        var response =
                gateway.execute(
                        provider,
                        product,
                        order,
                        "CREATE",
                        gateway.prepare(provider, product, form(jiguang())).fields());
        assertEquals("UP-9", response.externalOrderNo());
        assertFalse(response.toString().contains("must-not-return"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(http)
                .postForString(
                        eq(provider),
                        eq("https://authorized.example/site/jiguang/jiguang.api.php?act=add"),
                        fields.capture());
        assertEquals("saved-uid", fields.getValue().get("login_uid"));
        assertEquals("saved-key", fields.getValue().get("login_key"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"code\":0,\"data\":{\"order_no\":\"wrong-success-code\"}}",
                "{\"code\":1,\"data\":{}}",
                "{\"code\":1,\"code\":1,\"data\":{\"order_no\":\"duplicate\"}}",
                "{\"code\":-1,\"msg\":\"secret-password\"}",
                "{} trailing",
                "<html>password</html>"
            })
    void invalidWriteRepliesNeverBecomeSuccessAndNeverRetry(String body) {
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(body);
        var ex =
                assertThrows(
                        ProviderRequestException.class,
                        () -> gateway.execute(provider, product, order, "CREATE", Map.of()));
        assertNull(ex.getCause());
        assertFalse(ex.getMessage().contains("secret"));
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @Test
    void refundAndAddTimesUseTheirOwnContract() {
        when(http.postForString(any(), contains("refund_preview"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"item\":{\"remaining\":8}}}");
        assertEquals(8, gateway.refundRemaining(provider, order));
        when(http.postForString(any(), contains("refund_confirm"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"item\":{\"remaining\":7}}}");
        assertEquals(
                7, gateway.execute(provider, product, order, "REFUND", Map.of()).refundedUnits());
        when(http.postForString(any(), contains("addtimes_preview"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"item\":{\"delta\":2,\"cost\":2}}}");
        assertThrows(BusinessException.class, () -> gateway.checkAddTimes(provider, order, 2));
    }

    @Test
    void syncOnlyReturnsAnExactOwnedRemoteReferenceAndSanitizedStatus() {
        when(http.postForString(any(), contains("act=orders"), anyMap()))
                .thenReturn(
                        "{\"code\":1,\"data\":[{\"order_no\":\"OTHER\",\"password\":\"secret\",\"status\":\"finished\",\"completed_times\":10},{\"order_no\":\"REMOTE-7\",\"status\":\"in_progress\",\"completed_times\":3}]}");
        var result = gateway.sync(provider, order);
        assertEquals(3, result.completed());
        assertEquals("ACTIVE", result.status());
        assertFalse(result.toString().contains("secret"));
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        Integer.valueOf(4).equals(m.get("type"))
                                                && "REMOTE-7".equals(m.get("keywords"))));
    }

    @Test
    void emptyAndDuplicateOrderResultsAreNotTreatedAsCancellation() {
        for (String rows :
                List.of("[]", "[{\"order_no\":\"REMOTE-7\"},{\"order_no\":\"REMOTE-7\"}]")) {
            when(http.postForString(any(), anyString(), anyMap()))
                    .thenReturn("{\"code\":1,\"data\":" + rows + "}");
            assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        }
    }

    @Test
    void heishaRechecksPlanAndKeepsPreflightTokenOutOfLookupView() {
        provider.setProviderType("heisha");
        product.setProviderType("heisha");
        String body =
                """
{"code":1,"data":{"account":{"schoolName":"示例大学"},"runPreflightToken":"private-token","checkedAt":"2026-09-06T10:00:00Z","plans":[{"planOptionId":"plan1","runName":"日常计划","singleMinDistanceKm":1,"singleMaxDistanceKm":3,"timeFragments":[]}],"fences":[{"fenceOptionId":"fence1","fenceName":"校内区域"}]}}
""";
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(body);
        var input =
                Map.of(
                        "account",
                        "13800138000",
                        "password",
                        "private-password",
                        "planOptionId",
                        "plan1",
                        "fenceOptionId",
                        "fence1",
                        "runTime",
                        "08:00");
        var lookup = gateway.lookup(provider, product, input);
        assertFalse(lookup.toString().contains("private-token"));
        assertFalse(lookup.toString().contains("private-password"));
        var prepared = gateway.prepare(provider, product, form(input));
        assertEquals("private-token", prepared.fields().get("run_preflight_token"));
        assertEquals("plan1", prepared.fields().get("plan_option_id"));
        assertEquals("[]", prepared.fields().get("time_fragments"));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                form(
                                        Map.of(
                                                "account",
                                                "13800138000",
                                                "password",
                                                "private-password",
                                                "planOptionId",
                                                "foreign",
                                                "fenceOptionId",
                                                "fence1",
                                                "runTime",
                                                "08:00"))));
    }

    @Test
    void faceProductsHaveNativeAdaptersButUnknownTypesStillFailClosed() {
        assertTrue(PhpNativeServiceGateway.supported("heisha", "default", "3"));
        assertTrue(PhpNativeServiceGateway.supported("heisha", "default", "4"));
        assertFalse(PhpNativeServiceGateway.supported("unknown", "default", "1"));
    }

    @Test
    void flashUsesPhpBracketEncodingPerTaskAndFrozenRepairMultiplier() {
        provider.setProviderType("flash");
        product.setProviderType("flash");
        product.setProject("xbd");
        product.setRemoteProductId("xbd");
        String json =
                "{\"code\":0,\"data\":{\"student\":{\"student_id\":\"s1\",\"rule\":{\"1\":[]},\"fences\":[{\"fence_id\":\"f1\",\"name\":\"区域\"}]}}}";
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(json);
        String time =
                ServiceTime.now()
                        .plusDays(2)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        var input =
                Map.of(
                        "account",
                        "13800138000",
                        "password",
                        "private",
                        "runType",
                        "1",
                        "fenceId",
                        "f1",
                        "repair",
                        "true");
        var prepared =
                gateway.prepare(
                        provider,
                        product,
                        new OrderForm(1, new BigDecimal("2"), input, List.of(time), true));
        assertEquals(new BigDecimal("4"), prepared.billablePerUnit());
        assertEquals(time, prepared.fields().get("form[task_list][0][start_time]"));
        assertEquals("1", prepared.fields().get("form[repair]"));
        assertEquals("s1", prepared.fields().get("form[student_id]"));
    }

    @Test
    void flashPauseAndResumeUseDocumentedInvertedFlag() {
        provider.setProviderType("flash");
        product.setProviderType("flash");
        product.setProject("sdxy");
        product.setRemoteProductId("sdxy");
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":null}");
        assertEquals(
                "PAUSED", gateway.execute(provider, product, order, "PAUSE", Map.of()).status());
        assertEquals(
                "ACTIVE", gateway.execute(provider, product, order, "RESUME", Map.of()).status());
        verify(http)
                .postForString(
                        any(),
                        eq("https://authorized.example/site/flash/api.php?act=pause&appId=sdxy"),
                        argThat(m -> "0".equals(m.get("pause"))));
        verify(http).postForString(any(), anyString(), argThat(m -> "1".equals(m.get("pause"))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "pending_generate",
                "pending",
                "next_batch",
                "sent",
                "wait_ack",
                "ack_retry"
            })
    void heishaWaitingAndAcknowledgementStatesAreNotErrors(String state) {
        provider.setProviderType("heisha");
        order.setProviderType("heisha");
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":1,\"data\":[{\"order_no\":\"REMOTE-7\",\"status\":\""
                                + state
                                + "\",\"completed_times\":0}]}");
        assertEquals("ACTIVE", gateway.sync(provider, order).status());
    }

    void flashOrder() {
        provider.setProviderType("flash");
        product.setProviderType("flash");
        product.setProject("sdxy");
        product.setRemoteProductId("sdxy");
        order.setProviderType("flash");
        order.setProject("sdxy");
        order.setExternalSubOrderNo("SUB-7");
    }

    Map<String, String> taskChange(String id) {
        return Map.of(
                "taskId",
                id,
                "time",
                ServiceTime.now()
                        .plusDays(1)
                        .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")),
                "page",
                "1");
    }

    @Test
    void flashTaskChangeVerifiesTheOwnedLogPageBeforeAnyMutation() {
        flashOrder();
        when(http.postForString(any(), contains("act=log&"), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":{\"list\":[{\"run_task_id\":\"TASK-1\",\"start_time\":\"2026-09-10"
                            + " 08:00:00\",\"status_display\":\"未开始\"}]}}");
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAction(
                                provider, order, "CHANGE_TIME", taskChange("OTHER-TASK")));
        var prepared = gateway.prepareAction(provider, order, "CHANGE_TIME", taskChange("TASK-1"));
        verify(http, never()).postForString(any(), contains("act=change_task_time&"), anyMap());
        when(http.postForString(any(), contains("act=change_task_time&"), anyMap()))
                .thenReturn("{\"code\":0,\"data\":null}");
        gateway.execute(provider, product, order, "CHANGE_TIME", prepared);
        verify(http)
                .postForString(
                        any(),
                        contains("act=change_task_time&appId=sdxy"),
                        argThat(
                                m ->
                                        "TASK-1".equals(m.get("run_task_id"))
                                                && "REMOTE-7".equals(m.get("agg_order_id"))
                                                && prepared.get("start_time")
                                                        .equals(m.get("start_time"))));
        verify(http, atLeastOnce())
                .postForString(
                        any(),
                        contains("act=log&"),
                        argThat(m -> "SUB-7".equals(m.get("sdxy_order_id"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"成功", "退款", "执行中", "unknown-state"})
    void endedOrUnknownFlashTasksCannotBeRescheduled(String status) {
        flashOrder();
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":{\"list\":[{\"run_task_id\":\"TASK-1\",\"start_time\":\"2026-09-10"
                            + " 08:00:00\",\"status_display\":\""
                                + status
                                + "\"}]}}");
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAction(provider, order, "CHANGE_TIME", taskChange("TASK-1")));
        verify(http, never()).postForString(any(), contains("act=change_task_time&"), anyMap());
    }

    @Test
    void duplicateFlashTaskReferencesAreAmbiguousRatherThanSelectingAnArbitraryRow() {
        flashOrder();
        String row =
                "{\"run_task_id\":\"TASK-1\",\"start_time\":\"2026-09-10"
                        + " 08:00:00\",\"status_display\":\"未开始\"}";
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"list\":[" + row + "," + row + "]}}");
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAction(provider, order, "CHANGE_TIME", taskChange("TASK-1")));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "sdxy,sdxy_order_id",
        "ydsjxy,ydsjxy_order_id",
        "xbd,xbd_order_id"
    })
    void flashCreationRequiresTheCorrectProjectSpecificSubOrderId(String project, String field) {
        flashOrder();
        product.setProject(project);
        product.setRemoteProductId(project);
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":{\"agg_order_id\":\"REMOTE-7\",\"sub_order\":{\""
                                + field
                                + "\":\"SUB-7\"}}}");
        assertEquals(
                "SUB-7",
                gateway.execute(provider, product, order, "CREATE", Map.of()).externalSubOrderNo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sdxy", "ydsjxy", "xbd"})
    void perTaskDelayRetainsTheOwnedAggregateAndVerifiesOneTask(String project) {
        flashOrder();
        product.setProject(project);
        product.setRemoteProductId(project);
        order.setProject(project);
        when(http.postForString(any(), contains("act=log&"), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":{\"list\":[{\"run_task_id\":\"TASK-1\",\"start_time\":\"2026-09-10"
                            + " 08:00:00\",\"status_display\":\"未开始\"}]}}");
        var fields =
                gateway.prepareAction(
                        provider, order, "DELAY_TASK", Map.of("taskId", "TASK-1", "page", "1"));
        assertEquals(Map.of("run_task_id", "TASK-1"), fields);
        verify(http, never()).postForString(any(), contains("act=delay_task&"), anyMap());
        when(http.postForString(any(), contains("act=delay_task&"), anyMap()))
                .thenReturn("{\"code\":0}");
        gateway.execute(provider, product, order, "DELAY_TASK", fields);
        verify(http)
                .postForString(
                        any(),
                        contains("act=delay_task&appId=" + project),
                        argThat(
                                m ->
                                        "TASK-1".equals(m.get("run_task_id"))
                                                && "REMOTE-7".equals(m.get("agg_order_id"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"成功", "退款", "执行中", "unknown-state"})
    void completedOrAmbiguousTasksCannotBeDelayed(String status) {
        flashOrder();
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":{\"list\":[{\"run_task_id\":\"TASK-1\",\"start_time\":\"2026-09-10"
                            + " 08:00:00\",\"status_display\":\""
                                + status
                                + "\"}]}}");
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAction(
                                provider,
                                order,
                                "DELAY_TASK",
                                Map.of("taskId", "TASK-1", "page", "1")));
        verify(http, never()).postForString(any(), contains("act=delay_task&"), anyMap());
    }

    @Test
    void perTaskDelayCannotAcceptAnotherAggregateArbitraryFieldsOrDuplicateTaskIds() {
        flashOrder();
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAction(
                                provider,
                                order,
                                "DELAY_TASK",
                                Map.of(
                                        "taskId",
                                        "TASK-1",
                                        "page",
                                        "1",
                                        "agg_order_id",
                                        "FOREIGN")));
        verifyNoInteractions(http);
        String row =
                "{\"run_task_id\":\"TASK-1\",\"start_time\":\"2026-09-10"
                        + " 08:00:00\",\"status_display\":\"未开始\"}";
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"list\":[" + row + "," + row + "]}}");
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAction(
                                provider,
                                order,
                                "DELAY_TASK",
                                Map.of("taskId", "TASK-1", "page", "1")));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAction(
                                provider,
                                order,
                                "DELAY_TASK",
                                Map.of("taskId", "FOREIGN", "page", "1")));
        verify(http, never()).postForString(any(), contains("act=delay_task&"), anyMap());
    }

    String smsLookupData(String password) {
        return "{\"code\":0,\"data\":{\"student\":{\"student_id\":\"BOUND-STUDENT\",\"password\":\""
                + password
                + "\",\"run_rule_lst\":[{\"run_rule_id\":\"r1\",\"label\":\"日常计划\"}]},\"zone_list\":[{\"zone_id\":\"z1\",\"name\":\"授权区域\"}]}}";
    }

    @Test
    void flashSmsAuthorizationStoresOnlyAServerSnapshotAndPreparesWithoutReplayingCode() {
        flashOrder();
        when(http.postForString(any(), contains("act=sdxy_send_code&"), anyMap()))
                .thenReturn("{\"code\":0}");
        gateway.sendCode(provider, product, "13800138000");
        verify(http)
                .postForString(
                        any(),
                        contains("act=sdxy_send_code&"),
                        argThat(
                                m ->
                                        "13800138000".equals(m.get("form[phone]"))
                                                && !m.containsKey("form[password]")));
        when(http.postForString(any(), contains("act=sdxy_get_user_info_by_code&"), anyMap()))
                .thenReturn(smsLookupData("upstream-secret"));
        var account =
                gateway.authenticate(provider, product, "SMS", "13800138000", "123456", "测试大学");
        assertFalse(account.lookup().toString().contains("BOUND-STUDENT"));
        assertFalse(account.lookup().toString().contains("upstream-secret"));
        assertFalse(account.toString().contains("upstream-secret"));
        assertEquals("upstream-secret", account.password());
        var form =
                new OrderForm(
                        1,
                        new BigDecimal("2"),
                        Map.of("runRuleId", "r1", "zoneId", "z1", "runType", "SUN"),
                        List.of(taskChange("unused").get("time")),
                        true,
                        null,
                        "private-session-id");
        var prepared = gateway.prepareAuthenticated(provider, product, form, account);
        assertEquals("BOUND-STUDENT", prepared.fields().get("form[student_id]"));
        assertEquals("upstream-secret", prepared.fields().get("form[password]"));
        verify(http, times(1))
                .postForString(any(), contains("act=sdxy_get_user_info_by_code&"), anyMap());
        verify(http, never())
                .postForString(any(), contains("act=sdxy_get_user_info_by_password&"), anyMap());
    }

    @Test
    void smsBoundStudentCannotBeOverwrittenAndUnknownChoiceIsRejected() {
        flashOrder();
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(smsLookupData(""));
        var account = gateway.authenticate(provider, product, "SMS", "13800138000", "123456", "");
        var f =
                new OrderForm(
                        1,
                        BigDecimal.ONE,
                        Map.of("account", "other"),
                        List.of(taskChange("unused").get("time")),
                        true);
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAuthenticated(provider, product, f, account));
        var bad =
                new OrderForm(
                        1,
                        BigDecimal.ONE,
                        Map.of("runRuleId", "foreign", "zoneId", "z1", "runType", "SUN"),
                        f.taskTimes(),
                        true);
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAuthenticated(provider, product, bad, account));
    }

    @Test
    void ruleRefreshUsesBoundStudentAndReloadsNewChoicesWithoutSmsReplay() {
        flashOrder();
        when(http.postForString(any(), contains("act=sdxy_get_user_info_by_code&"), anyMap()))
                .thenReturn(smsLookupData("stored-password"));
        var account =
                gateway.authenticate(provider, product, "SMS", "13800138000", "123456", "测试大学");
        when(http.postForString(any(), contains("act=sdxy_update_run_rule&"), anyMap()))
                .thenReturn("{\"code\":0}");
        when(http.postForString(any(), contains("act=sdxy_get_user_info_by_password&"), anyMap()))
                .thenReturn(smsLookupData("stored-password").replace("日常计划", "已刷新计划"));
        var next = gateway.refreshRules(provider, product, account);
        assertTrue(next.lookup().toString().contains("已刷新计划"));
        verify(http)
                .postForString(
                        any(),
                        contains("act=sdxy_update_run_rule&"),
                        argThat(m -> "BOUND-STUDENT".equals(m.get("student_id"))));
        verify(http, times(1))
                .postForString(any(), contains("act=sdxy_get_user_info_by_code&"), anyMap());
    }

    @Test
    void ruleAcknowledgementWithoutReusablePasswordRequiresReauthorizationNotFakeFreshChoices() {
        flashOrder();
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(smsLookupData(""));
        var account = gateway.authenticate(provider, product, "SMS", "13800138000", "123456", "");
        when(http.postForString(any(), contains("act=sdxy_update_run_rule&"), anyMap()))
                .thenReturn("{\"code\":0}");
        assertNull(gateway.refreshRules(provider, product, account));
        verify(http, never())
                .postForString(any(), contains("act=sdxy_get_user_info_by_password&"), anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sdxy", "ydsjxy", "xbd"})
    void passwordAuthorizationForEveryFlashProjectKeepsExactStudentBindingAndRuleCapabilities(
            String project) {
        flashOrder();
        product.setProject(project);
        product.setRemoteProductId(project);
        String json =
                switch (project) {
                    case "sdxy" -> smsLookupData("ignored-upstream-password");
                    case "ydsjxy" ->
                            "{\"code\":0,\"data\":{\"student\":{\"uid\":\"BOUND-STUDENT\",\"rule_args\":{\"1\":[]},\"default_zone_id\":\"z1\",\"default_zone\":{\"name\":\"授权区域\"}}}}";
                    default ->
                            "{\"code\":0,\"data\":{\"student\":{\"student_id\":\"BOUND-STUDENT\",\"rule\":{\"1\":[]},\"fences\":[{\"fence_id\":\"f1\",\"name\":\"授权区域\"}]}}}";
                };
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(json);
        var account =
                gateway.authenticate(
                        provider,
                        product,
                        "PASSWORD",
                        "authorized-account",
                        "typed-password",
                        "示例学校");
        assertEquals("typed-password", account.password());
        var input =
                new OrderForm(
                        1,
                        new BigDecimal("2"),
                        account.lookup().suggested(),
                        List.of(taskChange("unused").get("time")),
                        true);
        var prepared = gateway.prepareAuthenticated(provider, product, input, account);
        assertEquals(
                "BOUND-STUDENT",
                prepared.fields().get("ydsjxy".equals(project) ? "form[uid]" : "form[student_id]"));
        assertEquals("typed-password", prepared.fields().get("form[password]"));
        assertFalse(account.lookup().toString().contains("BOUND-STUDENT"));
        verify(http, times(1))
                .postForString(
                        any(),
                        contains("act=" + project + "_get_user_info_by_password&appId=" + project),
                        anyMap());
        if ("ydsjxy".equals(project)) {
            assertThrows(
                    BusinessException.class,
                    () -> gateway.refreshRules(provider, product, account));
            verify(http, times(1)).postForString(any(), anyString(), anyMap());
        } else {
            assertNotNull(gateway.refreshRules(provider, product, account));
            verify(http)
                    .postForString(
                            any(),
                            contains("act=" + project + "_update_run_rule&appId=" + project),
                            argThat(m -> "BOUND-STUDENT".equals(m.get("student_id"))));
            verify(http, times(2))
                    .postForString(
                            any(),
                            contains(
                                    "act="
                                            + project
                                            + "_get_user_info_by_password&appId="
                                            + project),
                            anyMap());
        }
    }
}
