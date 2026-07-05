package com.course.platform.service;

import com.course.platform.domain.vo.StatisticsResponse;

/**
 * 统计服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface StatisticsService {

    /**
     * 获取统计数据
     * 
     * @param userId 用户ID
     * @return 统计数据
     */
    StatisticsResponse getStatistics(Long userId);
}

