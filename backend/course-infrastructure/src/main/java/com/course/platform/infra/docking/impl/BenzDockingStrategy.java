package com.course.platform.infra.docking.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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

        log.info("Benz查课请求: url={}, params={}", url, DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("Benz查课响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") != 1 && json.getInt("code") != 0) { // 部分接口成功码可能是0或1，需根据实际调整，参考代码中是code!=1为错，但benz对接.php中是code==-1为错
             // 参考benz对接.php: if ($result["code"] == -1 ) { ... } else { ... }
             // 这里假设非-1即为成功，或者根据msg判断
             if (json.getInt("code") == -1) {
                 throw new BusinessException("查课失败: " + json.getStr("msg"));
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

        log.info("Benz下单请求: url={}, params={}", url, DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("Benz下单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == 1 || json.getInt("code") == 0) { // 参考代码中 code==0 为成功
            // 尝试从响应中提取订单ID
            String thirdOrderId = null;
            
            // 方式1: 检查 data.id 字段
            if (json.containsKey("data")) {
                Object dataObj = json.get("data");
                if (dataObj instanceof JSONObject) {
                    JSONObject data = (JSONObject) dataObj;
                    thirdOrderId = data.getStr("id");
                } else if (dataObj instanceof JSONArray) {
                    JSONArray dataArray = (JSONArray) dataObj;
                    if (dataArray.size() > 0 && dataArray.get(0) instanceof JSONObject) {
                        thirdOrderId = ((JSONObject) dataArray.get(0)).getStr("id");
                    }
                }
            }
            
            // 方式2: 直接从根级别获取 id 字段
            if (StrUtil.isBlank(thirdOrderId)) {
                thirdOrderId = json.getStr("id");
            }
            
            // 方式3: 从 msg 中提取（某些API可能在msg中返回订单号）
            if (StrUtil.isBlank(thirdOrderId)) {
                String msg = json.getStr("msg");
                // 如果msg包含数字，尝试提取
                if (StrUtil.isNotBlank(msg) && msg.matches(".*\\d+.*")) {
                    // 提取第一个连续的数字序列
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
                    java.util.regex.Matcher matcher = pattern.matcher(msg);
                    if (matcher.find()) {
                        thirdOrderId = matcher.group();
                    }
                }
            }
            
            log.info("Benz下单成功，已获取第三方订单ID: {}", StrUtil.isNotBlank(thirdOrderId));
            return DockResult.success("下单成功", thirdOrderId);
        } else {
            return DockResult.fail(json.getStr("msg"));
        }
    }

    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=chadan";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("user", order.getStudentAccount()); // 学生账号
        params.put("pass", order.getStudentPassword()); // 学生密码
        params.put("school", order.getSchoolName());    // 学校名称
        params.put("kcname", order.getCourseName());    // 课程名称（更精确匹配）

        log.info("Benz查单请求: url={}, params={}", url, DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("Benz查单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == 1) {
            JSONArray data = json.getJSONArray("data");
            if (data != null && data.size() > 0) {
                // 返回第一个匹配的订单（已通过参数精确匹配）
                return parseProgressItem(data.getJSONObject(0));
            }
            // 未找到对应课程
            return OrderProgressResult.builder()
                    .progress(order.getProgress())
                    .orderStatus(order.getOrderStatus())
                    .remarks("未在第三方平台找到该课程订单")
                    .build();
        } else {
            throw new BusinessException("查单失败: " + json.getStr("msg"));
        }
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
        
        // 如果没有 thirdOrderId，尝试通过查单获取
        if (StrUtil.isBlank(thirdOrderId)) {
            log.warn("订单缺少第三方订单ID，尝试通过查单获取：orderId={}", order.getId());
            try {
                OrderProgressResult progressResult = queryOrderProgress(order, platform, apiProvider);
                thirdOrderId = progressResult.getThirdOrderId();
                
                if (StrUtil.isNotBlank(thirdOrderId)) {
                    log.info("通过查单获取到第三方订单ID");
                    // 更新订单的 third_order_id
                    order.setThirdOrderId(thirdOrderId);
                } else {
                    return DockResult.fail("无法获取第三方订单ID，补单失败");
                }
            } catch (Exception e) {
                log.error("查询第三方订单ID失败：{}", e.getMessage(), e);
                return DockResult.fail("查询第三方订单ID失败：" + e.getMessage());
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("id", thirdOrderId);

        log.info("Benz补单请求: url={}, params={}", url, DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("Benz补单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        // 参考 bsjk.php: code==1 为成功
        if (json.getInt("code") == 1) {
            return DockResult.success("补单提交成功", thirdOrderId);
        } else {
            return DockResult.fail(json.getStr("msg"));
        }
    }
    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=getclass";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());

        log.info("Benz获取课程列表请求: url={}, params={}", url, DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        // log.debug("Benz获取课程列表响应已接收: length={}", response == null ? 0 : response.length()); // 响应可能很大，暂不打印

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") != 1 && json.getInt("code") != 0) {
             if (json.getInt("code") == -1) {
                 throw new BusinessException("获取课程列表失败: " + json.getStr("msg"));
             }
        }

        List<PlatformItem> items = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");
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

        log.info("Benz批量查单请求: url={}, params={}", url, DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.info("Benz批量查单响应数据量: {} 字节", response != null ? response.length() : 0);

        JSONObject json = JSONUtil.parseObj(response);
        List<OrderProgressResult> results = new ArrayList<>();
        
        // 根据 benztb.php，直接从 data 数组获取订单列表
        JSONArray data = json.getJSONArray("data");
        if (data != null && data.size() > 0) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                OrderProgressResult result = parseBatchProgressItem(item);
                if (result != null) {
                    results.add(result);
                }
            }
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
            log.error("解析批量订单进度失败，已跳过敏感响应内容", e);
            return null;
        }
    }
}
