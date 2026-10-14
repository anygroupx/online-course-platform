package com.course.platform.infra.docking.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.ProviderOrderLog;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DaytimeDockingStrategyTest {

    private static final String ACCOUNT_A = "18229565354";
    private static final String ACCOUNT_B = "13800000000";

    private ApiHttpClient apiHttpClient;
    private CourseOrderMapper courseOrderMapper;
    private DaytimeDockingStrategy strategy;
    private ApiProvider provider;
    private CoursePlatform platform;
    private CourseOrder order;

    @BeforeEach
    void setUp() {
        // MyBatis-Plus 的 LambdaQueryWrapper 依赖 TableInfo 缓存，纯单元测试（无 Spring 容器）下
        // 必须手动初始化，否则 lambda 解析列名会抛 "can not find lambda cache"。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "daytime-batch-test"),
                CourseOrder.class);
        apiHttpClient = mock(ApiHttpClient.class);
        courseOrderMapper = mock(CourseOrderMapper.class);
        strategy = new DaytimeDockingStrategy(apiHttpClient);
        strategy.setCourseOrderMapper(courseOrderMapper);

        provider = new ApiProvider();
        provider.setId(7L);
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
    @DisplayName("进度查询应发送 Daytime 的 yid username 参数且不得携带 school")
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
        // 实测：username 与 school 同时传会被上游判为无结果并返回空数组。
        assertFalse(params.containsKey("school"));
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
    @DisplayName("订单日志应复用查单协议并归一化内嵌日志字段")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void fetchOrderLogs_shouldUseChadanAndNormalizeEmbeddedLogs() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"third-123\",\"logs\":[{\"log_id\":\"9\",\"action\":\"同步进度\",\"msg\":\"进度更新为50%\",\"state\":\"成功\",\"admin\":\"system\",\"addtime\":\"2026-09-05 10:00:00\"}]}]}");

        List<ProviderOrderLog> logs = strategy.fetchOrderLogs(order, provider);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient).postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), captor.capture());
        assertEquals("third-123", captor.getValue().get("yid"));
        assertEquals("student-account", captor.getValue().get("username"));
        assertFalse(captor.getValue().containsKey("school"));
        assertFalse(captor.getValue().containsKey("oid"));
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
    @DisplayName("查单响应没有历史日志时应返回当前执行快照")
    void fetchOrderLogs_shouldReturnCurrentSnapshotWhenHistoryIsAbsent() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"third-123\",\"status\":\"进行中\",\"process\":\"50%\",\"remarks\":\"正在学习\",\"update_time\":\"2026-09-11 18:30:00\"}]}");

        List<ProviderOrderLog> logs = strategy.fetchOrderLogs(order, provider);

        assertEquals(1, logs.size());
        ProviderOrderLog log = logs.get(0);
        assertEquals("third-123", log.getId());
        assertEquals("当前执行状态", log.getTitle());
        assertEquals("当前进度：50%；备注：正在学习", log.getContent());
        assertEquals("进行中", log.getStatus());
        assertEquals("2026-09-11 18:30:00", log.getCreateTime());
    }

    @Test
    @DisplayName("查单响应没有匹配订单时不得展示其他订单")
    void fetchOrderLogs_shouldRejectNonMatchingOrder() {
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"another-order\",\"status\":\"已完成\"}]}");

        BusinessException error = assertThrows(BusinessException.class,
                () -> strategy.fetchOrderLogs(order, provider));

        assertTrue(error.getMessage().contains("没有匹配订单数据"));
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

    // ==================== 批量查询订单进度 ====================

    @Test
    @DisplayName("批量查单应按账号聚合，一次调用返回该账号全部订单")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void batchQuery_shouldAggregateAllOrdersOfEachAccount() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                localOrder("remote-1", ACCOUNT_A, "pw-a", "大学英语"),
                localOrder("remote-2", ACCOUNT_A, "pw-a", "大学英语"),
                localOrder("remote-3", ACCOUNT_B, "pw-b", "形势与政策")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":["
                        + "{\"id\":\"remote-1\",\"status\":\"进行中\",\"process\":\"16.47%\",\"remarks\":\"学习中\","
                        + "\"kcname\":\"大学英语\",\"kcks\":\"2026-09-01 08:00:00\",\"ksjs\":\"2026-09-09 18:00:00\"},"
                        + "{\"id\":\"remote-2\",\"status\":\"已完成\",\"process\":\"100%\"}]}")
                .thenReturn("{\"code\":1,\"data\":[]}");

        List<OrderProgressResult> results = strategy.batchQueryOrderProgress(provider, 1000L, 0);

        // 两个账号各查一次，且全程串行（无并发）。
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiHttpClient, times(2)).postForString(
                eq(provider), eq("https://daytime.example/api.php?act=chadan"), captor.capture());
        List<Map> requests = captor.getAllValues();
        assertEquals(ACCOUNT_A, requests.get(0).get("username"));
        assertEquals(ACCOUNT_B, requests.get(1).get("username"));
        for (Map request : requests) {
            // 批量查询只传 uid + key + username。
            assertEquals(3, request.size());
            assertEquals("provider-uid", request.get("uid"));
            assertEquals("provider-key", request.get("key"));
            assertFalse(request.containsKey("school"));
            assertFalse(request.containsKey("yid"));
        }

        // A 账号返回 2 条，B 账号空数组 —— 空数组不产生条目也不报错。
        assertEquals(2, results.size());
        OrderProgressResult first = results.get(0);
        assertEquals("remote-1", first.getThirdOrderId());
        assertEquals("16.47%", first.getProgress());
        assertEquals("学习中", first.getRemarks());
        assertEquals(Constants.ORDER_STATUS_PROCESSING, first.getOrderStatus());
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0), first.getCourseStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 9, 18, 0), first.getExamEndTime());

        OrderProgressResult second = results.get(1);
        assertEquals("remote-2", second.getThirdOrderId());
        assertEquals(Constants.ORDER_STATUS_COMPLETED, second.getOrderStatus());
        // 没有 remarks 时回填第三方状态文案，与单笔查询保持一致。
        assertEquals("第三方状态：已完成", second.getRemarks());
        assertNull(second.getCourseStartTime());
    }

    @Test
    @DisplayName("批量结果必须回填本地身份字段，否则 UPDATE 五字段匹配零行命中")
    void batchQuery_shouldCarryLocalIdentityFieldsForFullMatch() {
        // 上游响应不含密码，course_name 也是供应商侧的 kcname（与本地不同）。
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                localOrder("remote-1", ACCOUNT_A, "pw-local-a", "本地课程名")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":["
                        + "{\"id\":\"remote-1\",\"status\":\"进行中\",\"process\":\"33%\",\"kcname\":\"供应商课程名\"}]}");

        List<OrderProgressResult> results = strategy.batchQueryOrderProgress(provider, 1000L, 0);

        assertEquals(1, results.size());
        OrderProgressResult result = results.get(0);
        // updateOrderProgressByFullMatch 以这五个字段精确匹配；缺失/取错值都会零行更新。
        assertEquals(ACCOUNT_A, result.getStudentAccount());
        assertEquals("pw-local-a", result.getStudentPassword());
        assertEquals("本地课程名", result.getCourseName());
        assertEquals("remote-1", result.getThirdOrderId());
        assertEquals("33%", result.getProgress());
    }

    @Test
    @DisplayName("上游返回本地未录入的订单时应跳过，不猜身份字段")
    void batchQuery_shouldSkipUnknownUpstreamOrder() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                localOrder("remote-known", ACCOUNT_A, "pw-a", "课程甲")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":["
                        + "{\"id\":\"remote-known\",\"status\":\"进行中\",\"process\":\"10%\"},"
                        + "{\"id\":\"remote-unknown\",\"status\":\"进行中\",\"process\":\"20%\"}]}");

        List<OrderProgressResult> results = strategy.batchQueryOrderProgress(provider, 1000L, 0);

        assertEquals(1, results.size());
        assertEquals("remote-known", results.get(0).getThirdOrderId());
    }

    @Test
    @DisplayName("上游返回空数组属正常情况，不抛异常")
    void batchQuery_shouldTreatEmptyArrayAsNoUpdates() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(localOrder("remote-1", ACCOUNT_A, "pw-a", "课程甲")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");

        assertTrue(strategy.batchQueryOrderProgress(provider, 1000L, 0).isEmpty());
    }

    @Test
    @DisplayName("单个账号失败被隔离，其余账号继续同步")
    void batchQuery_shouldIsolateSingleAccountFailure() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                localOrder("r-1", ACCOUNT_A, "pw-a", "课程甲"),
                localOrder("r-2", ACCOUNT_B, "pw-b", "课程乙"),
                localOrder("r-3", "13900000000", "pw-c", "课程丙")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT))
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"r-2\",\"status\":\"进行中\",\"process\":\"50%\"}]}")
                .thenThrow(new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED));

        List<OrderProgressResult> results = strategy.batchQueryOrderProgress(provider, 1000L, 0);

        assertEquals(1, results.size());
        assertEquals("r-2", results.get(0).getThirdOrderId());
        // 失败的账号被跳过而不是终止整批：三个账号都发起了请求。
        verify(apiHttpClient, times(3)).postForString(any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("上游拒绝整批时账号级失败仍被隔离")
    void batchQuery_shouldIsolateRejectedResponsePerAccount() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                localOrder("remote-1", ACCOUNT_A, "pw-a", "课程甲"),
                localOrder("remote-2", ACCOUNT_B, "pw-b", "课程乙")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":-1,\"msg\":\"至少需要一个有效参数\"}")
                .thenReturn("{\"code\":1,\"data\":[{\"id\":\"remote-2\",\"status\":\"待处理\"}]}");

        List<OrderProgressResult> results = strategy.batchQueryOrderProgress(provider, 1000L, 0);

        assertEquals(1, results.size());
        assertEquals("remote-2", results.get(0).getThirdOrderId());
        assertEquals(Constants.ORDER_STATUS_PENDING, results.get(0).getOrderStatus());
    }

    @Test
    @DisplayName("没有待同步订单时不应请求上游")
    void batchQuery_shouldSkipUpstreamWhenNoLocalAccount() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertTrue(strategy.batchQueryOrderProgress(provider, 1000L, 0).isEmpty());
        verify(apiHttpClient, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("批量查单不得更新同步水位")
    void batchQuery_shouldNotAdvanceWatermark() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(localOrder("remote-1", ACCOUNT_A, "pw-a", "课程甲")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");

        strategy.batchQueryOrderProgress(provider, 1000L, 0);

        assertNull(provider.getLastSyncTime());
    }

    @Test
    @DisplayName("批量查单只允许只读查询，不得触发任何下单/补刷动作")
    void batchQuery_shouldOnlyUseReadOnlyAction() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(localOrder("remote-1", ACCOUNT_A, "pw-a", "课程甲")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");

        strategy.batchQueryOrderProgress(provider, 1000L, 0);

        verify(apiHttpClient, times(1)).postForString(any(), anyString(), anyMap());
        verify(apiHttpClient, never()).postForString(any(), eq("https://daytime.example/api.php?act=add"), anyMap());
        verify(apiHttpClient, never()).postForString(any(), eq("https://daytime.example/api.php?act=budan"), anyMap());
    }

    @Test
    @DisplayName("未保存的接口配置无法批量查单")
    void batchQuery_shouldRequirePersistedProvider() {
        provider.setId(null);

        assertThrows(BusinessException.class,
                () -> strategy.batchQueryOrderProgress(provider, 1000L, 0));
        verify(apiHttpClient, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("缺少订单查询依赖时应显式失败而不是静默返回空")
    void batchQuery_shouldFailFastWithoutMapper() {
        DaytimeDockingStrategy withoutMapper = new DaytimeDockingStrategy(apiHttpClient);

        assertThrows(BusinessException.class,
                () -> withoutMapper.batchQueryOrderProgress(provider, 1000L, 0));
        verify(apiHttpClient, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("账号数超过单批窗口时不得漏单：不做与上层 offset 无法对齐的 LIMIT 分页")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void batchQuery_shouldNotPageWithIncompatibleLimit() {
        // 上层 batchSyncOrderProgress 以 10000 步进 offset、并以"返回条数 < 500"结束循环。
        // 若本策略对账号做 LIMIT 分页，offset=10000 会直接跳过全部账号 -> 静默漏单。
        List<CourseOrder> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(localOrder("r-" + i, "1380000" + String.format("%04d", i), "pw-" + i, "课程" + i));
        }
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(many);
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");

        // 即便上层传入 10000 的 offset，也必须覆盖全部 25 个账号。
        strategy.batchQueryOrderProgress(provider, 1000L, 10000);

        verify(apiHttpClient, times(25)).postForString(any(), anyString(), anyMap());

        ArgumentCaptor<LambdaQueryWrapper<CourseOrder>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseOrderMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertFalse(sql.contains("LIMIT"), () -> "账号级 LIMIT 会与上层 offset 步进错位: " + sql);
    }

    @Test
    @DisplayName("批量查单按 providerId 过滤本地订单，并排除终态订单")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void batchQuery_shouldFilterLocalOrdersByProviderOnly() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(localOrder("remote-1", ACCOUNT_A, "pw-a", "课程甲")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");

        strategy.batchQueryOrderProgress(provider, 1000L, 0);

        ArgumentCaptor<LambdaQueryWrapper<CourseOrder>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseOrderMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("api_provider_id"), () -> "unexpected sql: " + sql);
        assertTrue(sql.contains("student_account"), () -> "unexpected sql: " + sql);
        assertTrue(sql.contains("ORDER BY"), () -> "unexpected sql: " + sql);
        // 终态订单必须排除，否则已完成订单会被每轮定时任务反复重查重写。
        assertTrue(sql.contains("order_status"), () -> "unexpected sql: " + sql);
    }

    @Test
    @DisplayName("批量查单必须排除全部终态订单状态，否则已完成订单会一直显示在同步")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void batchQuery_shouldExcludeEveryTerminalStatus() {
        when(courseOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(localOrder("remote-1", ACCOUNT_A, "pw-a", "课程甲")));
        when(apiHttpClient.postForString(eq(provider), eq("https://daytime.example/api.php?act=chadan"), anyMap()))
                .thenReturn("{\"code\":1,\"data\":[]}");

        strategy.batchQueryOrderProgress(provider, 1000L, 0);

        ArgumentCaptor<LambdaQueryWrapper<CourseOrder>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseOrderMapper).selectList(captor.capture());
        LambdaQueryWrapper<CourseOrder> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("NOT IN"), () -> "终态订单未排除: " + sql);

        // 五个终态都必须出现在 NOT IN 的绑定参数里（已完成/已取消/失败/考试完成/等待退款）。
        Collection<Object> boundValues = wrapper.getParamNameValuePairs().values();
        for (Integer terminal : Constants.TERMINAL_ORDER_STATUSES) {
            assertTrue(boundValues.contains(terminal),
                    () -> "终态状态 " + terminal + " 未出现在 NOT IN 绑定参数中: " + boundValues);
        }
    }

    private CourseOrder localOrder(String thirdOrderId, String account, String password, String courseName) {
        CourseOrder row = new CourseOrder();
        row.setThirdOrderId(thirdOrderId);
        row.setStudentAccount(account);
        row.setStudentPassword(password);
        row.setCourseName(courseName);
        row.setOrderStatus(Constants.ORDER_STATUS_PROCESSING);
        return row;
    }

}
