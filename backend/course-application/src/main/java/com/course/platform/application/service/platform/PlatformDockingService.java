package com.course.platform.application.service.platform;

import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.vo.CourseInfoResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 平台对接服务接口
 */
public interface PlatformDockingService {

    /**
     * 查询课程
     *
     * @param platform 平台信息
     * @param request  查询请求
     * @return 课程列表
     */
    List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request);

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
     *
     * @param order       订单信息
     * @param platform    平台信息
     * @param apiProvider API配置
     * @return 对接结果
     */
    DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider);

    /**
     * 一键导入平台/课程
     *
     * @param apiProviderId    API配置ID
     * @param priceMultiplier  价格倍率
     * @param targetCategoryId 目标分类ID (可选)
     * @return 导入结果 (成功数, 失败数等)
     */
    Map<String, Object> importPlatforms(Long apiProviderId, BigDecimal priceMultiplier, String targetCategoryId);

    /**
     * 一键导入平台/课程（增强版）
     *
     * @param apiProviderId    API配置ID
     * @param priceMultiplier  价格倍率
     * @param targetCategoryId 目标分类ID (可选，支持远程分类ID)
     * @param syncCategories   是否同步分类
     * @param skipCategoryIds  跳过的分类ID列表
     * @return 导入结果
     */
    Map<String, Object> importPlatforms(Long apiProviderId, BigDecimal priceMultiplier, String targetCategoryId, Boolean syncCategories, List<String> skipCategoryIds);

    /**
     * 批量同步订单进度（增量）
     *
     * @param apiProviderId    API配置ID
     * @param timestampSeconds 上次同步时间戳（秒），为null则全量同步
     * @param offset           分页偏移量
     * @return 同步结果统计 (syncedCount, updatedCount, timestamp等)
     */
    Map<String, Object> batchSyncOrderProgress(Long apiProviderId, Long timestampSeconds, Integer offset);
}
