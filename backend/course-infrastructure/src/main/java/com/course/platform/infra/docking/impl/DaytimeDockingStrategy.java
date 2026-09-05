package com.course.platform.infra.docking.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.docking.DockingLogSanitizer;
import com.course.platform.infra.external.ApiHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daytime（29 系统）独立对接策略。
 */
@Slf4j
@Component
public class DaytimeDockingStrategy implements PlatformDockingStrategy {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    protected final ApiHttpClient apiHttpClient;

    public DaytimeDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Override
    public String getProviderType() {
        return "Daytime";
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
        if (isCode(json, -1)) {
            throw new BusinessException("查课失败: " + message(json, "第三方接口返回失败"));
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
            return DockResult.fail(message(json, "下单失败"));
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

        Map<String, Object> params = authParams(apiProvider);
        params.put("yid", thirdOrderId);
        params.put("username", order.getStudentAccount());
        params.put("school", order.getSchoolName());

        JSONObject json = parseResponse(post(apiProvider, "chadan", "进度查询", params), "进度查询");
        if (!isCode(json, 1)) {
            throw new BusinessException("进度查询失败: " + message(json, "第三方接口返回失败"));
        }

        JSONObject item = findProgressItem(json.getJSONArray("data"), thirdOrderId);
        if (item == null) {
            throw new BusinessException("进度查询成功，但响应中没有订单数据");
        }

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
            return DockResult.success(message(json, "补刷提交成功"), thirdOrderId);
        }
        return DockResult.fail(message(json, "补刷失败"));
    }

    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        Map<String, Object> params = authParams(apiProvider);
        JSONObject json = parseResponse(post(apiProvider, "getclass", "商品列表", params), "商品列表");
        if (isCode(json, -1)) {
            throw new BusinessException("获取商品列表失败: " + message(json, "第三方接口返回失败"));
        }

        List<PlatformItem> items = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");
        if (data == null) {
            return items;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            items.add(PlatformItem.builder()
                    .id(firstNonBlank(item.getStr("cid"), item.getStr("id")))
                    .name(firstNonBlank(item.getStr("name"), item.getStr("kcname")))
                    .price(item.getBigDecimal("price", BigDecimal.ZERO))
                    .categoryId(item.getStr("fenlei"))
                    .categoryName(firstNonBlank(item.getStr("category_name"), item.getStr("fenleiname")))
                    .type(getProviderType())
                    .content(item.getStr("content"))
                    .build());
        }
        return items;
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
        String response = apiHttpClient.postForString(url, params);
        log.debug("{} {}响应已接收: length={}", getProviderType(), operation,
                response == null ? 0 : response.length());
        return response;
    }

    private String endpoint(ApiProvider apiProvider, String action) {
        String baseUrl = StrUtil.removeSuffix(apiProvider.getApiUrl(), "/");
        return baseUrl + "/api.php?act=" + action;
    }

    private JSONObject parseResponse(String response, String operation) {
        if (StrUtil.isBlank(response)) {
            throw new BusinessException(operation + "失败: 第三方接口返回空响应");
        }
        try {
            return JSONUtil.parseObj(response);
        } catch (Exception e) {
            throw new BusinessException(operation + "失败: 第三方接口返回格式错误");
        }
    }

    private JSONObject findProgressItem(JSONArray data, String thirdOrderId) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            String responseId = firstNonBlank(item.getStr("id"), item.getStr("yid"));
            if (thirdOrderId.equals(responseId)) {
                return item;
            }
        }
        return data.getJSONObject(0);
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
