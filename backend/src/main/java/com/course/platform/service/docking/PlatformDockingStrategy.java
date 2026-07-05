
package com.course.platform.service.docking;

import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;

import java.util.List;

/**
 * 平台对接策略接口
 */
public interface PlatformDockingStrategy {

    /**
     * 获取支持的平台类型 (对应ApiProvider.providerType)
     *
     * @return 平台类型标识
     */
    String getProviderType();

    /**
     * 查询课程
     *
     * @param platform    平台信息
     * @param request     查询请求
     * @param apiProvider API配置
     * @return 课程列表
     */
    List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request, ApiProvider apiProvider);

    /**
     * 对接下单
     *
     * @param order       订单信息
     * @param platform    平台信息
     * @param apiProvider API配置
     * @return 对接结果
     */
    DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider);

    /**
     * 查询订单进度
     *
     * @param order       订单信息
     * @param platform    平台信息
     * @param apiProvider API配置
     * @return 进度结果
     */
    OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider);

    /**
     * 补单
     * @param order 订单信息
     * @param platform 平台配置
     * @param apiProvider API配置
     * @return 对接结果
     */
    DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider);

    /**
     * 获取平台/课程列表 (用于一键上架)
     * @param apiProvider API配置
     * @return 平台/课程列表
     */
    List<PlatformItem> fetchPlatformList(ApiProvider apiProvider);

    /**
     * 批量查询订单进度（增量）
     * 适用于支持批量查询的第三方平台（如 Benz）
     *
     * @param apiProvider      API配置
     * @param timestampSeconds 时间戳（秒），获取该时间点之后更新的订单，为null则全量查询
     * @param offset           分页偏移量
     * @return 订单进度列表
     */
    default List<OrderProgressResult> batchQueryOrderProgress(ApiProvider apiProvider, Long timestampSeconds, Integer offset) {
        // 默认实现：返回空列表，表示不支持批量查询
        // 各个策略可根据第三方平台能力决定是否override此方法
        return java.util.Collections.emptyList();
    }
}
