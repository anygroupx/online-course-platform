package com.course.platform.infra.docking.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.docking.DockingLogSanitizer;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.hutool.core.util.StrUtil;

/**
 * 暗网平台对接策略
 */
@Slf4j
@Component
public class AnwangDockingStrategy implements PlatformDockingStrategy {

    private final ApiHttpClient apiHttpClient;

    public AnwangDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Override
    public String getProviderType() {
        return "yjdj";
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
        params.put("kcid", ""); // QueryCourseRequest 中没有 courseId 字段

        log.info("暗网查课请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("暗网查课响应已接收: length={}", response == null ? 0 : response.length());
        
        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == -1) {
            throw new BusinessException("查课失败: " + json.getStr("msg"));
        }

        List<CourseInfoResponse.CourseItem> courseItems = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                CourseInfoResponse.CourseItem courseItem = new CourseInfoResponse.CourseItem();
                courseItem.setId(item.getStr("id"));
                courseItem.setName(item.getStr("name"));
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
        params.put("platform", platform.getDockParam());
        params.put("school", order.getSchoolName());
        params.put("user", order.getStudentAccount());
        params.put("pass", order.getStudentPassword());
        params.put("kcname", order.getCourseName());
        params.put("kcid", order.getCourseId() != null ? order.getCourseId() : "");
        
        // 暗网特有参数：时长和分数
        // shichang: 使用倒计时时长，如果没有则传空
        String shichang = "";
        if (order.getCountdownDuration() != null && order.getCountdownDuration() > 0) {
            shichang = String.valueOf(order.getCountdownDuration());
        }
        params.put("shichang", shichang);
        
        // score: 暂不支持分数设置，传空字符串
        params.put("score", "");

        log.info("暗网下单请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("暗网下单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == 0) {
            return DockResult.success("下单成功", null);
        } else {
            return DockResult.fail(json.getStr("msg"));
        }
    }

    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        // 暗网进度查询使用独立的 /api/search 端点
        String url = apiProvider.getApiUrl() + "/api/search";
        
        // 构建GET请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("kcname", order.getCourseName());
        params.put("username", order.getStudentAccount());
        params.put("cid", platform.getDockParam());

        log.info("暗网进度查询请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.getForString(url, params);
        log.debug("暗网进度查询响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        
        if (json.getInt("code") != 1) {
            throw new BusinessException("进度查询失败: " + json.getStr("msg"));
        }

        // 暗网API返回的是数组，取第一个元素
        JSONArray dataArray = json.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty()) {
            throw new BusinessException("未查询到订单进度信息");
        }

        JSONObject data = dataArray.getJSONObject(0);
        
        OrderProgressResult result = OrderProgressResult.builder()
                .progress(data.getStr("process", "0%"))
                .remarks(data.getStr("remarks", ""))
                .build();

        // 解析时间字段（可能为空）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        String ksks = data.getStr("ksks"); // 考试开始时间
        if (ksks != null && !ksks.isEmpty() && !"0000-00-00 00:00:00".equals(ksks)) {
            try {
                result.setExamStartTime(LocalDateTime.parse(ksks, formatter));
            } catch (Exception e) {
                log.warn("解析考试开始时间失败: {}", ksks, e);
            }
        }
        
        String ksjs = data.getStr("ksjs"); // 考试结束时间
        if (ksjs != null && !ksjs.isEmpty() && !"0000-00-00 00:00:00".equals(ksjs)) {
            try {
                result.setExamEndTime(LocalDateTime.parse(ksjs, formatter));
            } catch (Exception e) {
                log.warn("解析考试结束时间失败: {}", ksjs, e);
            }
        }

        // 根据 status_text 判断订单状态
        String statusText = data.getStr("status");
        if (statusText != null) {
            if (statusText.contains("完成") || statusText.contains("成功")) {
                result.setOrderStatus(2); // 已完成
            } else if (statusText.contains("失败") || statusText.contains("错误")) {
                result.setOrderStatus(4); // 失败
            } else {
                result.setOrderStatus(1); // 进行中
            }
        }

        return result;
    }

    @Override
    public DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        String url = apiProvider.getApiUrl() + "/api.php?act=budan";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("id", order.getThirdOrderId());

        log.info("暗网补刷请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("暗网补刷响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == 0) {
            return DockResult.success("补刷成功", null);
        } else {
            return DockResult.fail(json.getStr("msg"));
        }
    }

    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        // 暗网平台一键导入：调用 /api.php?act=getclass 接口
        String url = apiProvider.getApiUrl() + "/api.php?act=getclass";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());

        log.info("暗网一键导入请求: params={}", DockingLogSanitizer.sanitize(params));
        String response = apiHttpClient.postForString(url, params);
        log.debug("暗网一键导入响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") != 1) {
            throw new BusinessException("获取平台列表失败: " + json.getStr("msg"));
        }

        List<PlatformItem> items = new ArrayList<>();
        JSONArray data = json.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                
                PlatformItem platformItem = new PlatformItem();
                platformItem.setId(item.getStr("cid"));
                platformItem.setName(item.getStr("name"));
                platformItem.setPrice(item.getBigDecimal("price", BigDecimal.ZERO));
                platformItem.setContent(item.getStr("content", ""));
                
                // 暗网API支持fenlei分类字段
                String fenlei = item.getStr("fenlei");
                if (StrUtil.isNotBlank(fenlei)) {
                    platformItem.setCategoryId(fenlei);
                    // 分类名称可能没有，使用默认命名
                    String categoryName = item.getStr("fenleiname");
                    if (StrUtil.isBlank(categoryName)) {
                        categoryName = "分类" + fenlei;
                    }
                    platformItem.setCategoryName(categoryName);
                }
                
                items.add(platformItem);
            }
        }
        
        log.info("暗网一键导入成功: 共获取{}个课程", items.size());
        return items;
    }
}
