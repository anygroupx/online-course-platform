package com.course.platform.infra.docking.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.ProviderOrderLog;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.docking.DockingLogSanitizer;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.docking.ProviderResponseParser;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.domain.exception.ProviderRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daytime（29 系统）独立对接策略。
 */
@Slf4j
@Component
public class DaytimeDockingStrategy implements PlatformDockingStrategy {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 单次 {@code chadan} 响应允许解析的最大订单数。
     * 上游按 username 聚合返回全部订单，异常膨胀的响应体不得被当作有效进度写入。
     */
    private static final int MAX_ITEMS_PER_ACCOUNT = 500;

    protected final ApiHttpClient apiHttpClient;
    private final ProviderUrlNormalizer urlNormalizer = new ProviderUrlNormalizer();

    /**
     * 批量同步需要自行定位"待同步的本地订单"。
     *
     * <p>用 setter 注入而非构造器注入：{@link TwentyNineDockingStrategy}（"29" 同系统兼容类型）
     * 通过 {@code super(apiHttpClient)} 复用本类，若走构造器注入则该子类拿不到 mapper。
     * 字段/方法级注入由 Spring 在子类 bean 上一并处理，因此两种 provider 类型都能拿到。</p>
     *
     * <p>{@code required = false} 只为兼容 {@code BdkjDockingStrategy} 里
     * {@code new TwentyNineDockingStrategy(apiHttpClient)} 这种手工实例化（其只调用查课/下单）。</p>
     */
    private CourseOrderMapper courseOrderMapper;

    public DaytimeDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Autowired(required = false)
    void setCourseOrderMapper(CourseOrderMapper courseOrderMapper) {
        this.courseOrderMapper = courseOrderMapper;
    }

    @Override
    public String getProviderType() {
        return "Daytime";
    }

