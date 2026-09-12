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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

class InternshipNativeServiceGatewayTest {
    ApiHttpClient http;
    InternshipNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setup() throws Exception {
        http = mock(ApiHttpClient.class);
        gateway = new InternshipNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider();
        provider.setProviderType("sxdk_tw");
        provider.setApiUrl("https://authorized.example/direct/api.php");
        provider.setUsername("saved-uid");
        provider.setApiKey("saved-key");
        product = new ServiceProduct();
        product.setProviderType("sxdk_tw");
        product.setProject("zxjy");
        product.setRemoteProductId("zxjy");
        order = new ServiceOrder();
        order.setProviderType("sxdk_tw");
        order.setProject("zxjy");
        order.setExternalOrderNo("R-6");
        order.setUnitCharge(new BigDecimal("0.25"));
        order.setScheduleJson(
                json.writeValueAsString(DailyServicePlan.create(schedule(2), today())));
    }

    LocalDate today() {
        return ServiceTime.now().toLocalDate();
    }

    InternshipSchedule schedule(int days) {
        return new InternshipSchedule(
                today().plusDays(days),
                List.of(1, 2, 3, 4, 5, 6, 7),
                "08:30:00",
                "18:00:00",
                1,
                true,
                true,
                false,
                false,
                false,
                7,
                0,
                null);
    }

    Map<String, String> fields() {
        return new LinkedHashMap<>(
                Map.of(
                        "account",
                        "13800138000",
                        "password",
                        "secret-password",
                        "name",
                        "本人姓名",
                        "address",
                        "授权地址",
                        "lat",
                        "28.12",
                        "lng",
                        "112.12",
                        "gwName",
                        "岗位"));
    }

    OrderForm form() {
        return new OrderForm(0, null, fields(), List.of(), true, schedule(2));
    }

    String remote() throws Exception {
        var row = new LinkedHashMap<String, Object>(fields());
        row.remove("account");
        row.put("phone", "13800138000");
        row.put("id", "R-6");
        row.put("platform", "zxjy");
        row.put("end_time", schedule(2).endDate().toString());
        row.put("check_week", "0,1,2,3,4,5,6");
        row.put("code", 1);
        return json.writeValueAsString(Map.of("code", 0, "data", List.of(row)));
    }

