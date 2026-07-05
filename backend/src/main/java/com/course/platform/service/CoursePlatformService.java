package com.course.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.CoursePlatform;

/**
 * 课程平台服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface CoursePlatformService {

    /**
     * 创建课程平台
     * 
     * @param platform 课程平台信息
     * @return 平台ID
     */
    Long createPlatform(CoursePlatform platform);

    /**
     * 更新课程平台
     * 
     * @param platform 课程平台信息
     */
    void updatePlatform(CoursePlatform platform);

    /**
     * 删除课程平台
     * 
     * @param id 平台ID
     */
    void deletePlatform(Long id);

    /**
     * 分页查询课程平台
     * 
     * @param keyword 搜索关键词
     * @param status 状态
     * @param categoryId 分类ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 平台分页数据
     */
    IPage<CoursePlatform> queryPlatforms(String keyword, Integer status, Long categoryId, Integer page, Integer pageSize);

    /**
     * 根据分类ID批量删除课程平台
     * 
     * @param categoryId 分类ID
     * @return 删除的记录数
     */
    int deletePlatformsByCategoryId(Long categoryId);
}

