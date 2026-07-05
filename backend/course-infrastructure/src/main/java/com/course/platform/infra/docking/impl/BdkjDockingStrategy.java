package com.course.platform.infra.docking.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.course.platform.common.exception.BusinessException;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 暗网 (bdkj) 平台对接策略
 */
@Slf4j
@Component
public class BdkjDockingStrategy implements PlatformDockingStrategy {

    private final ApiHttpClient apiHttpClient;

    public BdkjDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Override
    public String getProviderType() {
        return "bdkj";
    }

    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request, ApiProvider apiProvider) {
        // 复用29逻辑
        return new TwentyNineDockingStrategy(apiHttpClient).queryCourses(platform, request, apiProvider);
    }

    @Override
    public DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        // 复用29逻辑
        return new TwentyNineDockingStrategy(apiHttpClient).dockOrder(order, platform, apiProvider);
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
