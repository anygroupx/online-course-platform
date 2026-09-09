package com.course.platform.infra.servicecommerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.servicecommerce.ServiceProduct;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

class HeishaFaceGatewayTest {
    static final String PREFLIGHT =
            """
{"code":1,"data":{"account":{"phone":"13800138000","schoolName":"测试大学","password":"discard-extra-secret"},"photos":["discard-photo"],"runPreflightToken":"private-preflight-token","checkedAt":"2026-09-08T10:00:00+08:00","plans":[{"planOptionId":"p1","runName":"本人计划","singleMinDistanceKm":1,"singleMaxDistanceKm":3,"timeFragments":[{"raw":"07:00-10:00"}]}],"fences":[{"fenceOptionId":"f1","fenceName":"本人区域"}]}}
""";
    static final String COLLECT =
            """
{"code":1,"data":{"phone":"13800138000","faceToken":"private-face-token","collectUrl":"https://collect.example/collect?c=private-link","batchStatus":{"completed":false,"fileCount":0,"minFileCount":2,"maxFileCount":3}}}
""";
    static final String COMPLETE =
            """
{"code":1,"data":{"phone":"13800138000","faceToken":"private-face-token","collectUrl":"https://attacker.invalid/replaced","batchStatus":{"completed":true,"fileCount":2,"minFileCount":2,"maxFileCount":3}}}
""";
    ApiHttpClient http;
    PhpNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;

    @BeforeEach
    void setup() {
        http = mock(ApiHttpClient.class);
        gateway = new PhpNativeServiceGateway(http, new ProviderUrlNormalizer());
        provider = new ApiProvider();
        provider.setProviderType("heisha");
        provider.setApiUrl("https://supplier.example/site");
        provider.setUsername("saved-uid");
        provider.setApiKey("saved-key");
        product = new ServiceProduct();
        product.setProviderType("heisha");
        product.setProject("default");
        product.setRemoteProductId("3");
        when(http.postForString(any(), contains("act=preflight"), anyMap())).thenReturn(PREFLIGHT);
        when(http.postForString(any(), contains("act=collect_link"), anyMap())).thenReturn(COLLECT);
        when(http.postForString(any(), contains("act=face_check"), anyMap())).thenReturn(COMPLETE);
    }

    AccountSnapshot authorize() {
        return gateway.authenticate(
                provider, product, "PASSWORD", "13800138000", "private-password", "");
    }

    OrderForm form(Map<String, String> fields) {
        return new OrderForm(10, new BigDecimal("2"), fields, List.of(), true);
    }

