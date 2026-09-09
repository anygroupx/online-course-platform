package com.course.platform.infra.integration;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static com.course.platform.infra.integration.PhpTemplateReadOnlyConnector.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhpTemplateReadOnlyConnectorTest {
    private ApiHttpClient http;
    private ApiProvider provider;
    private PhpTemplateReadOnlyConnector connector;

    @BeforeEach void setUp() {
        http = mock(ApiHttpClient.class);
        provider = new ApiProvider();
        provider.setId(9L);
        provider.setStatus(ApiProvider.STATUS_ACTIVE);
        provider.setApiUrl("https://template.example/site/");
        provider.setUsername("operator-uid");
        provider.setApiKey("test-key-never-in-url");
        provider.setPassword("unused-password");
        use(JIGUANG);
    }

    private void use(PhpTemplateReadOnlyConnector.Protocol protocol) {
        connector = new PhpTemplateReadOnlyConnector(protocol, http, new ProviderUrlNormalizer());
        provider.setProviderType(connector.getProviderType());
    }

    private void responds(String json) {
        when(http.postForString(eq(provider), anyString(), anyMap())).thenReturn(json);
    }

    private ProviderRequestException fails(String json) {
        responds(json);
        ProviderRequestException error = assertThrows(ProviderRequestException.class,
                () -> connector.fetchCatalog(provider, null));
        assertEquals(ProviderRequestException.PUBLIC_MESSAGE, error.getMessage());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("upstream-secret"));
        return error;
    }

    @Test void flashUsesZeroSuccessCodeOneProjectOneRequestAndCorrectUnit() {
        use(FLASH);
        responds("{\"code\":0,\"data\":\"0.25\"}");
        var quote = connector.fetchCatalog(provider, null).get(0);
        assertEquals("sdxy", quote.id());
        assertEquals("元/次", quote.priceUnit());
        assertEquals(new BigDecimal("0.25"), quote.unitPrice());
        verify(http).postForString(provider, "https://template.example/site/flash/api.php?act=get_price&appId=sdxy",
                Map.of("login_uid", "operator-uid", "login_key", "test-key-never-in-url"));
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest @ValueSource(strings = {"ydsjxy", "xbd"})
    void flashOtherProjectsArePerKmWithoutComputingRepairMultiplier(String project) {
        use(FLASH); responds("{\"code\":0,\"data\":0.5}");
        assertEquals("元/公里（倍率另计）", connector.fetchCatalog(provider, project).get(0).priceUnit());
        verify(http).postForString(eq(provider), eq("https://template.example/site/flash/api.php?act=get_price&appId=" + project), anyMap());
    }

    @Test void flashDoesNotTreatOrdinaryActionSuccessCodeAsPriceSuccess() {
        use(FLASH);
        assertEquals(ProviderRequestException.Reason.UPSTREAM_REJECTED, fails("{\"code\":1,\"data\":0.5}").getReason());
    }

    @ParameterizedTest @ValueSource(strings = {"refund", "sdxy&act=add", "https://evil.example", "SDXY", "sdxy "})
    void arbitraryProjectCannotBecomeRemoteAction(String project) {
        use(FLASH);
        assertThrows(BusinessException.class, () -> connector.fetchCatalog(provider, project));
        verifyNoInteractions(http);
    }

    @Test void heishaDictionaryAndJiguangArrayAreNotInterchangeable() {
        use(HEISHA);
        responds("{\"code\":1,\"data\":{\"1\":{\"name\":\"日常项目\",\"price\":\"0.15\"}}}");
        assertEquals("1", connector.fetchCatalog(provider, null).get(0).id());
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, fails("{\"code\":1,\"data\":[]}").getReason());
        use(JIGUANG);
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, fails("{\"code\":1,\"data\":{}}").getReason());
    }

    @Test void catalogIsAWhiteListWithDecimalStringsAndNoCredentialsOrCid() throws Exception {
        responds("{\"code\":1,\"key\":\"upstream-secret\",\"data\":[{\"product_id\":2,\"cid\":789,\"name\":\"日常项目\",\"price\":\"0.125000\",\"password\":\"upstream-secret\"}]}");
        var products = connector.fetchCatalog(provider, null);
        String json = new ObjectMapper().writeValueAsString(products);
        assertTrue(json.contains("\"unitPrice\":\"0.125000\""));
        assertFalse(json.contains("cid"));
        assertFalse(json.contains("password"));
        assertFalse(json.contains("upstream-secret"));
        verify(http).postForString(eq(provider), eq("https://template.example/site/jiguang/jiguang.api.php?act=products"),
                eq(Map.of("login_uid", "operator-uid", "login_key", "test-key-never-in-url")));
    }

    @Test void emptyCatalogIsDifferentFromInvalidOrRejectedResponse() {
        responds("{\"code\":1,\"data\":[]}");
        assertTrue(connector.fetchCatalog(provider, null).isEmpty());
        assertEquals(ProviderRequestException.Reason.UPSTREAM_REJECTED, fails("{\"code\":-2,\"msg\":\"upstream-secret\"}").getReason());
    }

    @ParameterizedTest @ValueSource(strings = {
        "null", "[]", "<html>upstream-secret</html>", "{\"code\":1}", "{\"code\":true,\"data\":[]}",
        "{\"code\":1,\"data\":null}", "{\"code\":1,\"code\":1,\"data\":[]}", "{\"code\":1,\"data\":[]}{}",
        "{code:1,data:[]}", "{\"code\":1,\"data\":[{\"name\":\"missing ID\",\"price\":1}]}",
        "{\"code\":1,\"data\":[{\"product_id\":1,\"name\":\"a\",\"price\":1},{\"product_id\":1,\"name\":\"b\",\"price\":1}]}"
    }) void malformedPayloadsFailClosedWithoutEchoingBody(String body) {
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, fails(body).getReason());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "NaN", "Infinity", "1e999999", "0.0000001", "1000000000", "", " 1", "1x"})
    void invalidMonetaryValuesAreNeverCoercedToZero(String price) {
        use(FLASH);
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, fails("{\"code\":0,\"data\":\"" + price + "\"}").getReason());
    }

    @ParameterizedTest @ValueSource(strings = {"10.0", "100.00", "1e2", "0.000001", "100000000.00"})
    void numericDecimalsAreNotRejectedWhenJacksonNormalizesTheirExponent(String number) {
        use(FLASH); responds("{\"code\":0,\"data\":" + number + "}");
        assertEquals(0, new BigDecimal(number).compareTo(connector.fetchCatalog(provider, null).get(0).unitPrice()));
    }

    @Test void hostileNumericExponentsAreRejectedBeforePlainStringExpansion() {
        use(FLASH);
        for (String number : new String[]{"1e999999", "1e-999999", "1000000000.00", "-10.0"}) {
            assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE,
                    fails("{\"code\":0,\"data\":" + number + "}").getReason());
        }
    }

    @Test void boundedParserRejectsDeepLargeAndUnboundedStrings() {
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE,
                fails("{\"code\":1,\"data\":[],\"nested\":" + "[".repeat(30) + "0" + "]".repeat(30) + "}").getReason());
        assertEquals(ProviderRequestException.Reason.RESPONSE_TOO_LARGE, fails("x".repeat(262145)).getReason());
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE,
                fails("{\"code\":1,\"data\":[],\"extra\":\"" + "x".repeat(10000) + "\"}").getReason());
    }

    @Test void missingApiKeyDoesNotFallBackToPasswordOrSendBlankAuthentication() {
        provider.setApiKey("");
        assertThrows(ProviderRequestException.class, () -> connector.testConnection(provider));
        verifyNoInteractions(http);
    }

    @Test void callerCannotMistypeBaseIntoAnotherEndpointOrMismatchType() {
        provider.setApiUrl("https://template.example/site/jiguang/jiguang.api.php");
        assertThrows(RuntimeException.class, () -> connector.testConnection(provider));
        provider.setApiUrl("https://template.example/site");
        provider.setProviderType("flash");
        assertThrows(ProviderRequestException.class, () -> connector.testConnection(provider));
        verifyNoInteractions(http);
    }

    @Test void schoolPageUsesOnlyProvenListFieldsAndBoundedFormParameters() throws Exception {
        responds("{\"code\":1,\"data\":{\"list\":[{\"id\":1,\"name\":\"甲大学\",\"api_key\":\"upstream-secret\"},{\"id\":\"school-2\",\"name\":\"乙大学\"}],\"total\":99999999}}");
        var page = connector.searchSchools(provider, new PluginPageQuery(2, 2, " 大学 "));
        assertEquals(2, page.page()); assertEquals(2, page.items().size()); assertTrue(page.hasMore());
        assertFalse(new ObjectMapper().writeValueAsString(page).contains("upstream-secret"));
        verify(http).postForString(provider, "https://template.example/site/jiguang/jiguang.api.php?act=schools",
                Map.of("login_uid", "operator-uid", "login_key", "test-key-never-in-url", "page", 2, "pageSize", 2, "keyword", "大学"));
    }

    @Test void schoolsRequireCorrectShapeNoDuplicatesAndNoOversizedPage() {
        for (String data : new String[]{"[]", "{}", "{\"list\":{}}",
                "{\"list\":[{\"id\":1,\"name\":\"a\"},{\"id\":1,\"name\":\"a\"}]}"}) {
            responds("{\"code\":1,\"data\":" + data + "}");
            assertThrows(ProviderRequestException.class, () -> connector.searchSchools(provider, new PluginPageQuery(1, 1, "")));
        }
        responds("{\"code\":1,\"data\":{\"list\":[]}}");
        assertFalse(connector.searchSchools(provider, new PluginPageQuery(2, 20, "")).hasMore());
    }

    @Test void schoolCapabilityCannotBeGuessedForOtherPlugins() {
        use(FLASH);
        assertThrows(ProviderRequestException.class, () -> connector.searchSchools(provider, new PluginPageQuery(1, 20, "")));
        verifyNoInteractions(http);
    }

    @Test void timeoutIsNeverRetriedAndSafeReasonIsPreserved() {
        ProviderRequestException expected = new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
        when(http.postForString(any(), anyString(), anyMap())).thenThrow(expected);
        assertSame(expected, assertThrows(ProviderRequestException.class, () -> connector.testConnection(provider)));
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @Test void pageBoundsAreCheckedEvenOutsideHttpController() {
        for (int[] bounds : new int[][]{{0, 20}, {-1, 20}, {10001, 20}, {1, 0}, {1, 101}}) {
            assertThrows(BusinessException.class, () -> new PluginPageQuery(bounds[0], bounds[1], ""));
        }
        assertThrows(BusinessException.class, () -> new PluginPageQuery(1, 20, "x".repeat(81)));
        assertThrows(BusinessException.class, () -> new PluginPageQuery(1, 20, "a\nb"));
    }
}
