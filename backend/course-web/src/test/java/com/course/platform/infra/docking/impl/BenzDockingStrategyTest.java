package com.course.platform.infra.docking.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.ApiHttpClient;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

class BenzDockingStrategyTest {
    ApiHttpClient http;
    BenzDockingStrategy strategy;
    ApiProvider provider;
    static final String ROW =
            "{\"id\":\"remote-order-9\",\"cid\":\"product-2\",\"user\":\"student-account\",\"pass\":\"private-password\",\"kcname\":\"示例课程\",\"status\":\"已完成\",\"process\":\"100%\",\"remarks\":\"完成\",\"kcks\":\"2026-09-01"
                + " 08:00:00\",\"kcjs\":\"2026-09-30 18:00:00\",\"ksks\":\"2026-09-08"
                + " 08:00:00\",\"ksjs\":\"2026-09-09 18:00:00\"}";

    @BeforeEach
    void setup() {
        http = mock(ApiHttpClient.class);
        strategy = new BenzDockingStrategy(http);
        provider = new ApiProvider();
        provider.setProviderType("27");
        provider.setApiUrl("https://supplier.example/site");
        provider.setUsername("saved-uid");
        provider.setApiKey("saved-key");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void batchUsesEvidencedActionOffsetTimestampAndKeepsRemoteOrderIdentityDistinctFromProduct(
            int code) {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":" + code + ",\"data\":[" + ROW + "]}");
        var rows = strategy.batchQueryOrderProgress(provider, 1000L, 10000);
        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals("remote-order-9", row.getThirdOrderId());
        assertEquals("student-account", row.getStudentAccount());
        assertEquals("private-password", row.getStudentPassword());
        assertEquals("100%", row.getProgress());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 1, 8, 0), row.getCourseStartTime());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 9, 18, 0), row.getExamEndTime());
        assertFalse(row.toString().contains("private-password"));
        verify(http, times(1))
                .postForString(
                        eq(provider),
                        eq("https://supplier.example/site/api.php?act=plchadan"),
                        argThat(
                                m ->
                                        m.size() == 4
                                                && "saved-uid".equals(m.get("uid"))
                                                && "saved-key".equals(m.get("key"))
                                                && Long.valueOf(1000).equals(m.get("timestamp"))
                                                && Integer.valueOf(10000).equals(m.get("offset"))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"code\":-1,\"data\":[]}",
                "{\"code\":2,\"data\":[]}",
                "{\"code\":1}",
                "{\"code\":1,\"data\":null}",
                "{\"code\":1,\"data\":{}}",
                "{\"code\":1,\"data\":[null]}",
                "{\"code\":1,\"data\":[\"private-password\"]}",
                "{\"code\":1,\"data\":[{}]}",
                "{\"code\":1,\"data\":[{\"user\":\"private-password\"}]}"
            })
    void rejectedAndMalformedPagesAreNeverSilentlyConvertedToEmptySuccess(String body) {
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(body);
        var e =
                assertThrows(
                        ProviderRequestException.class,
                        () -> strategy.batchQueryOrderProgress(provider, 1L, 0));
        assertNull(e.getCause());
        assertFalse(e.getMessage().contains("private-password"));
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @Test
    void oneMalformedRowRejectsTheEntirePageRatherThanSkippingIt() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[" + ROW + ",{}]}");
        assertThrows(
                ProviderRequestException.class,
                () -> strategy.batchQueryOrderProgress(provider, 1L, 0));
    }

    @Test
    void onlyExplicitSuccessfulEmptyArrayMeansThereAreNoUpdates() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");
        assertTrue(strategy.batchQueryOrderProgress(provider, null, null).isEmpty());
        verify(http)
                .postForString(
                        any(),
                        anyString(),
                        argThat(
                                m ->
                                        !m.containsKey("timestamp")
                                                && Integer.valueOf(0).equals(m.get("offset"))));
    }

    @Test
    void oversizedBatchPageFailsRatherThanConsumingUnboundedUpdates() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":1,\"data\":["
                                + String.join(",", java.util.Collections.nCopies(10001, ROW))
                                + "]}");
        assertThrows(
                ProviderRequestException.class,
                () -> strategy.batchQueryOrderProgress(provider, 1L, 0));
    }

    @Test
    void catalogMapsPriceCategoryAndDescriptionFromTheOriginalImportContract() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"code\":1,\"data\":[{\"cid\":\"c1\",\"name\":\"示例商品\",\"price\":\"0.1234\",\"fenlei\":\"category-1\",\"category_name\":\"示例分类\",\"content\":\"商品说明\"}]}");
        var item = strategy.fetchPlatformList(provider).get(0);
        assertEquals("c1", item.getId());
        assertEquals(new BigDecimal("0.1234"), item.getPrice());
        assertEquals("category-1", item.getCategoryId());
        assertEquals("示例分类", item.getCategoryName());
        assertEquals("商品说明", item.getContent());
        verify(http)
                .postForString(
                        eq(provider),
                        eq("https://supplier.example/site/api.php?act=getclass"),
                        argThat(
                                m ->
                                        m.size() == 2
                                                && m.containsKey("uid")
                                                && m.containsKey("key")));
    }
}