    @Override
    public void testConnection(ApiProvider apiProvider) {
        queryBalance(apiProvider);
    }

    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform,
                                                              QueryCourseRequest request,
                                                              ApiProvider apiProvider) {
        Map<String, Object> params = authParams(apiProvider);
        params.put("platform", platform.getQueryParam());
        params.put("school", request.getSchoolName());
        params.put("user", request.getStudentAccount());
        params.put("pass", request.getStudentPassword());

        String response = post(apiProvider, "get", "查课", params);
        JSONObject json = parseResponse(response, "查课");
        if (!isCode(json, 0) && !isCode(json, 1)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }

        List<CourseInfoResponse.CourseItem> courses = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");
        if (data == null) {
            return courses;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            CourseInfoResponse.CourseItem course = new CourseInfoResponse.CourseItem();
            course.setId(firstNonBlank(item.getStr("id"), item.getStr("kcid")));
            course.setName(firstNonBlank(item.getStr("name"), item.getStr("kcname")));
            courses.add(course);
        }
        return courses;
    }

    @Override
    public DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        Map<String, Object> params = authParams(apiProvider);
        params.put("platform", platform.getDockParam());
        params.put("school", order.getSchoolName());
        params.put("user", order.getStudentAccount());
        params.put("pass", order.getStudentPassword());
        params.put("kcname", order.getCourseName());
        params.put("kcid", order.getCourseId());

        JSONObject json = parseResponse(post(apiProvider, "add", "下单", params), "下单");
        if (!isCode(json, 0)) {
            return DockResult.fail(ProviderRequestException.PUBLIC_MESSAGE);
        }

        String thirdOrderId = extractThirdOrderId(json);
        if (StrUtil.isBlank(thirdOrderId)) {
            return DockResult.fail("下单成功但第三方未返回订单ID，已阻止保存无效对接结果");
        }
        return DockResult.success("下单成功", thirdOrderId);
    }

    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order,
                                                   CoursePlatform platform,
                                                   ApiProvider apiProvider) {
        String thirdOrderId = requireThirdOrderId(order, "查询进度");
        JSONObject item = queryOrderItem(order, apiProvider, thirdOrderId, "进度查询");

        String remoteStatus = firstNonBlank(item.getStr("status"), item.getStr("status_text"));
        Integer localStatus = mapOrderStatus(remoteStatus, order.getOrderStatus());
        String remarks = item.getStr("remarks");
        if (StrUtil.isBlank(remarks) && StrUtil.isNotBlank(remoteStatus)) {
            remarks = "第三方状态：" + normalizeDisplayStatus(remoteStatus);
        }

        return OrderProgressResult.builder()
                .progress(item.getStr("process"))
                .orderStatus(localStatus)
                .remarks(remarks)
                .courseStartTime(parseTime(firstNonBlank(item.getStr("courseStartTime"), item.getStr("kcks"))))
                .courseEndTime(parseTime(firstNonBlank(item.getStr("courseEndTime"), item.getStr("kcjs"))))
                .examStartTime(parseTime(firstNonBlank(item.getStr("examStartTime"), item.getStr("ksks"))))
                .examEndTime(parseTime(firstNonBlank(item.getStr("examEndTime"), item.getStr("ksjs"))))
                .thirdOrderId(firstNonBlank(item.getStr("id"), item.getStr("yid"), thirdOrderId))
                .build();
    }

    @Override
    public DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        String thirdOrderId;
        try {
            thirdOrderId = requireThirdOrderId(order, "补刷");
        } catch (BusinessException e) {
            return DockResult.fail(e.getMessage());
        }

        Map<String, Object> params = authParams(apiProvider);
        params.put("id", thirdOrderId);

        JSONObject json = parseResponse(post(apiProvider, "budan", "补刷", params), "补刷");
        Integer code = json.getInt("code");
        if (Integer.valueOf(1).equals(code) || Integer.valueOf(0).equals(code)) {
            return DockResult.success("补刷提交成功", thirdOrderId);
        }
        return DockResult.fail(ProviderRequestException.PUBLIC_MESSAGE);
    }

    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        return fetchPlatformList(apiProvider, null);
    }

    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider, String categoryId) {
        Map<String, Object> params = authParams(apiProvider);
        if (StrUtil.isNotBlank(categoryId)) {
            params.put("fenlei", categoryId);
        }
        JSONObject json = parseResponse(post(apiProvider, "getclass", "商品列表", params), "商品列表");
        if (!isCode(json, 0) && !isCode(json, 1)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }

        List<PlatformItem> items = new ArrayList<>();
        JSONArray data = responseArray(json, "data", "list", "class");
        if (data == null) {
            return items;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            String id = firstNonBlank(item.getStr("cid"), item.getStr("id"), item.getStr("kcid"));
            String name = firstNonBlank(item.getStr("name"), item.getStr("kcname"), item.getStr("title"));
            if (StrUtil.isBlank(id) || StrUtil.isBlank(name)) {
                continue;
            }
            items.add(PlatformItem.builder()
                    .id(id)
                    .name(name)
                    .price(firstBigDecimal(item, null, "price", "money", "cost"))
                    .categoryId(firstNonBlank(item.getStr("fenlei"), item.getStr("category_id")))
                    .categoryName(firstNonBlank(item.getStr("category_name"), item.getStr("fenleiname"), item.getStr("fenlei_name")))
                    .type(getProviderType())
                    .content(firstNonBlank(item.getStr("content"), item.getStr("description"), item.getStr("desc")))
                    .build());
        }
        return items;
    }

    @Override
    public BigDecimal queryBalance(ApiProvider apiProvider) {
        JSONObject json = parseResponse(post(apiProvider, "getmoney", "余额查询", authParams(apiProvider)), "余额查询");
        if (!isCode(json, 0) && !isCode(json, 1)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }

        BigDecimal balance = firstBigDecimal(json, null, "money", "balance", "amount", "je");
        Object data = json.get("data");
        if (balance == null && data instanceof JSONObject dataObject) {
            balance = firstBigDecimal(dataObject, null, "money", "balance", "amount", "je");
        } else if (balance == null && data != null) {
            balance = toBigDecimal(data);
        }
        if (balance == null) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
        return balance;
    }

    @Override
    public List<ProviderOrderLog> fetchOrderLogs(CourseOrder order, ApiProvider apiProvider) {
        String thirdOrderId = requireThirdOrderId(order, "查询订单日志");
        // Daytime/29 does not expose the guessed getOrderLogs action used by the original
        // adapter. Reuse its verified read-only chadan contract and present either embedded
        // logs or a current execution snapshot instead of repeatedly calling a rejected API.
        JSONObject item = queryOrderItem(order, apiProvider, thirdOrderId, "订单日志");
        JSONArray data = responseArray(item, "logs", "log_list", "records");
        if (data != null) {
            return normalizeOrderLogs(data);
        }

        String progress = item.getStr("process");
        String remarks = item.getStr("remarks");
        String content = snapshotContent(progress, remarks);
        return List.of(ProviderOrderLog.builder()
                .id(firstNonBlank(item.getStr("id"), item.getStr("yid"), thirdOrderId))
                .title("当前执行状态")
                .content(content)
                .status(normalizeDisplayStatus(firstNonBlank(item.getStr("status"), item.getStr("status_text"))))
                .operator(firstNonBlank(item.getStr("operator"), item.getStr("admin")))
                .createTime(firstNonBlank(item.getStr("update_time"), item.getStr("updateTime"),
                        item.getStr("addtime"), item.getStr("time")))
                .build());
    }

    /**
     * 批量查询订单进度。
     *
     * <p>上游 {@code chadan} 按 {@code username} 聚合返回该账号名下<b>全部</b>订单，
     * 因此这里按学生账号分组，一次调用处理一批账号（而不是逐单查 {@code yid}，
     * 实测 {@code yid} 查询偶发 20s 超时）。</p>
     *
     * <p>账号之间串行查询；单个账号失败只记录日志并跳过，不影响整批。
     * 本方法不推进 {@code api_provider.last_sync_time}，水位由
     * {@code PlatformDockingServiceImpl.batchSyncOrderProgress} 统一管理。</p>
     */
    @Override
    public List<OrderProgressResult> batchQueryOrderProgress(ApiProvider apiProvider,
                                                             Long timestampSeconds,
                                                             Integer offset) {
        if (apiProvider == null || apiProvider.getId() == null) {
            throw new BusinessException("批量同步订单进度需要已保存的接口配置");
        }
        if (courseOrderMapper == null) {
            throw new BusinessException("批量同步订单进度缺少订单查询依赖");
        }

        List<CourseOrder> localOrders = loadSyncableOrders(apiProvider.getId());
        if (localOrders.isEmpty()) {
            log.info("{}批量查单: providerId={}, 没有待同步的本地订单",
                    getProviderType(), apiProvider.getId());
            return List.of();
        }

        // 按学生账号分组：上游 chadan 按 username 聚合返回该账号名下全部订单，
        // 一个账号只需一次请求（实测 yid 逐单查询偶发 20s 超时）。
        Map<String, List<CourseOrder>> byAccount = new LinkedHashMap<>();
        for (CourseOrder order : localOrders) {
            byAccount.computeIfAbsent(order.getStudentAccount(), key -> new ArrayList<>()).add(order);
        }

        List<OrderProgressResult> results = new ArrayList<>();
        for (Map.Entry<String, List<CourseOrder>> entry : byAccount.entrySet()) {
            try {
                results.addAll(queryAccountProgress(apiProvider, entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                // 单账号失败（超时/上游错误/响应异常）必须隔离，不能让整批同步失败。
                // 不打印异常详情：上游报文与解析异常可能夹带账号、密码等凭据。
                log.warn("{}批量查单: 账号查询失败，已跳过。providerId={}, errorType={}",
                        getProviderType(), apiProvider.getId(), e.getClass().getSimpleName());
            }
        }

        log.info("{}批量查单: providerId={}, 账号数={}, 订单数={}",
                getProviderType(), apiProvider.getId(), byAccount.size(), results.size());
        return results;
    }

    /**
     * 加载该 provider 下待同步的本地订单。
     *
     * <p>必须带出 {@code student_password} 与 {@code course_name}：上层
     * {@code updateOrderProgressByFullMatch} 以
     * {@code student_account + student_password + course_name + third_order_id + api_provider_id}
     * 五字段精确匹配。这两个值只存在于本地，上游 {@code chadan} 不返回
     * （响应里的 {@code kcname} 是供应商侧课程名，不等于本地 {@code course_name}）。
     * 用上游值或 null 去匹配会导致 {@code student_password = NULL} 恒假 —— 零行更新。</p>
     *
     * <p>不做 LIMIT 分页：上层以 10000 步进 offset，并依赖"返回条数 &lt; 500 即结束"终止循环；
     * 账号级窗口与该步进无法对齐，一旦账号数超过单批上限，后续账号会被静默跳过。</p>
     */
    private List<CourseOrder> loadSyncableOrders(Long apiProviderId) {
        return courseOrderMapper.selectList(
                new LambdaQueryWrapper<CourseOrder>()
                        .select(CourseOrder::getId,
                                CourseOrder::getThirdOrderId,
                                CourseOrder::getStudentAccount,
                                CourseOrder::getStudentPassword,
                                CourseOrder::getCourseName,
                                CourseOrder::getOrderStatus)
                        .eq(CourseOrder::getApiProviderId, apiProviderId)
                        .isNotNull(CourseOrder::getStudentAccount)
                        .orderByAsc(CourseOrder::getStudentAccount)
                        .orderByAsc(CourseOrder::getId));
    }

    /** 查询单个账号名下的全部订单进度；空数组是正常情况，返回空列表。 */
    private List<OrderProgressResult> queryAccountProgress(ApiProvider apiProvider,
                                                           String studentAccount,
                                                           List<CourseOrder> localOrders) {
        // 实测：username 与 school 同时传会返回空数组，批量查询只传 uid + key + username。
        Map<String, Object> params = authParams(apiProvider);
        params.put("username", studentAccount);

        JSONObject json = parseResponse(post(apiProvider, "chadan", "批量查单", params), "批量查单");
        if (!isCode(json, 1)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }

        Object data = json.get("data");
        if (data == null || data instanceof JSONArray array && array.isEmpty()) {
            // 不存在的账号 / 无订单 -> 空数组，属正常情况。
            return List.of();
        }
        JSONArray items = requireProgressArray(data);

        // 本地订单按 third_order_id 建索引：身份字段（密码/课程名）只能取本地值，
        // 上游响应不提供，用上游 kcname 或 null 去匹配会让 UPDATE 零行命中。
        Map<String, CourseOrder> byThirdOrderId = new LinkedHashMap<>();
        for (CourseOrder order : localOrders) {
            if (order != null && StrUtil.isNotBlank(order.getThirdOrderId())) {
                byThirdOrderId.putIfAbsent(order.getThirdOrderId(), order);
            }
        }

        List<OrderProgressResult> results = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
            }
            OrderProgressResult result = toBatchProgressResult(item, byThirdOrderId);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * 解析批量响应条目。
     *
     * <p>进度/状态/时间字段取自上游；但 {@code studentPassword} 与 {@code courseName}
     * 必须回填<b>本地订单</b>的值——它们是上层 {@code updateOrderProgressByFullMatch}
     * 的匹配键，上游不返回，缺失会导致更新零行命中。</p>
     *
     * <p>上游返回但本地没有对应订单的条目直接跳过：宁可不同步，也不猜身份。</p>
     */
    private OrderProgressResult toBatchProgressResult(JSONObject item,
                                                      Map<String, CourseOrder> localByThirdOrderId) {
        String thirdOrderId = firstNonBlank(item.getStr("id"), item.getStr("yid"));
        if (StrUtil.isBlank(thirdOrderId)) {
            // 没有第三方订单号的条目无法匹配本地订单，跳过而不是伪造身份。
            return null;
        }

        CourseOrder localOrder = localByThirdOrderId.get(thirdOrderId);
        if (localOrder == null) {
            // 上游多返回的订单（本地未录入）：没有可信的密码/课程名，不能落库。
            return null;
        }

        String remoteStatus = firstNonBlank(item.getStr("status"), item.getStr("status_text"));
        String remarks = item.getStr("remarks");
        if (StrUtil.isBlank(remarks) && StrUtil.isNotBlank(remoteStatus)) {
            remarks = "第三方状态：" + normalizeDisplayStatus(remoteStatus);
        }

        return OrderProgressResult.builder()
                .progress(item.getStr("process"))
                .orderStatus(mapOrderStatus(remoteStatus, localOrder.getOrderStatus()))
                .remarks(remarks)
                .courseStartTime(parseTime(firstNonBlank(item.getStr("courseStartTime"), item.getStr("kcks"))))
                .courseEndTime(parseTime(firstNonBlank(item.getStr("courseEndTime"), item.getStr("kcjs"))))
                .examStartTime(parseTime(firstNonBlank(item.getStr("examStartTime"), item.getStr("ksks"))))
                .examEndTime(parseTime(firstNonBlank(item.getStr("examEndTime"), item.getStr("ksjs"))))
                .studentAccount(localOrder.getStudentAccount())
                .studentPassword(localOrder.getStudentPassword())
                .courseName(localOrder.getCourseName())
                .thirdOrderId(thirdOrderId)
                .build();
    }

    /** 只接受协议声明的数组形态；单对象响应不再猜测，直接判为无效响应。 */
    private JSONArray requireProgressArray(Object data) {
        if (!(data instanceof JSONArray array)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
        if (array.size() > MAX_ITEMS_PER_ACCOUNT) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
        return array;
    }

    private JSONObject queryOrderItem(CourseOrder order, ApiProvider apiProvider,
                                      String thirdOrderId, String operation) {
        Map<String, Object> params = authParams(apiProvider);
        params.put("yid", thirdOrderId);
        params.put("username", order.getStudentAccount());
        // 实测：username 与 school 同时传会被上游判为无结果并返回空数组，因此不传 school。

        JSONObject json = parseResponse(post(apiProvider, "chadan", operation, params), operation);
        if (!isCode(json, 1)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }

        JSONObject item = findProgressItem(json.get("data"), thirdOrderId);
        if (item == null) {
            throw new BusinessException(operation + "成功，但响应中没有匹配订单数据");
        }
        return item;
    }

    private List<ProviderOrderLog> normalizeOrderLogs(JSONArray data) {
        List<ProviderOrderLog> logs = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            logs.add(ProviderOrderLog.builder()
                    .id(firstNonBlank(item.getStr("id"), item.getStr("log_id")))
                    .title(firstNonBlank(item.getStr("title"), item.getStr("type"), item.getStr("action")))
                    .content(firstNonBlank(item.getStr("content"), item.getStr("message"), item.getStr("msg"), item.getStr("text"), item.getStr("log")))
                    .status(firstNonBlank(item.getStr("status"), item.getStr("state")))
                    .operator(firstNonBlank(item.getStr("operator"), item.getStr("admin"), item.getStr("username"), item.getStr("name")))
                    .createTime(firstNonBlank(item.getStr("create_time"), item.getStr("createTime"), item.getStr("addtime"), item.getStr("time")))
                    .build());
        }
        return logs;
    }

    private String snapshotContent(String progress, String remarks) {
        if (StrUtil.isNotBlank(progress) && StrUtil.isNotBlank(remarks)) {
            return "当前进度：" + progress + "；备注：" + remarks;
        }
        if (StrUtil.isNotBlank(progress)) {
            return "当前进度：" + progress;
        }
        if (StrUtil.isNotBlank(remarks)) {
            return remarks;
        }
        return "已获取最新执行状态";
    }

    protected Map<String, Object> authParams(ApiProvider apiProvider) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        return params;
    }

    private String post(ApiProvider apiProvider, String action, String operation, Map<String, Object> params) {
        String url = endpoint(apiProvider, action);
        log.info("{} {}请求: params={}", getProviderType(), operation,
                DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(apiProvider, url, params);
        log.debug("{} {}响应已接收: length={}", getProviderType(), operation,
                response == null ? 0 : response.length());
        return response;
    }

    private String endpoint(ApiProvider apiProvider, String action) {
        return UriComponentsBuilder.fromUri(urlNormalizer.normalize(apiProvider.getApiUrl(), getProviderType()))
                .path("/api.php").queryParam("act", action).build(true).toUriString();
    }

    private JSONObject parseResponse(String response, String operation) {
        return ProviderResponseParser.parseObject(response);
    }

    private JSONArray responseArray(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value instanceof JSONArray array) {
                return array;
            }
            if (value instanceof JSONObject object) {
                for (String nestedKey : new String[]{"list", "data", "logs"}) {
                    Object nested = object.get(nestedKey);
                    if (nested instanceof JSONArray array) {
                        return array;
                    }
                }
            }
        }
        return null;
    }

    private BigDecimal firstBigDecimal(JSONObject json, BigDecimal defaultValue, String... keys) {
        for (String key : keys) {
            BigDecimal value = toBigDecimal(json.get(key));
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String normalized = String.valueOf(value).replace(",", "").replace("¥", "").trim();
            return StrUtil.isBlank(normalized) ? null : new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private JSONObject findProgressItem(Object data, String thirdOrderId) {
        if (data instanceof JSONObject item) {
            String responseId = firstNonBlank(item.getStr("id"), item.getStr("yid"));
            return StrUtil.isBlank(responseId) || thirdOrderId.equals(responseId) ? item : null;
        }
        if (!(data instanceof JSONArray array) || array.isEmpty()) {
            return null;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String responseId = firstNonBlank(item.getStr("id"), item.getStr("yid"));
            if (thirdOrderId.equals(responseId)) {
                return item;
            }
        }
        return null;
    }

    private String extractThirdOrderId(JSONObject json) {
        String id = firstNonBlank(json.getStr("id"), json.getStr("yid"));
        if (StrUtil.isNotBlank(id)) {
            return id;
        }

        Object rawData = json.get("data");
        if (rawData instanceof JSONObject dataObject) {
            return firstNonBlank(dataObject.getStr("id"), dataObject.getStr("yid"));
        }
        if (rawData instanceof JSONArray dataArray && !dataArray.isEmpty()) {
            JSONObject first = dataArray.getJSONObject(0);
            return firstNonBlank(first.getStr("id"), first.getStr("yid"));
        }
        return null;
    }

    private String requireThirdOrderId(CourseOrder order, String operation) {
        if (order == null || StrUtil.isBlank(order.getThirdOrderId())) {
            throw new BusinessException("订单缺少第三方订单ID，无法" + operation);
        }
        return order.getThirdOrderId();
    }

    private Integer mapOrderStatus(String status, Integer currentStatus) {
        if (StrUtil.isBlank(status)) {
            return currentStatus != null
                    ? currentStatus
                    : SystemVariableCache.getStatusValue("order_status", "processing");
        }
        String normalized = status.trim();
        return switch (normalized) {
            case "待处理", "待上号" -> status("pending");
            case "进行中", "处理中", "正在学习", "学习中" -> status("processing");
            case "已完成", "完成", "已结束" -> status("completed");
            case "已取消", "取消" -> status("cancelled");
            case "异常", "失败", "错误" -> status("failed");
            case "待考试" -> status("exam_pending");
            case "考试中" -> status("exam_processing");
            case "考试完成", "已考完" -> status("exam_completed");
            case "已退款", "等待退款" -> status("refund_pending");
            default -> currentStatus != null ? currentStatus : status("processing");
        };
    }

    private Integer status(String key) {
        return SystemVariableCache.getStatusValue("order_status", key);
    }

    private String normalizeDisplayStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        return switch (status) {
            case "待处理" -> "待上号";
            case "已退款" -> "等待退款";
            default -> status;
        };
    }

    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value) || "0000-00-00 00:00:00".equals(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("{}时间字段解析失败", getProviderType());
            return null;
        }
    }

    private boolean isCode(JSONObject json, int expected) {
        return Integer.valueOf(expected).equals(json.getInt("code"));
    }

    private String message(JSONObject json, String defaultMessage) {
        return firstNonBlank(json.getStr("msg"), json.getStr("message"), defaultMessage);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
