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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

class SsbenzDistanceGatewayTest {
    ApiHttpClient http;
    SsbenzDistanceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;

    @BeforeEach void setup() throws Exception {
        http = mock(ApiHttpClient.class);
        gateway = new SsbenzDistanceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider();
        provider.setProviderType("ssbenz_xbd");
        provider.setApiUrl("https://authorized.example/install/xbd/ydapi/");
        provider.setApiKey("saved+token&secret");
        provider.setUsername("unused-uid");
        provider.setToken("unused-token");
        product = new ServiceProduct();
        product.setProviderType("ssbenz_xbd");
        product.setProject("xbd");
        product.setRemoteProductId("0");
        order = new ServiceOrder();
        order.setProviderType("ssbenz_xbd");
        order.setProject("xbd");
        order.setRemoteProductId("0");
        order.setQuantity(1);
        order.setDistance(new BigDecimal("120.50"));
        order.setExternalOrderNo("42");
        order.setAccountLabel("13***00");
        order.setScheduleJson(new ObjectMapper().writeValueAsString(gateway.prepare(provider, product, form()).distancePlan()));
    }

    Map<String, String> fields() {
        return new LinkedHashMap<>(Map.of("account", "13800138000", "password", "my-password",
                "schoolName", "", "startTime", "09:05", "endTime", "21:10", "weekdays", "5,1,3"));
    }
    OrderForm form() { return form("120.50", fields()); }
    OrderForm form(String distance, Map<String, String> values) {
        return new OrderForm(1, new BigDecimal(distance), values, List.of(), true);
    }
    void response(String text) { when(http.postForString(any(), anyString(), anyMap())).thenReturn(text); }
    RemoteResult create() {
        return gateway.execute(provider, product, order, "CREATE", gateway.prepare(provider, product, form()).fields());
    }

