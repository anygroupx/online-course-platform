package com.course.platform.infra.docking.impl;

import com.course.platform.common.constant.Constants;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.ProviderOrderLog;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DaytimeDockingStrategyTest {

    private ApiHttpClient apiHttpClient;
    private DaytimeDockingStrategy strategy;
    private ApiProvider provider;
    private CoursePlatform platform;
    private CourseOrder order;

    @BeforeEach
    void setUp() {
        apiHttpClient = mock(ApiHttpClient.class);
        strategy = new DaytimeDockingStrategy(apiHttpClient);

        provider = new ApiProvider();
        provider.setApiUrl("https://daytime.example/");
        provider.setUsername("provider-uid");
        provider.setApiKey("provider-key");

        platform = new CoursePlatform();
        platform.setDockParam("platform-1");
        platform.setQueryParam("platform-1");

        order = new CourseOrder();
        order.setThirdOrderId("third-123");
        order.setStudentAccount("student-account");
        order.setStudentPassword("student-password");
        order.setSchoolName("测试大学");
        order.setCourseName("大学英语");
        order.setCourseId("course-1");
        order.setOrderStatus(Constants.ORDER_STATUS_PROCESSING);
    }

    @Test
    @DisplayName("下单成功时应保存响应根节点订单号")
    void dockOrder_shouldKeepThirdOrderId() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=add"), anyMap()))
                .thenReturn("{\"code\":0,\"id\":\"remote-987\"}");

        DockResult result = strategy.dockOrder(order, platform, provider);

        assertTrue(result.isSuccess());
        assertEquals("remote-987", result.getThirdOrderId());
    }

    @Test
    @DisplayName("下单未返回订单号时不应伪装为可用的成功结果")
    void dockOrder_shouldRejectMissingThirdOrderId() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=add"), anyMap()))
                .thenReturn("{\"code\":0,\"msg\":\"ok\"}");

        DockResult result = strategy.dockOrder(order, platform, provider);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未返回订单ID"));
    }

    @Test
    @DisplayName("进度查询应发送 Daytime 的 yid username school 参数")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void queryProgress_shouldUseDaytimeParameters() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"third-123\",\"status\":\"待处理\",\"process\":\"0%\"}]}");

        OrderProgressResult result = strategy.queryOrderProgress(order, platform, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertEquals("third-123", params.get("yid"));
        assertEquals("student-account", params.get("username"));
        assertEquals("测试大学", params.get("school"));
        assertFalse(params.containsKey("pass"));
        assertFalse(params.containsKey("kcname"));
        assertFalse(params.containsKey("user"));
        assertEquals(Constants.ORDER_STATUS_PENDING, result.getOrderStatus());
    }

    @Test
    @DisplayName("已退款应映射为等待退款状态")
    void queryProgress_shouldMapRefundPending() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"third-123\",\"status\":\"已退款\"}]}");

        OrderProgressResult result = strategy.queryOrderProgress(order, platform, provider);

        assertEquals(Constants.ORDER_STATUS_REFUND_PENDING, result.getOrderStatus());
        assertEquals("第三方状态：等待退款", result.getRemarks());
    }

    @Test
    @DisplayName("补刷应只使用第三方订单号 id")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void retryOrder_shouldUseRemoteId() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=budan"), anyMap()))
                .thenReturn("{\"code\":1,\"msg\":\"提交成功\"}");

        DockResult result = strategy.retryOrder(order, platform, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq(provider), eq("https://daytime.example/api.php?act=budan"), captor.capture());
        assertEquals("third-123", captor.getValue().get("id"));
        assertFalse(captor.getValue().containsKey("yid"));
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("商品列表查询应发送分类参数并归一化商品字段")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void fetchPlatformList_shouldUseCategoryAndNormalizeProducts() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=getclass"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"cid\":\"88\",\"name\":\"高等数学\",\"money\":\"12.50\",\"fenlei\":\"3\",\"fenleiname\":\"大学课程\"}]}");

        List<PlatformItem> items = strategy.fetchPlatformList(provider, "3");

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq(provider), eq("https://daytime.example/api.php?act=getclass"), captor.capture());
        assertEquals("provider-uid", captor.getValue().get("uid"));
        assertEquals("provider-key", captor.getValue().get("key"));
        assertEquals("3", captor.getValue().get("fenlei"));
        assertEquals(1, items.size());
        assertEquals("88", items.get(0).getId());
        assertEquals("高等数学", items.get(0).getName());
        assertEquals(new BigDecimal("12.50"), items.get(0).getPrice());
        assertEquals("大学课程", items.get(0).getCategoryName());
    }

    @Test
    @DisplayName("余额查询应调用 getmoney 并解析嵌套余额")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void queryBalance_shouldUseCredentialsAndParseBalance() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=getmoney"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":{\"balance\":\"1,234.56\"}}");

        BigDecimal balance = strategy.queryBalance(provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq(provider), eq("https://daytime.example/api.php?act=getmoney"), captor.capture());
        assertEquals("provider-uid", captor.getValue().get("uid"));
        assertEquals("provider-key", captor.getValue().get("key"));
        assertEquals(new BigDecimal("1234.56"), balance);
    }

    @Test
    @DisplayName("订单日志应使用第三方订单号并归一化日志字段")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void fetchOrderLogs_shouldUseRemoteOrderIdAndNormalizeLogs() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=getOrderLogs"), anyMap()))
                .thenReturn("{\"code\":1,\"logs\":[{\"log_id\":\"9\",\"action\":\"同步进度\",\"msg\":\"进度更新为50%\",\"state\":\"成功\",\"admin\":\"system\",\"addtime\":\"2026-09-05 10:00:00\"}]}");

        List<ProviderOrderLog> logs = strategy.fetchOrderLogs(order, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq(provider), eq("https://daytime.example/api.php?act=getOrderLogs"), captor.capture());
        assertEquals("third-123", captor.getValue().get("oid"));
        assertEquals(1, logs.size());
        ProviderOrderLog log = logs.get(0);
        assertEquals("9", log.getId());
        assertEquals("同步进度", log.getTitle());
        assertEquals("进度更新为50%", log.getContent());
        assertEquals("成功", log.getStatus());
        assertEquals("system", log.getOperator());
        assertEquals("2026-09-05 10:00:00", log.getCreateTime());
    }

    @Test
    @DisplayName("API地址已包含 api.php 时不应重复拼接路径")
    void queryBalance_shouldAcceptApiScriptBaseUrl() {
        provider.setApiUrl("https://daytime.example/api.php");
        when(apiHttpClient.postForString(eq(provider),
                eq("https://daytime.example/api.php?act=getmoney"), anyMap()))
                .thenReturn("{\"code\":1,\"money\":\"88.00\"}");

        assertEquals(new BigDecimal("88.00"), strategy.queryBalance(provider));
    }

}
