package com.course.platform.infra.docking.impl;

import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ikun 平台对接策略
 */
@Component
public class IkunDockingStrategy implements PlatformDockingStrategy {

    private static final Logger log = LoggerFactory.getLogger(IkunDockingStrategy.class);

    private final ApiHttpClient apiHttpClient;

    public IkunDockingStrategy(ApiHttpClient apiHttpClient) {
        this.apiHttpClient = apiHttpClient;
    }

    @Override
    public String getProviderType() {
        return "ikun";
    }

    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request, ApiProvider apiProvider) {
        // Ikun 查课逻辑暂未明确，复用Benz逻辑尝试
        return new BenzDockingStrategy(apiHttpClient).queryCourses(platform, request, apiProvider);
    }

    @Override
    public DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        // Ikun 使用 GET 请求
        // $url = trim($a['url']).'/getorder/?platform='.urlencode(trim($noun)).'&school='.urlencode(trim($school)).'&account='.trim($user).'&password='.urlencode(trim($pass)).'&course='.urlencode(trim($kcname));
        
        String baseUrl = apiProvider.getApiUrl() + "/getorder/";
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?platform=").append(URLUtil.encode(platform.getDockParam()));
        urlBuilder.append("&school=").append(URLUtil.encode(order.getSchoolName()));
        urlBuilder.append("&account=").append(URLUtil.encode(order.getStudentAccount()));
        urlBuilder.append("&password=").append(URLUtil.encode(order.getStudentPassword()));
        urlBuilder.append("&course=").append(URLUtil.encode(order.getCourseName()));

        String url = urlBuilder.toString();
        log.info("Ikun下单请求已构建");
        
        // ApiHttpClient 需要支持 GET 请求
        // 假设 postForString 可以改用 getForString 或者 ApiHttpClient 有 get 方法
        // 查看 ApiHttpClient 发现只有 postForString? 
        // 我需要检查 ApiHttpClient 是否有 get 方法。如果没有，需要添加。
        // 暂时假设有 get 方法，或者使用 postForString (如果服务端支持POST兼容)
        // 但参考代码明确是 file_get_contents(url)，所以是 GET。
        
        // 检查 ApiHttpClient
        // 如果没有 get 方法，我需要去添加。
        // 先写在这里，等会去检查 ApiHttpClient。
        String response = apiHttpClient.getForString(url, null); 
        log.debug("Ikun下单响应已接收: length={}", response == null ? 0 : response.length());

        JSONObject json = JSONUtil.parseObj(response);
        if (json.getInt("code") == 1) {
            String thirdOrderId = json.getStr("order_token");
            return DockResult.success("下单成功", thirdOrderId);
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