    @Test void catalogAndProbeOnlyReadTwoPricesWithSavedPostTokenAndIgnoreExecutableFields() throws Exception {
        response("{\"code\":1,\"xbdpr\":0.123456,\"xbdprs\":\"0.20\",\"dj\":\"DROP TABLE sys_user\",\"gonggao\":\"<script>secret</script>\"}");
        var catalog = gateway.fetchCatalog(provider, "xbd");
        assertEquals(List.of("0", "1"), catalog.stream().map(p -> p.id()).toList());
        assertEquals(new BigDecimal("0.123456"), catalog.get(0).unitPrice());
        assertEquals("元/公里", catalog.get(1).priceUnit());
        String json = new ObjectMapper().writeValueAsString(catalog);
        assertFalse(json.contains("script"));
        assertFalse(json.contains("DROP"));
        assertFalse(json.contains("saved"));
        assertFalse(json.contains("晨跑"));
        gateway.testConnection(provider);
        verify(http, times(2)).postForString(same(provider),
                eq("https://authorized.example/install/xbd/ydapi/school"), eq(Map.of("token", "saved+token&secret")));
        assertFalse(gateway.supportsSchools());
        assertEquals("xbd", gateway.projects().get(0).id());
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "true", "-0.1", "10000", "0.1234567", "\"NaN\"", "\"1e2\"", "\"0.1junk\"", "{}", "[]", "\"\""})
    void invalidOrMissingPriceIsNotZeroOrSaleable(String price) {
        response("{\"code\":1,\"xbdpr\":" + price + ",\"xbdprs\":1}");
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE,
                assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, null)).getReason());
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest @ValueSource(strings = {"{\"code\":1,\"code\":1}", "{\"code\":1}{}", "null", "[]", "{\"code\":true}", "{\"code\":1.0}", "{\"code\":\"01\"}", "<html>error</html>", "{\"code\":1,\"xbdpr\":1,\"xbdpr\":2}"})
    void parserRejectsAmbiguousEnvelopesWithoutReturningRemoteText(String raw) {
        response(raw);
        var failure = assertThrows(ProviderRequestException.class, () -> gateway.fetchCatalog(provider, null));
        assertFalse(failure.getMessage().contains(raw));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "1"})
    void prepareIsOfflineAndUsesOneTotalDistanceOrderAndNeutralTypeCodes(String type) {
        product.setRemoteProductId(type);
        var prepared = gateway.prepare(provider, product, form());
        assertEquals(1, prepared.quantity());
        assertEquals(new BigDecimal("120.50"), prepared.distance());
        assertEquals(prepared.distance(), prepared.billablePerUnit());
        assertEquals("13***00", prepared.accountLabel());
        assertEquals("135", prepared.fields().get("weeks"));
        assertEquals(9, prepared.fields().get("ks_h"));
        assertEquals(5, prepared.fields().get("ks_m"));
        assertEquals("120.50", prepared.fields().get("zkm"));
        assertEquals(Integer.parseInt(type), prepared.fields().get("type"));
        assertEquals("自动识别", prepared.fields().get("school"));
        assertEquals(List.of(1, 3, 5), prepared.distancePlan().weekdays());
        assertFalse(prepared.toString().contains("my-password"));
        assertFalse(prepared.distancePlan().toString().contains("13800138000"));
        assertFalse(prepared.fields().containsKey("token"));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"0.01", "50.01", "1000", "999999.99"})
    void totalDistanceIsNotRestrictedToFiftyOrAnIntegerCount(String distance) {
        var result = gateway.prepare(provider, product, form(distance, fields()));
        assertEquals(1, result.quantity());
        assertEquals(0, new BigDecimal(distance).compareTo(result.distance()));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1000000", "0.001", "120.501", "999999.991"})
    void invalidDistanceIsRejectedNotRounded(String distance) {
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form(distance, fields())));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"05:59", "23:00", "9:00", "09:00:00", "09:60", "21:10", "21:11"})
    void invalidOrReversedWindowNeverFallsBackToDefaultHours(String start) {
        var values = fields(); values.put("startTime", start);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form("120.50", values)));
        verifyNoInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "8", "1,1", "1,2,3,4,5,6,7,1", "1, 3", "135", "1.0", "a"})
    void invalidWeeksCannotSilentlyAlterPlan(String weeks) {
        var values = fields(); values.put("weekdays", weeks);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form("120.50", values)));
        verifyNoInteractions(http);
    }

    @Test void formRejectsUnknownFieldsCredentialInjectionOtherSessionsAndOtherQuantityModels() {
        for (String key : List.of("token", "type", "user", "pass", "url", "quantity", "zkm", "runType")) {
            var values = fields(); values.put(key, "injected");
            assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form("120.50", values)));
        }
        var values = fields(); values.put(null, "bad");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form("120.50", values)));
        for (var input : List.of(
                new OrderForm(2, new BigDecimal("2"), fields(), List.of(), true),
                new OrderForm(1, new BigDecimal("2"), fields(), List.of(), false),
                new OrderForm(1, new BigDecimal("2"), fields(), List.of("2030-01-01 10:00:00"), true),
                new OrderForm(1, new BigDecimal("2"), fields(), List.of(), true, null, UUID.randomUUID().toString())))
            assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, input));
        verifyNoInteractions(http);
    }

    @Test void accountFieldsAreBoundedNoControlsAndPasswordIsNotTrimmedByJava() {
        var values = fields(); values.put("password", " my-password ");
        assertEquals(" my-password ", gateway.prepare(provider, product, form("120.50", values)).fields().get("pass"));
        for (String key : List.of("account", "password", "schoolName")) {
            values = fields(); values.put(key, "bad\nfield");
            var input = form("120.50", values);
            assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, input));
        }
        values = fields(); values.put("schoolName", "学".repeat(121));
        var tooLong = form("120.50", values);
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, tooLong));
        verifyNoInteractions(http);
    }

    @Test void aCreateReceiptIsOnlySubmittedEvenIfItClaimsCompletedOrContainsSecrets() throws Exception {
        response("{\"code\":1,\"id\":42,\"status\":\"completed\",\"msg\":\"private-remote-password\",\"completed\":1000}");
        RemoteResult result = create();
        assertEquals("42", result.externalOrderNo());
        assertEquals("SUBMITTED", result.status());
        assertNull(result.completed()); assertNull(result.refundedUnits());
        assertFalse(new ObjectMapper().writeValueAsString(result).contains("private"));
        var expected = new LinkedHashMap<>(gateway.prepare(provider, product, form()).fields());
        expected.put("token", "saved+token&secret");
        verify(http, times(1)).postForString(provider, "https://authorized.example/install/xbd/ydapi/add", expected);
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"{\"code\":-1,\"msg\":\"retry now secret\"}", "{\"code\":1}", "{\"code\":1,\"id\":0}", "{\"code\":1,\"id\":\"other\"}", "{\"code\":1,\"id\":\"042\"}", "{\"code\":1,\"id\":42.0}", "{\"code\":1,\"id\":42,\"id\":43}"})
    void ambiguousCreationNeverRetriesOrClaimsNoDebit(String raw) {
        response(raw);
        assertThrows(ProviderRequestException.class, this::create);
        verify(http, times(1)).postForString(any(), endsWith("/add"), anyMap());
        verifyNoMoreInteractions(http);
    }

    @Test void timeoutIsDispatchedOnceWithoutPhpFallback() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        assertThrows(ProviderRequestException.class, this::create);
        verify(http, times(1)).postForString(any(), endsWith("/add"), anyMap());
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "1", "2"})
    void syncMatchesBoundIdNotFirstRowAndNeverTreatsDockStatusAsCompletion(String status) throws Exception {
        response("{\"code\":1,\"data\":[{\"id\":41,\"status\":1},{\"id\":\"42\",\"status\":\"" + status
                + "\",\"yfees\":0,\"fees\":999,\"statuslog\":\"<script>private</script>\",\"pass\":\"secret\",\"tdkm\":120.5}]}");
        var result = gateway.sync(provider, order);
        assertEquals("42", result.externalOrderNo());
        assertEquals("1".equals(status) ? "SUBMITTED" : "SUBMISSION_REVIEW", result.status());
        assertNull(result.completed()); assertNull(result.refundedUnits());
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("script")); assertFalse(json.contains("secret"));
        verify(http).postForString(provider, "https://authorized.example/install/xbd/ydapi/order", Map.of("token", "saved+token&secret"));
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"[]", "[{\"id\":41,\"status\":1}]", "[{\"id\":42,\"status\":1},{\"id\":42,\"status\":0}]", "[{\"id\":42,\"status\":1},{\"id\":42,\"status\":1}]", "[{\"id\":42}]", "[{\"id\":null,\"status\":1}]", "[{\"id\":42,\"status\":3}]", "[{\"id\":42,\"status\":true}]", "[{\"id\":42,\"status\":1.0}]", "[{\"id\":\"042\",\"status\":1}]", "{}", "null", "[42]"})
    void malformedMissingOrDuplicateResultsDoNotGuessOwnershipOrRefunds(String rows) {
        response("{\"code\":1,\"data\":" + rows + "}");
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        verify(http, times(1)).postForString(any(), endsWith("/order"), anyMap());
        verifyNoMoreInteractions(http);
    }

    @Test void boundedListsAndContradictoryEnvelopeIdsFailClosed() {
        response("{\"code\":1,\"id\":43,\"data\":[{\"id\":42,\"status\":1}]}");
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        String rows = String.join(",", java.util.stream.IntStream.rangeClosed(1, 1001)
                .mapToObj(i -> "{\"id\":" + i + ",\"status\":1}").toList());
        response("{\"code\":1,\"data\":[" + rows + "]}");
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
        response(" ".repeat(262145));
        assertThrows(ProviderRequestException.class, () -> gateway.sync(provider, order));
    }

    @ParameterizedTest @ValueSource(strings = {"http://authorized.example/xbd/ydapi", "https://authorized.example/xbd/ydapi/add", "https://authorized.example/xbd/ydapi/order.php", "https://authorized.example/xbd/ydapi/add.php/extra", "https://authorized.example/xbd/ydapi/?token=secret", "https://127.0.0.1/xbd/ydapi", "https://authorized.example/xbd/ydapi#fragment"})
    void requiresHttpsApiDirectoryAndNeverNormalizesAnActionIntoSomethingElse(String url) {
        provider.setApiUrl(url);
        assertThrows(com.course.platform.infra.http.SafeHttpException.class, () -> gateway.fetchCatalog(provider, null));
        verifyNoInteractions(http);
    }

    @Test void unsupportedOperationsAndTamperedSnapshotsNeverCallRemote() {
        assertThrows(BusinessException.class, () -> gateway.lookup(provider, product, fields()));
        assertThrows(BusinessException.class, () -> gateway.refundRemaining(provider, order));
        assertThrows(BusinessException.class, () -> gateway.checkAddTimes(provider, order, 1));
        assertThrows(BusinessException.class, () -> gateway.logs(provider, order, 1));
        assertThrows(BusinessException.class, () -> gateway.schools(provider, product, 1, "学校"));
        assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "REFUND", Map.of()));
        for (String key : List.of("token", "type", "zkm", "user", "ks_h", "weeks")) {
            var body = new LinkedHashMap<>(gateway.prepare(provider, product, form()).fields());
            body.put(key, key.equals("zkm") ? "100.00" : "injected");
            assertThrows(BusinessException.class, () -> gateway.execute(provider, product, order, "CREATE", body));
        }
        order.setQuantity(10);
        assertThrows(BusinessException.class, this::create);
        product.setProject("ydsj");
        assertThrows(BusinessException.class, () -> gateway.prepare(provider, product, form()));
        provider.setProviderType("27");
        assertThrows(BusinessException.class, () -> gateway.fetchCatalog(provider, null));
        verifyNoInteractions(http);
    }
}