    Map<String, String> plan() {
        return Map.of("planOptionId", "p1", "fenceOptionId", "f1", "runTime", "08:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "4"})
    void bothFaceProductsUseExactOfficialProtocolAndTrustedBinding(String id) {
        product.setRemoteProductId(id);
        var account = authorize();
        assertFalse(account.lookup().toString().contains("private"));
        assertFalse(account.lookup().suggested().containsKey("schoolName"));
        assertFalse(account.heisha().preflightJson().contains("discard"));
        assertFalse(account.toString().contains("private-password"));
        var collected = gateway.collectFace(provider, product, account);
        var checked = gateway.checkFace(provider, product, collected);
        assertTrue(checked.heisha().status().completed());
        assertEquals(
                "https://collect.example/collect?c=private-link", checked.heisha().collectUrl());
        clearInvocations(http);
        var prepared = gateway.prepareAuthenticated(provider, product, form(plan()), checked);
        assertEquals("private-face-token", prepared.fields().get("face_token"));
        assertEquals("private-preflight-token", prepared.fields().get("run_preflight_token"));
        assertEquals("private-password", prepared.fields().get("password"));
        assertEquals("13800138000", prepared.fields().get("phone"));
        assertEquals("测试大学", prepared.fields().get("school_name"));
        assertEquals("p1", prepared.fields().get("plan_option_id"));
        assertEquals("f1", prepared.fields().get("fence_option_id"));
        assertEquals(id, prepared.fields().get("product_id"));
        assertEquals("[{\"raw\":\"07:00-10:00\"}]", prepared.fields().get("time_fragments"));
        assertFalse(prepared.toString().contains("private-face-token"));
        verifyNoInteractions(http); // No password replay, collection, or read during checkout.
    }

    @Test
    void collectionUsesSavedProviderCredentialsAndNeverAcceptsUserSuppliedToken() {
        var account = authorize();
        var collected = gateway.collectFace(provider, product, account);
        gateway.checkFace(provider, product, collected);
        verify(http)
                .postForString(
                        eq(provider),
                        eq("https://supplier.example/site/heisha/heisha.api.php?act=collect_link"),
                        argThat(
                                m ->
                                        m.size() == 3
                                                && "13800138000".equals(m.get("phone"))
                                                && "saved-uid".equals(m.get("login_uid"))
                                                && "saved-key".equals(m.get("login_key"))));
        verify(http)
                .postForString(
                        eq(provider),
                        eq("https://supplier.example/site/heisha/heisha.api.php?act=face_check"),
                        argThat(
                                m ->
                                        m.size() == 4
                                                && "13800138000".equals(m.get("phone"))
                                                && "private-face-token"
                                                        .equals(m.get("face_token"))));
        assertThrows(
                BusinessException.class, () -> gateway.collectFace(provider, product, collected));
        verify(http, times(1)).postForString(any(), contains("act=collect_link"), anyMap());
    }

    @Test
    void faceProductsCannotUseLegacyPasswordCheckoutOrUncheckedSnapshot() {
        var account = authorize();
        var fields = new HashMap<>(plan());
        fields.put("account", "13800138000");
        fields.put("password", "private-password");
        clearInvocations(http);
        assertThrows(
                BusinessException.class, () -> gateway.prepare(provider, product, form(fields)));
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAuthenticated(provider, product, form(plan()), account));
        verifyNoInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"face_token", "faceToken", "account", "password", "schoolName", "studentId"})
    void checkoutCannotOverrideAnyServerBoundIdentityOrToken(String key) {
        var checked =
                gateway.checkFace(
                        provider, product, gateway.collectFace(provider, product, authorize()));
        var fields = new HashMap<>(plan());
        fields.put(key, "attacker-controlled");
        clearInvocations(http);
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAuthenticated(provider, product, form(fields), checked));
        verifyNoInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"completed\":\"true\",\"fileCount\":2,\"minFileCount\":2,\"maxFileCount\":3}",
                "{\"completed\":1,\"fileCount\":2,\"minFileCount\":2,\"maxFileCount\":3}",
                "{\"completed\":true,\"fileCount\":1,\"minFileCount\":2,\"maxFileCount\":3}",
                "{\"completed\":true,\"fileCount\":4,\"minFileCount\":2,\"maxFileCount\":3}",
                "{\"completed\":false,\"fileCount\":0,\"minFileCount\":0,\"maxFileCount\":3}",
                "{\"completed\":false,\"fileCount\":0,\"minFileCount\":3,\"maxFileCount\":2}",
                "{\"completed\":true,\"fileCount\":2,\"minFileCount\":2}",
                "{}"
            })
    void malformedOrContradictoryPhotoReceiptsNeverAuthorizeCheckout(String status) {
        var account = gateway.collectFace(provider, product, authorize());
        when(http.postForString(any(), contains("act=face_check"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"batchStatus\":" + status + "}}");
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.checkFace(provider, product, account));
        verify(http, times(1)).postForString(any(), contains("act=face_check"), anyMap());
    }

    @Test
    void mismatchedIdentityOrTokenCannotReplaceAnAuthorization() {
        var account = authorize();
        when(http.postForString(any(), contains("act=collect_link"), anyMap()))
                .thenReturn(COLLECT.replace("13800138000", "13900139000"));
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.collectFace(provider, product, account));
        when(http.postForString(any(), contains("act=collect_link"), anyMap())).thenReturn(COLLECT);
        var collected = gateway.collectFace(provider, product, account);
        when(http.postForString(any(), contains("act=face_check"), anyMap()))
                .thenReturn(COMPLETE.replace("private-face-token", "foreign-token"));
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.checkFace(provider, product, collected));
        when(http.postForString(any(), contains("act=preflight"), anyMap()))
                .thenReturn(PREFLIGHT.replace("13800138000", "13900139000"));
        assertThrows(ProviderRequestException.class, this::authorize);
    }

    @Test
    void unknownPlansOutOfRangeDistanceAndWrongAuthModeFailClosed() {
        var account =
                gateway.checkFace(
                        provider, product, gateway.collectFace(provider, product, authorize()));
        var fields = new HashMap<>(plan());
        fields.put("planOptionId", "not-owned");
        assertThrows(
                BusinessException.class,
                () -> gateway.prepareAuthenticated(provider, product, form(fields), account));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.prepareAuthenticated(
                                provider,
                                product,
                                new OrderForm(10, new BigDecimal("4"), plan(), List.of(), true),
                                account));
        clearInvocations(http);
        assertThrows(
                BusinessException.class,
                () -> gateway.authenticate(provider, product, "SMS", "13800138000", "123456", ""));
        assertThrows(
                BusinessException.class,
                () ->
                        gateway.authenticate(
                                provider, product, "PASSWORD", "not-a-phone", "password", ""));
        verifyNoInteractions(http);
    }
}
