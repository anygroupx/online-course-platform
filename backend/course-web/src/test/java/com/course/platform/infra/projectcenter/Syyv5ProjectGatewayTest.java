package com.course.platform.infra.projectcenter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

class Syyv5ProjectGatewayTest {
    ApiHttpClient http;
    Syyv5ProjectGateway gateway;
    ApiProvider provider;
    static final String CUSTOMER =
            "{\"id\":15,\"project_id\":7,\"api_key\":\"private-customer-key\",\"balance\":\"0.00\",\"status\":1}";

    @BeforeEach
    void setup() {
        http = mock(ApiHttpClient.class);
        gateway = new Syyv5ProjectGateway(http, new ProviderUrlNormalizer(),
                new ProjectTicketImagePolicy(new com.course.platform.infra.projectclient.ProjectTicketImageCodec()));
        provider = new ApiProvider();
        provider.setProviderType("syyv5");
        provider.setApiUrl("https://supplier.example/path/openapi.php");
        provider.setApiKey("private-owner-key");
    }

    @Test
    void numericPhpJsonKeepsExactDecimalsIncludingExponentsWithoutPassingThroughDouble() {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"status\":\"success\",\"customer\":{\"id\":15,\"project_id\":7,\"api_key\":\"private-customer-key\",\"balance\":999999999999.123456,\"status\":1}}");
        assertEquals(
                new BigDecimal("999999999999.123456"),
                gateway.customer(provider, "7", "15").balance());
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"status\":\"success\",\"apiKeys\":[{\"project_id\":7,\"project_name\":\"项目甲\",\"price\":1e-6}]}");
        assertEquals(
                0,
                new BigDecimal("0.000001")
                        .compareTo(gateway.projects(provider).get(0).basePrice()));
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.customer(provider, "7", "9999999999999999999"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e1000000", "1e-1000000", "10e2147483647"})
    void absurdNumericExponentsAreRejectedBeforeExpansionOrScaleNormalization(String value) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"status\":\"success\",\"balance_after\":" + value + "}");
        assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2),
                () ->
                        assertThrows(
                                ProviderRequestException.class,
                                () ->
                                        gateway.adjust(
                                                provider,
                                                "7",
                                                "15",
                                                BigDecimal.ONE,
                                                "87c5c14d-2eba-4dd7-a023-000000000001")));
    }

    @Test
    void connectionCheckOnlyReadsProjectCatalogueWithSavedFullUrlAndNoDefaultPrice() {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"status\":\"success\",\"apiKeys\":[{\"project_id\":7,\"project_name\":\"项目甲\",\"price\":\"0.123456\",\"api_key\":\"never-expose\",\"redirect_url\":\"javascript:secret\"}]}");
        var p = gateway.projects(provider).get(0);
        assertEquals("7", p.id());
        assertEquals(new BigDecimal("0.123456"), p.basePrice());
        assertFalse(p.toString().contains("never-expose"));
        gateway.testConnection(provider);
        verify(http, times(2))
                .getForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(Map.of("action", "getUserApiKeys", "api_key", "private-owner-key")));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"price\":1}",
                "{\"project_id\":7,\"project_name\":\"项目\"}",
                "{\"project_id\":7,\"project_name\":\"项目\",\"price\":0}",
                "{\"project_id\":7,\"project_name\":\"项目\",\"price\":\"1e2\"}",
                "{\"project_id\":7,\"project_name\":\"项目\",\"price\":-1}",
                "{\"project_id\":\"bad\",\"project_name\":\"项目\",\"price\":1}"
            })
    void malformedCatalogNeverInventsPriceOrIdentifier(String item) {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn("{\"status\":\"success\",\"apiKeys\":[" + item + "]}");
        assertThrows(ProviderRequestException.class, () -> gateway.projects(provider));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"status\":\"error\",\"message\":\"private-owner-key\"}",
                "{\"status\":\"success\"}",
                "{\"status\":\"success\",\"status\":\"success\",\"apiKeys\":[]}",
                "{\"status\":\"success\",\"apiKeys\":[]} trailing",
                "<html>secret</html>",
                "{\"status\":1,\"apiKeys\":[]}"
            })
    void rejectedMalformedOrAmbiguousRepliesAreSanitized(String body) {
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(body);
        var ex = assertThrows(ProviderRequestException.class, () -> gateway.projects(provider));
        assertNull(ex.getCause());
        assertFalse(ex.getMessage().contains("private-owner-key"));
    }

    @Test
    void openingIsOneLegacyMutatingGetWithExactlyZeroInitialBalance() {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn("{\"status\":\"success\",\"customer\":" + CUSTOMER + "}");
        var receipt = gateway.provision(provider, "7");
        assertEquals("15", receipt.id());
        assertFalse(receipt.toString().contains("private-customer-key"));
        verify(http)
                .getForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "generateCustomer",
                                        "api_key",
                                        "private-owner-key",
                                        "project_id",
                                        "7",
                                        "balance",
                                        "0")));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    void uncertainOpeningNeverAutomaticallyReplays() {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT));
        assertThrows(ProviderRequestException.class, () -> gateway.provision(provider, "7"));
        verify(http, times(1)).getForString(any(), anyString(), anyMap());
    }

    @Test
    void nonzeroInitialBalanceAndWrongProjectAreNotAcceptedAsFreeCreation() {
        for (String customer :
                List.of(
                        CUSTOMER.replace("0.00", "10"),
                        CUSTOMER.replace("\"project_id\":7", "\"project_id\":8"),
                        CUSTOMER.replace("private-customer-key", ""))) {
            when(http.getForString(any(), anyString(), anyMap()))
                    .thenReturn("{\"status\":\"success\",\"customer\":" + customer + "}");
            assertThrows(ProviderRequestException.class, () -> gateway.provision(provider, "7"));
        }
    }

    @Test
    void boundedCustomerReadAcceptsOnlyExactCustomerAndProjectNotAnotherAccountOrDuplicate() {
        for (String data :
                List.of("\"customer\":" + CUSTOMER, "\"customers\":[" + CUSTOMER + "]")) {
            when(http.getForString(any(), anyString(), anyMap()))
                    .thenReturn("{\"status\":\"success\"," + data + "}");
            assertEquals("15", gateway.customer(provider, "7", "15").id());
        }
        for (String data :
                List.of(
                        "\"customers\":[]",
                        "\"customers\":[" + CUSTOMER + "," + CUSTOMER + "]",
                        "\"customer\":" + CUSTOMER.replace("\"id\":15", "\"id\":16"),
                        "\"customer\":" + CUSTOMER.replace("\"project_id\":7", "\"project_id\":8"),
                        "\"customer\":" + CUSTOMER + ",\"customers\":[" + CUSTOMER + "]")) {
            when(http.getForString(any(), anyString(), anyMap()))
                    .thenReturn("{\"status\":\"success\"," + data + "}");
            assertThrows(
                    ProviderRequestException.class, () -> gateway.customer(provider, "7", "15"));
        }
        verify(http, atLeastOnce())
                .getForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "getCustomerInfo",
                                        "api_key",
                                        "private-owner-key",
                                        "customer_ids",
                                        "15")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.123456", "-1.123456"})
    void walletAdjustmentUsesSignedExactDecimalPostAndNeverSendsKeysInEndpoint(String amount) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"status\":\"success\",\"balance_after\":\"3.50\",\"actual_price\":\"0.12\"}");
        String op = UUID.randomUUID().toString();
        var result = gateway.adjust(provider, "7", "15", new BigDecimal(amount), op);
        assertEquals(new BigDecimal("3.50"), result.balanceAfter());
        verify(http)
                .postForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "adjustCustomerBalance",
                                        "api_key",
                                        "private-owner-key",
                                        "customer_id",
                                        "15",
                                        "project_id",
                                        "7",
                                        "amount",
                                        amount,
                                        "remark",
                                        "Platform operation " + op)));
        verify(http, never()).getForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"balance_after\":-1}",
                "{\"balance_after\":\"secret\"}",
                "{\"balance_after\":\"1.0000001\"}",
                "{\"balance_after\":1,\"balance_before\":1}",
                "{\"balance_after\":1,\"customer_id\":16}",
                "{\"balance_after\":1,\"project_id\":8}"
            })
    void invalidAdjustmentReceiptNeverBecomesSuccess(String fields) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"status\":\"success\"," + fields.substring(1));
        assertThrows(
                ProviderRequestException.class,
                () ->
                        gateway.adjust(
                                provider, "7", "15", BigDecimal.ONE, UUID.randomUUID().toString()));
    }

    @Test
    void plainHttpNeverCarriesTheMainApiKey() {
        provider.setApiUrl("http://supplier.example/api.php");
        assertThrows(ProviderRequestException.class, () -> gateway.projects(provider));
        verifyNoInteractions(http);
    }
}
