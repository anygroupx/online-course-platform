package com.course.platform.service.docking.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.service.docking.PlatformDockingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 29同系统平台对接策略
 */
@Component
public class TwentyNineDockingStrategy implements PlatformDockingStrategy {

    private static final Logger log = LoggerFactory.getLogger(TwentyNineDockingStrategy.class);

    private final ApiHttpClient apiHttpClient;

    public TwentyNineDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Override
    public String getProviderType() {
        return "29";
    }

    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request, ApiProvider apiProvider) {
        // 29同系统查课逻辑与Benz类似
        String url = apiProvider.getApiUrl() + "/api.php?act=get";
        
        Map<String, Object> params = new HashMap<>();
        params.put("uid", apiProvider.getUsername());
        params.put("key", apiProvider.getApiKey());
        params.put("platform", platform.getQueryParam());
        params.put("school", request.getSchoolName());
        params.put("user", request.getStudentAccount());
        params.put("pass", request.getStudentPassword());

        log.info("29查课请求: url={}, params={}", url, params);
        String response = apiHttpClient.postForString(url, params);
        
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
        params.put("kcid", order.getCourseId()); // 29特有参数

        log.info("29下单请求: url={}, params={}", url, params);
        String response = apiHttpClient.postForString(url, params);
        log.info("29下单响应: {}", response);

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == 0) {
            return DockResult.success("下单成功", null);
        } else {
            return DockResult.fail(json.getStr("msg"));
        }
    }

    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        // 复用Benz逻辑
        return new BenzDockingStrategy(apiHttpClient).queryOrderProgress(order, platform, apiProvider);
    }

    @Override
    public DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        // 复用Benz逻辑
        return new BenzDockingStrategy(apiHttpClient).retryOrder(order, platform, apiProvider);
    }

    @Override
    public List<PlatformItem> fetchPlatformList(ApiProvider apiProvider) {
        // 复用Benz逻辑
        return new BenzDockingStrategy(apiHttpClient).fetchPlatformList(apiProvider);
    }
}
