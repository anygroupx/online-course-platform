package com.course.platform.infra.docking.impl;

import com.course.platform.common.constant.Constants;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(apiHttpClient.postForString(eq("https://daytime.example/api.php?act=add"), anyMap()))
                .thenReturn("{\"code\":0,\"id\":\"remote-987\"}");

        DockResult result = strategy.dockOrder(order, platform, provider);

        assertTrue(result.isSuccess());
        assertEquals("remote-987", result.getThirdOrderId());
    }

    @Test
    @DisplayName("下单未返回订单号时不应伪装为可用的成功结果")
    void dockOrder_shouldRejectMissingThirdOrderId() {
        when(apiHttpClient.postForString(eq("https://daytime.example/api.php?act=add"), anyMap()))
                .thenReturn("{\"code\":0,\"msg\":\"ok\"}");

        DockResult result = strategy.dockOrder(order, platform, provider);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未返回订单ID"));
    }

    @Test
    @DisplayName("进度查询应发送 Daytime 的 yid username school 参数")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void queryProgress_shouldUseDaytimeParameters() {
        when(apiHttpClient.postForString(eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"third-123\",\"status\":\"待处理\",\"process\":\"0%\"}]}");

        OrderProgressResult result = strategy.queryOrderProgress(order, platform, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq("https://daytime.example/api.php?act=chadan"), captor.capture());
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
        when(apiHttpClient.postForString(eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"third-123\",\"status\":\"已退款\"}]}");

        OrderProgressResult result = strategy.queryOrderProgress(order, platform, provider);

        assertEquals(Constants.ORDER_STATUS_REFUND_PENDING, result.getOrderStatus());
        assertEquals("第三方状态：等待退款", result.getRemarks());
    }

    @Test
    @DisplayName("补刷应只使用第三方订单号 id")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void retryOrder_shouldUseRemoteId() {
        when(apiHttpClient.postForString(eq("https://daytime.example/api.php?act=budan"), anyMap()))
                .thenReturn("{\"code\":1,\"msg\":\"提交成功\"}");

        DockResult result = strategy.retryOrder(order, platform, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq("https://daytime.example/api.php?act=budan"), captor.capture());
        assertEquals("third-123", captor.getValue().get("id"));
        assertFalse(captor.getValue().containsKey("yid"));
        assertTrue(result.isSuccess());
    }
}
