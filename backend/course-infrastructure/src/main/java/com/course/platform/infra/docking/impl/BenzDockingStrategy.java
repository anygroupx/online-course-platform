package com.course.platform.infra.docking.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.course.platform.infra.docking.ProviderResponseParser;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.docking.DockingLogSanitizer;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Benz (27) 平台对接策略
 */
@Slf4j
@Component
public class BenzDockingStrategy implements PlatformDockingStrategy {

    private final ApiHttpClient apiHttpClient;

    public BenzDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Override
    public String getProviderType() {
        return "27";
    }

    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request, ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=get";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("platform", platform.getQueryParam());
        params.put("school", request.getSchoolName());
        params.put("user", request.getStudentAccount());
        params.put("pass", request.getStudentPassword());

        log.info("Benz查课请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(apiProvider, url, params);
        log.debug("Benz查课响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = ProviderResponseParser.parseObject(response);
        if (json.getInt("code") != 1 && json.getInt("code") != 0) { // 部分接口成功码可能是0或1，需根据实际调整，参考代码中是code!=1为错，但benz对接.php中是code==-1为错
             // 参考benz对接.php: if ($result["code"] == -1 ) { ... } else { ... }
             // 这里假设非-1即为成功，或者根据msg判断
             if (json.getInt("code") == -1) {
                 throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
             }
        }

        List<CourseInfoResponse.CourseItem> courseItems = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                CourseInfoResponse.CourseItem courseItem = new CourseInfoResponse.CourseItem();
                courseItem.setId(item.getStr("id"));
                courseItem.setName(item.getStr("name"));
                // Benz接口可能不返回封面图，使用默认
                // courseItem.setCoverImage("https://via.placeholder.com/150"); 
                courseItems.add(courseItem);
            }
        }
        return courseItems;
    }

    @Override
    public DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=add";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("platform", platform.getDockParam()); // 使用dockParam作为platform参数
        params.put("school", order.getSchoolName());
        params.put("user", order.getStudentAccount());
        params.put("pass", order.getStudentPassword());
        params.put("kcname", order.getCourseName());
        params.put("kcid", order.getCourseId());
        // 可选参数
        // params.put("miaoshua", order.getIsFlash() == 1 ? "1" : "0");

        log.info("Benz下单请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(apiProvider, url, params);
        log.debug("Benz下单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = ProviderResponseParser.parseObject(response);
        if (json.getInt("code") == 1 || json.getInt("code") == 0) { // 参考代码中 code==0 为成功
            // An accepted order without one unambiguous structured ID stays unbound.
            // Digits in message text can be a price/count, never an ownership receipt.
            String thirdOrderId = BenzReceiptMatcher.createdId(response);

            log.info("Benz下单成功，已获取第三方订单ID: {}", StrUtil.isNotBlank(thirdOrderId));
            return DockResult.success("下单成功", thirdOrderId);
        } else {
            return DockResult.fail(ProviderRequestException.PUBLIC_MESSAGE);
        }
    }

    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        if (StrUtil.isBlank(order.getThirdOrderId())) {
            // Never infer a receipt from the first account/name result, including before budan.
            throw new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
        }
        String response = apiHttpClient.postForString(apiProvider,
                apiProvider.getApiUrl() + "/api.php?act=chadan",
                BenzReceiptMatcher.query(order, apiProvider.getUsername(), apiProvider.getApiKey()));
        return parseProgressItem(BenzReceiptMatcher.select(response, order, platform,
                order.getThirdOrderId(), false));
    }

    private OrderProgressResult parseProgressItem(JSONObject item) {
        String statusText = item.getStr("status"); // "进行中", "已完成"
        String process = item.getStr("process"); // 进度描述
        String remarks = item.getStr("remarks");
        
        Integer orderStatus = SystemVariableCache.getStatusValue("order_status", "processing");
        if ("已完成".equals(statusText) || "完成".equals(statusText)) {
            orderStatus = SystemVariableCache.getStatusValue("order_status", "completed");
        } else if ("异常".equals(statusText)) {
            orderStatus = SystemVariableCache.getStatusValue("order_status", "failed");
        }

        // 解析时间
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime ksks = parseTime(item.getStr("courseStartTime"), fmt);
        LocalDateTime ksjs = parseTime(item.getStr("courseEndTime"), fmt);
        LocalDateTime ksks_exam = parseTime(item.getStr("examStartTime"), fmt);
        LocalDateTime ksjs_exam = parseTime(item.getStr("examEndTime"), fmt);
        
        // 提取第三方订单ID（yid/id）- 用于补单
        String thirdOrderId = item.getStr("id");
        if (StrUtil.isBlank(thirdOrderId)) {
            thirdOrderId = item.getStr("yid");
        }

        return OrderProgressResult.builder()
                .progress(process)
                .orderStatus(orderStatus)
                .remarks(remarks)
                .courseStartTime(ksks)
                .courseEndTime(ksjs)
                .examStartTime(ksks_exam)
                .examEndTime(ksjs_exam)
                .thirdOrderId(thirdOrderId) // 设置第三方订单ID
                .build();
    }
    
    private LocalDateTime parseTime(String timeStr, DateTimeFormatter fmt) {
        if (StrUtil.isBlank(timeStr)) return null;
        try {
            return LocalDateTime.parse(timeStr, fmt);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=budan";
        
        // 补单需要第三方订单ID (yid)
        String thirdOrderId = order.getThirdOrderId();
        
        if (StrUtil.isBlank(thirdOrderId)) {
            return DockResult.fail("请先核对并恢复执行编号，再发起补单");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("id", thirdOrderId);

        log.info("Benz补单请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(apiProvider, url, params);
        log.debug("Benz补单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = ProviderResponseParser.parseObject(response);
        // 参考 bsjk.php: code==1 为成功
        if (json.getInt("code") == 1) {
            return DockResult.success("补单提交成功", thirdOrderId);
        } else {
            return DockResult.fail(ProviderRequestException.PUBLIC_MESSAGE);
        }
    }
    @Override
    public void testConnection(ApiProvider apiProvider) {
        fetchPlatformList(apiProvider);
    }

    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=getclass";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());

        log.info("Benz获取课程列表请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(apiProvider, url, params);
        // log.debug("Benz获取课程列表响应已接收: length={}", response == null ? 0 : response.length()); // 响应可能很大，暂不打印

        JSONObject json = ProviderResponseParser.parseObject(response);
        if (json.getInt("code") != 1 && json.getInt("code") != 0) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }

        List<PlatformItem> items = new ArrayList<>();
        JSONArray data = ProviderResponseParser.requireArray(json, "data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                PlatformItem platformItem = PlatformItem.builder()
                        .id(item.getStr("cid"))
                        .name(item.getStr("name"))
                        .price(item.getBigDecimal("price"))
                        .categoryId(item.getStr("fenlei"))           // 分类ID
                        .categoryName(item.getStr("category_name"))  // 分类名称
                        .type(getProviderType())
                        .content(item.getStr("content"))
                        .build();
                items.add(platformItem);
            }
        }
        return items;
    }

    @Override
    public List<OrderProgressResult> batchQueryOrderProgress(ApiProvider apiProvider, Long timestampSeconds, Integer offset) {
        String url = apiProvider.getApiUrl() + "/api.php?act=plchadan";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("offset", offset != null ? offset : 0);
        
        // 如果提供了时间戳，则进行增量查询
        if (timestampSeconds != null) {
            params.put("timestamp", timestampSeconds);
        }

        log.info("Benz批量查单请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(apiProvider, url, params);
        log.info("Benz批量查单响应数据量: {} 字节", response != null ? response.length() : 0);

        JSONObject json = ProviderResponseParser.parseObject(response);
        if (json.getInt("code") != 1 && json.getInt("code") != 0) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }
        JSONArray data = ProviderResponseParser.requireArray(json, "data");
        if (data.size() > 10000) {
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
        List<OrderProgressResult> results = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            // Reject the whole page instead of silently dropping rows and advancing the watermark.
            if (!(data.get(i) instanceof JSONObject item)) {
                throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
            }
            results.add(parseBatchProgressItem(item));
        }
        
        log.info("Benz批量查单解析结果: {} 条订单", results.size());
        return results;
    }

    /**
     * 解析批量查询返回的订单进度项
     * 参考 benztb.php 的字段映射关系
     */
    private OrderProgressResult parseBatchProgressItem(JSONObject item) {
        try {
            for (String field : java.util.List.of("user", "pass", "kcname", "id")) {
                Object value = item.get(field);
                if (!(value instanceof String || value instanceof Number)
                        || String.valueOf(value).isBlank() || String.valueOf(value).length() > 2048
                        || String.valueOf(value).codePoints().anyMatch(Character::isISOControl)) {
                    throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
                }
            }
            String statusText = item.getStr("status");
            String process = item.getStr("process");
            String remarks = item.getStr("remarks");
            
            Integer orderStatus = SystemVariableCache.getStatusValue("order_status", "processing");
            if ("已完成".equals(statusText) || "完成".equals(statusText)) {
                orderStatus = SystemVariableCache.getStatusValue("order_status", "completed");
            } else if ("异常".equals(statusText)) {
                orderStatus = SystemVariableCache.getStatusValue("order_status", "failed");
            }

            // 解析时间
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime courseStart = parseTime(item.getStr("kcks"), fmt);  // courseStartTime
            LocalDateTime courseEnd = parseTime(item.getStr("kcjs"), fmt);    // courseEndTime
            LocalDateTime examStart = parseTime(item.getStr("ksks"), fmt);    // examStartTime
            LocalDateTime examEnd = parseTime(item.getStr("ksjs"), fmt);      // examEndTime

            return OrderProgressResult.builder()
                    // 进度信息
                    .progress(process)
                    .orderStatus(orderStatus)
                    .remarks(remarks)
                    // 时间信息
                    .courseStartTime(courseStart)
                    .courseEndTime(courseEnd)
                    .examStartTime(examStart)
                    .examEndTime(examEnd)
                    // 订单标识信息（用于匹配本地订单）
                    .studentAccount(item.getStr("user"))
                    .studentPassword(item.getStr("pass"))
                    .courseName(item.getStr("kcname"))
                    .thirdOrderId(item.getStr("id"))  // yid
                    .build();
        } catch (Exception e) {
            // Parser messages can contain account/password fields. Do not log or attach the cause.
            throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
        }
    }
}