    @Test
    void probeUsesTheSavedDirectEndpointNotAWrapperPathOrInventedPriceRequest() {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"money\":\"private\"}}");
        gateway.testConnection(provider);
        verify(http)
                .getForString(
                        provider,
                        "https://authorized.example/direct/api.php",
                        Map.of("act", "getUserInfo", "uid", "saved-uid", "key", "saved-key"));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"zxjy", "qzt", "gxy", "xyb", "gxzy", "jxzhjy"})
    void createProjectsUseDaysAndZeroBasedUpstreamWeekdaysNotDistance(String platform) {
        product.setProject(platform);
        product.setRemoteProductId(platform);
        var input = form();
        if ("qzt".equals(platform))
            input =
                    new OrderForm(
                            0,
                            null,
                            fields(),
                            List.of(),
                            true,
                            new InternshipSchedule(
                                    schedule(2).endDate(),
                                    schedule(2).weekdays(),
                                    "08:30:00",
                                    "18:00:00",
                                    1,
                                    false,
                                    true,
                                    false,
                                    false,
                                    false,
                                    7,
                                    0,
                                    null));
        var prepared = gateway.prepare(provider, product, input);
        assertEquals(3, prepared.quantity());
        assertNull(prepared.distance());
        assertEquals(BigDecimal.ONE, prepared.billablePerUnit());
        assertEquals(platform, prepared.fields().get("platform"));
        assertEquals("0,1,2,3,4,5,6", prepared.fields().get("check_week"));
        assertEquals("08:30:00", prepared.fields().get("up_check_time"));
        assertEquals("true", prepared.fields().get("week_paper"));
        assertEquals("secret-password", prepared.fields().get("password"));
        assertFalse(prepared.toString().contains("secret-password"));
        assertFalse(prepared.fields().containsKey("quantity"));
        assertFalse(prepared.fields().containsKey("amount"));
        verifyNoInteractions(http);
    }

    @Test
    void preparationRejectsAuthorizationBypassInvalidCalendarsAndRequestFieldInjection() {
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                new OrderForm(0, null, fields(), List.of(), false, schedule(2))));
        var bad = fields();
        bad.put("url", "https://attacker.example");
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                new OrderForm(0, null, bad, List.of(), true, schedule(2))));
        var invalid = fields();
        invalid.put("lat", "999");
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepare(
                                provider,
                                product,
                                new OrderForm(0, null, invalid, List.of(), true, schedule(2))));
        verifyNoInteractions(http);
    }

    @Test
    void schoolLookupHasItsOwnThreeDocumentedResponseShapesAndNeverReturnsRoutingUrls() {
        product.setProject("xxy");
        when(http.getForString(
                        any(), anyString(), argThat(m -> "getSchoolList".equals(m.get("act")))))
                .thenReturn(
                        "{\"code\":0,\"data\":[{\"schools\":[{\"school_id\":\"S-1\",\"school_name\":\"示例学院\",\"differ_api\":\"https://school.example/api.php\"}]}]}");
        var schools = gateway.schools(provider, product, 1, "");
        assertEquals("S-1", schools.items().get(0).id());
        assertFalse(schools.toString().contains("https:"));
        var f = fields();
        f.put("schoolId", "S-1");
        f.put("school", "示例学院");
        var prepared =
                gateway.prepare(
                        provider, product, new OrderForm(0, null, f, List.of(), true, schedule(2)));
        assertEquals("https://school.example/api.php", prepared.fields().get("url"));
        product.setProject("xxt");
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"result\":true,\"froms\":[{\"schoolid\":\"S-2\",\"name\":\"另一学院\"}]}");
        assertEquals("S-2", gateway.schools(provider, product, 1, "学院").items().get(0).id());
        product.setProject("hzj");
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"res\":\"success\",\"data\":{\"region\":[{\"schoolId\":\"S-3\",\"schoolName\":\"职教学院\"}]}}");
        assertEquals("S-3", gateway.schools(provider, product, 1, "").items().get(0).id());
    }

    @Test
    void accountLookupNormalizesOnlyKnownPublicFieldsAndKeepsCredentialsServerSide() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":{\"name\":\"本人姓名\",\"address\":\"授权地址\",\"lat\":28.1,\"lng\":112.1,\"password\":\"remote-secret\",\"token\":\"private-token\"}}");
        var result = gateway.lookup(provider, product, fields());
        assertEquals("本人姓名", result.suggested().get("name"));
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toString().contains("token"));
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        "searchPhoneInfo".equals(m.get("act"))
                                                && "13800138000".equals(m.get("phone"))
                                                && !m.containsKey("account")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"upstreamOrderId", "sourceOrderId"})
    void createNeedsAnExplicitReceiptReferenceRatherThanPhoneBasedInferredSuccess(String field) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"" + field + "\":\"R-6\"}}");
        assertEquals(
                "R-6",
                gateway.execute(
                                provider,
                                product,
                                order,
                                "CREATE",
                                gateway.prepare(provider, product, form()).fields())
                        .externalOrderNo());
        verify(http, never()).getForString(any(), anyString(), anyMap());
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"code\":0}",
                "{\"code\":0,\"selectOrderById\":{\"data\":[{\"id\":66}]}}",
                "{\"code\":1,\"msg\":\"secret-password\"}",
                "{\"code\":0,\"code\":0}",
                "<html>secret</html>"
            })
    void uncertainMutationRepliesAreNeverRetriedOrGuessedByPhone(String body) {
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(body);
        var error =
                assertThrows(
                        ProviderRequestException.class,
                        () -> gateway.execute(provider, product, order, "CREATE", Map.of()));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("secret"));
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
        verifyNoMoreInteractions(http);
    }

    @Test
    void existingPlanEditingReusesOnlyTheExactOwnedRemoteOrderAndNeverExposesPassword()
            throws Exception {
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(remote());
        var view = gateway.orderOptions(provider, order);
        assertFalse(view.suggested().containsKey("password"));
        assertFalse(view.suggested().containsKey("account"));
        assertEquals(schedule(2), view.schedule());
        var prepared =
                gateway.prepareScheduledAction(
                        provider,
                        product,
                        order,
                        new ActionForm("EDIT_SCHEDULE", 0, Map.of("password", ""), schedule(4)));
        assertEquals(2, prepared.quantity());
        assertEquals("secret-password", prepared.fields().get("password"));
        assertFalse(prepared.toString().contains("secret"));
        verify(http, never()).postForString(any(), anyString(), anyMap());
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(remote().replace("R-6", "OTHER"));
        assertThrows(ProviderRequestException.class, () -> gateway.orderOptions(provider, order));
    }

    @Test
    void mismatchedRemoteCalendarCannotBeUsedForLocalRefunds() throws Exception {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(remote().replace("0,1,2,3,4,5,6", "0,1"));
        assertThrows(BusinessException.class, () -> gateway.refundRemaining(provider, order));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    void
            cancellationUsesPublishedLocalUnusedDayTermsAfterAcknowledgementAndPauseFlagsAreNotFlashFlags() {
        when(http.postForString(any(), anyString(), anyMap())).thenReturn("{\"code\":0}");
        var result = gateway.execute(provider, product, order, "REFUND", Map.of());
        assertEquals(3, result.refundedUnits());
        gateway.execute(provider, product, order, "PAUSE", Map.of());
        gateway.execute(provider, product, order, "RESUME", Map.of());
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        "changeStatus".equals(m.get("act"))
                                                && Integer.valueOf(2).equals(m.get("code"))));
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        "changeStatus".equals(m.get("act"))
                                                && Integer.valueOf(1).equals(m.get("code"))));
    }

    @Test
    void logsRemainPlainSanitizedStatusNotRawHtmlOrSecretMessages() {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":0,\"data\":[{\"logTime\":\"2026-09-07"
                                + " 08:00:00\",\"logType\":\"日报\",\"logText\":\"<script>private"
                                + " password</script>\"}]}");
        var logs = gateway.logs(provider, order, 1);
        assertEquals("报告执行记录", logs.items().get(0).status());
        assertFalse(logs.toString().contains("private"));
        assertThrows(BusinessException.class, () -> gateway.logs(provider, order, 2));
    }

    @Test
    void xybLookupReceivesTheChosenModeAndFiveTimesPricingIsFrozenAtCreation() {
        product.setProject("xyb");
        product.setRemoteProductId("xyb");
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":{\"checkOrder\":{\"name\":\"本人\"}}}");
        var f = fields();
        f.put("runMode", "3");
        gateway.lookup(provider, product, f);
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        Integer.valueOf(3).equals(m.get("runType"))
                                                && !m.containsKey("runMode")));
        var s = schedule(2);
        var upgraded =
                new InternshipSchedule(
                        s.endDate(),
                        s.weekdays(),
                        s.checkInTime(),
                        s.checkOutTime(),
                        3,
                        false,
                        true,
                        false,
                        false,
                        false,
                        7,
                        0,
                        null);
        var prepared =
                gateway.prepare(
                        provider,
                        product,
                        new OrderForm(0, null, fields(), List.of(), true, upgraded));
        assertEquals(new BigDecimal("5"), prepared.billablePerUnit());
        assertEquals(3, prepared.fields().get("runType"));
        assertFalse(prepared.fields().containsKey("runMode"));
    }

    @Test
    void reportTypesMatchTheDocumentedProjectSpecificActionsAndCannotSubmitFutureDates()
            throws Exception {
        var s = schedule(2);
        var now = today().toString();
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareScheduledAction(
                                provider,
                                product,
                                order,
                                new ActionForm(
                                        "REPORT",
                                        0,
                                        Map.of(
                                                "startDate",
                                                now,
                                                "endDate",
                                                now,
                                                "reportType",
                                                "打卡"))));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareScheduledAction(
                                provider,
                                product,
                                order,
                                new ActionForm(
                                        "REPORT",
                                        0,
                                        Map.of(
                                                "startDate",
                                                now,
                                                "endDate",
                                                today().plusDays(1).toString(),
                                                "reportType",
                                                "日报"))));
        var prepared =
                gateway.prepareScheduledAction(
                        provider,
                        product,
                        order,
                        new ActionForm(
                                "REPORT",
                                0,
                                Map.of("startDate", now, "endDate", now, "reportType", "日报")));
        when(http.postForString(any(), anyString(), anyMap())).thenReturn("{\"code\":0}");
        gateway.execute(provider, product, order, "REPORT", prepared.fields());
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        "buPapers".equals(m.get("act"))
                                                && "R-6".equals(m.get("id"))
                                                && "日报".equals(m.get("levelName"))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "[]",
                "\"private-token\"",
                "{}",
                "{\"name\":false}",
                "{\"name\":\" \"}"
            })
    void malformedAccountLookupIsNeverAnEmptySuccess(String data) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":0,\"data\":" + data + "}");
        var failure =
                assertThrows(
                        ProviderRequestException.class,
                        () -> gateway.lookup(provider, product, fields()));
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, failure.getReason());
        assertFalse(failure.getMessage().contains("private-token"));
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @Test
    void reportCannotSpanUnboughtDatesEvenWhenBothEndpointsArePaid() throws Exception {
        LocalDate start = today().minusDays(4);
        var s = schedule(2);
        order.setScheduleJson(
                json.writeValueAsString(
                        new DailyServicePlan(
                                start, s, List.of(start, start.plusDays(2), today()))));
        var range =
                Map.of(
                        "startDate",
                        start.toString(),
                        "endDate",
                        today().toString(),
                        "reportType",
                        "日报");
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareScheduledAction(
                                provider, product, order, new ActionForm("REPORT", 0, range)));
        var one =
                Map.of(
                        "startDate",
                        start.plusDays(2).toString(),
                        "endDate",
                        start.plusDays(2).toString(),
                        "reportType",
                        "日报");
        assertEquals(
                0,
                gateway.prepareScheduledAction(
                                provider, product, order, new ActionForm("REPORT", 0, one))
                        .quantity());
        verifyNoInteractions(http);
    }

    @Test
    void orderOptionsExposeOnlyTheLocalImmutableNonsecretPaidCalendar() throws Exception {
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(remote());
        var options = gateway.orderOptions(provider, order);
        assertEquals(
                List.of(today(), today().plusDays(1), today().plusDays(2)), options.paidDates());
        assertFalse(options.toString().contains("secret-password"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> options.paidDates().add(today().plusDays(10)));
    }

    @Test
    void exactOrderLookupRejectsDuplicateIdsAndCrossProjectMatches() throws Exception {
        var row = json.readTree(remote()).path("data").get(0);
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(json.writeValueAsString(Map.of("code", 0, "data", List.of(row, row))));
        assertThrows(ProviderRequestException.class, () -> gateway.orderOptions(provider, order));
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(remote().replace("\"platform\":\"zxjy\"", "\"platform\":\"gxy\""));
        assertThrows(ProviderRequestException.class, () -> gateway.orderOptions(provider, order));
    }

    @Test
    void lookupScansBoundedPagesWithoutEverMatchingByPhone() throws Exception {
        List<Map<String, Object>> unrelated = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            unrelated.add(Map.of("id", "OTHER-" + i, "platform", "zxjy", "phone", "13800138000"));
        String page = json.writeValueAsString(Map.of("code", 0, "data", unrelated));
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(page);
        assertThrows(ProviderRequestException.class, () -> gateway.orderOptions(provider, order));
        verify(http, atMost(10)).getForString(any(), anyString(), anyMap());
        verify(http, atLeastOnce()).getForString(any(), anyString(), anyMap());
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    void calendarOrderIsNormalizedButDuplicateDaysAndRunModeDriftAreRejected() throws Exception {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(remote().replace("0,1,2,3,4,5,6", "6,4,3,5,1,2,0"));
        assertEquals(3, gateway.refundRemaining(provider, order));
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(remote().replace("0,1,2,3,4,5,6", "0,0,1"));
        assertThrows(
                ProviderRequestException.class, () -> gateway.refundRemaining(provider, order));
        var row =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        json.readTree(remote()).path("data").get(0);
        row.put("runType", 3);
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(json.writeValueAsString(Map.of("code", 0, "data", List.of(row))));
        assertThrows(BusinessException.class, () -> gateway.orderOptions(provider, order));
    }
    @ParameterizedTest
    @CsvSource({"1,ACTIVE", "2,PAUSED", "0,ATTENTION", "3,ATTENTION", "-1,ATTENTION"})
    void syncReadsOnlyTheBoundPlanStateAndNeverAttendanceOrMoney(int code, String expected) throws Exception {
        for (boolean textual : List.of(false, true)) {
            ObjectNode row = statusRow();
            if (textual) row.put("code", Integer.toString(code));
            else row.put("code", code);
            stubStatusRows(List.of(row));
            String calendar = order.getScheduleJson();
            var result = gateway.sync(provider, order);
            assertEquals("R-6", result.externalOrderNo());
            assertEquals(expected, result.status());
            assertEquals(0, result.completed(), "plan status does not certify any attendance");
            assertNull(result.refundedUnits());
            assertNull(result.externalSubOrderNo());
            assertEquals(calendar, order.getScheduleJson());
            assertFalse(result.toString().contains("secret-password"));
        }
        verify(http, times(2)).getForString(provider, "https://authorized.example/direct/api.php",
                Map.of("act", "getOrder", "page", 1, "pagesize", 100, "uid", "saved-uid", "key", "saved-key"));
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest
    @CsvSource({"1,COMPLETED", "2,COMPLETED", "0,ATTENTION", "3,ATTENTION", "-1,ATTENTION"})
    void expiredPaidPeriodNeverHidesAnUnknownPlanStateOrFabricatesAttendance(int code, String expected) throws Exception {
        var expired = DailyServicePlan.create(schedule(-1), today().minusDays(3));
        order.setScheduleJson(json.writeValueAsString(expired));
        ObjectNode row = statusRow();
        row.put("end_time", expired.schedule().endDate().toString());
        row.put("code", code);
        stubStatusRows(List.of(row));
        var result = gateway.sync(provider, order);
        assertEquals(expected, result.status());
        assertEquals(0, result.completed());
        assertNull(result.refundedUnits());
        assertEquals(expired, InternshipNativeServiceGateway.plan(order));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @CsvSource({"1,ACTIVE", "2,PAUSED"})
    void theEndDateRemainsAnInclusiveBeijingServiceDay(int code, String expected) throws Exception {
        var lastDay = DailyServicePlan.create(schedule(0), today().minusDays(2));
        order.setScheduleJson(json.writeValueAsString(lastDay));
        ObjectNode row = statusRow();
        row.put("end_time", today().toString());
        row.put("code", code);
        stubStatusRows(List.of(row));
        assertEquals(expected, gateway.sync(provider, order).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "false", "1.0", "1e0", "{}", "[]", "\"\"",
            "\"01\"", "\"1 \"", "\"unknown-secret\"", "10000"})
    void malformedPlanStatusIsNotAConfirmedCheckEvenAfterTheEndDate(String rawCode) throws Exception {
        var expired = DailyServicePlan.create(schedule(-1), today().minusDays(3));
        order.setScheduleJson(json.writeValueAsString(expired));
        ObjectNode row = statusRow();
        row.put("end_time", expired.schedule().endDate().toString());
        row.set("code", json.readTree(rawCode));
        stubStatusRows(List.of(row));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        row.remove("code");
        stubStatusRows(List.of(row));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "{}", "{\"id\":true,\"platform\":\"zxjy\"}",
            "{\"id\":1.0,\"platform\":\"zxjy\"}", "{\"id\":\"OTHER\",\"platform\":null}",
            "{\"id\":\"OTHER\",\"platform\":{}}", "{\"id\":\"OTHER\",\"platform\":true}"})
    void aMalformedRowAfterTheMatchingRowRejectsTheEntireFetchedPage(String rawRow) throws Exception {
        stubStatusRows(List.of(statusRow(), json.readTree(rawRow)));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
    }

    @Test
    void syncRejectsAmbiguousIdsCalendarConflictsAndNonUniqueJson() throws Exception {
        ObjectNode row = statusRow();
        stubStatusRows(List.of(row, row));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        row.put("platform", "gxy");
        stubStatusRows(List.of(row));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        for (String field : List.of("end_time", "check_week", "runType")) {
            row = statusRow();
            switch (field) {
                case "end_time" -> row.put(field, today().plusDays(20).toString());
                case "check_week" -> row.put(field, "1,2,3");
                default -> row.put(field, 3);
            }
            stubStatusRows(List.of(row));
            assertThrows(BusinessException.class, () -> gateway.sync(provider, order));
        }
        for (String malformed : List.of(remote() + "{}", remote().replace("\"code\":1", "\"code\":1,\"code\":2"))) {
            when(http.getForString(any(), anyString(), anyMap())).thenReturn(malformed);
            assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        }
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    void syncCanFindAnExactIdOnTheNextPageWithoutPhoneOrPasswordMatching() throws Exception {
        List<Map<String, Object>> other = new ArrayList<>();
        for (int i = 0; i < 100; i++) other.add(Map.of("id", "OTHER-" + i, "platform", "zxjy",
                "phone", "13800138000", "password", "secret-password"));
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(json.writeValueAsString(Map.of("code", 0, "data", other)), remote());
        assertEquals("ACTIVE", gateway.sync(provider, order).status());
        verify(http).getForString(provider, "https://authorized.example/direct/api.php",
                Map.of("act", "getOrder", "page", 2, "pagesize", 100, "uid", "saved-uid", "key", "saved-key"));
        verify(http, times(2)).getForString(any(), anyString(), anyMap());
        verifyNoMoreInteractions(http);
    }

    @Test
    void noBoundIdMeansNoRequestAndMissingOrOversizedListsNeverGuessAnOrder() throws Exception {
        for (String id : Arrays.asList(null, "", " ", "<script>")) {
            order.setExternalOrderNo(id);
            assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        }
        verifyNoInteractions(http);
        order.setExternalOrderNo("R-6");
        stubStatusRows(List.of());
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        stubStatusRows(Collections.nCopies(101, statusRow()));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
    }

    @Test
    void syncStopsAtTenUniqueFullPagesRatherThanSearchingAnUnboundedAccountHistory() throws Exception {
        var requests = new ArrayList<Integer>();
        when(http.getForString(any(), anyString(), anyMap())).thenAnswer(call -> {
            Map<String, Object> params = call.getArgument(2);
            int page = (Integer) params.get("page");
            requests.add(page);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 100; i++) rows.add(Map.of("id", "OTHER-" + page + "-" + i,
                    "platform", "zxjy", "phone", "13800138000", "password", "secret-password"));
            return json.writeValueAsString(Map.of("code", 0, "data", rows));
        });
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 10).boxed().toList(), requests);
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    void aRepeatedRowOnALaterFetchedPageCannotBeIgnoredEvenIfThatPageContainsTheTarget() throws Exception {
        List<Map<String, Object>> first = new ArrayList<>();
        for (int i = 0; i < 100; i++) first.add(Map.of("id", "OTHER-" + i, "platform", "zxjy"));
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(
                json.writeValueAsString(Map.of("code", 0, "data", first)),
                json.writeValueAsString(Map.of("code", 0, "data", List.of(statusRow(), first.get(0)))));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        verify(http, times(2)).getForString(any(), anyString(), anyMap());
        verifyNoMoreInteractions(http);
    }

    @Test
    void corruptLocalCalendarOrInterruptedReaderNeverStartsAnHttpQuery() {
        String saved = order.getScheduleJson();
        for (String badPlan : Arrays.asList(null, "{}", "[]", "private-value")) {
            order.setScheduleJson(badPlan);
            assertThrows(BusinessException.class, () -> gateway.sync(provider, order));
        }
        order.setScheduleJson(saved);
        Thread.currentThread().interrupt();
        try {
            assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        } finally {
            Thread.interrupted();
        }
        verifyNoInteractions(http);
    }

    private ObjectNode statusRow() throws Exception {
        return (ObjectNode) json.readTree(remote()).path("data").get(0);
    }

    private void stubStatusRows(List<?> rows) throws Exception {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(json.writeValueAsString(Map.of("code", 0, "data", rows)));
    }

    @Test void qztAdviceKeepsSundayMappingTimesAndDateSeparateFromPaidSchedule() throws Exception {
        product.setProject("qzt");product.setRemoteProductId("qzt");
        when(http.postForString(any(),anyString(),anyMap())).thenReturn(json.writeValueAsString(Map.of("code",0,"data",Map.of("name","本人","checkInTime","09:15","checkOutTime","17:45:30","endTime",today().plusDays(15).toString(),"weekList","1,2,6"))));
        var lookup=gateway.lookup(provider,product,fields());assertNull(lookup.schedule());assertNull(lookup.paidDates());
        assertEquals("09:15:00",lookup.advice().checkInTime());assertEquals("17:45:30",lookup.advice().checkOutTime());assertEquals(List.of(1,5,7),lookup.advice().weekdays());assertEquals(today().plusDays(15),lookup.advice().endDate());assertNull(lookup.advice().dailyReport());
        verify(http,times(1)).postForString(any(),anyString(),anyMap());
    }
    @Test void xybUsesOnlyCheckOrderReportFactsAndAbsentFlagsStayUnknown() throws Exception {
        product.setProject("xyb");product.setRemoteProductId("xyb");
        when(http.postForString(any(),anyString(),anyMap())).thenReturn(json.writeValueAsString(Map.of("code",0,"data",Map.of("needMonthlyBlogs",true,"checkOrder",Map.of("name","本人","needDailyBlogs",true,"needWeeklyBlogs",0)))));
        var advice=gateway.lookup(provider,product,fields()).advice();assertTrue(advice.dailyReport());assertFalse(advice.weeklyReport());assertNull(advice.monthlyReport());assertNull(advice.endDate());
    }
    @Test void xxtPairsTimesAndUsesExplicitReportFlags() throws Exception {
        product.setProject("xxt");product.setRemoteProductId("xxt");
        when(http.postForString(any(),anyString(),anyMap())).thenReturn(json.writeValueAsString(Map.of("code",0,"data",Map.of("name","本人","up_check_time","09:00:00","day_paper","1","week_paper","0"))));
        var advice=gateway.lookup(provider,product,fields()).advice();assertNull(advice.checkInTime());assertNull(advice.checkOutTime());assertTrue(advice.dailyReport());assertFalse(advice.weeklyReport());
    }
    @ParameterizedTest @ValueSource(strings={"hzj","gxzy","jxzhjy"})
    void regionalAdviceTreatsOneAndTwoAsTrueWithoutGuessingOtherValues(String type){
        var data=json.createObjectNode().put("day_paper",2).put("week_paper",1).put("month_paper",0);
        InternshipAdvice advice=org.springframework.test.util.ReflectionTestUtils.invokeMethod(gateway,"advice",type,data);
        assertTrue(advice.dailyReport());assertTrue(advice.weeklyReport());assertFalse(advice.monthlyReport());
    }
    @ParameterizedTest @ValueSource(strings={"{\"weekList\":\"0,1\"}","{\"weekList\":\"1,1\"}","{\"weekList\":\"1,8\"}","{\"checkInTime\":\"25:00:00\"}","{\"endTime\":\"2026-02-30\"}"})
    void malformedAdviceNeverChangesDatesOrIntroducesDefaults(String value) throws Exception {
        product.setProject("qzt");product.setRemoteProductId("qzt");var data=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(value);data.put("name","本人");when(http.postForString(any(),anyString(),anyMap())).thenReturn(json.writeValueAsString(Map.of("code",0,"data",data)));
        assertThrows(ProviderRequestException.class,()->gateway.lookup(provider,product,fields()));
    }

}
