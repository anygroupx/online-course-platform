package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.application.service.course.CoursePlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程平台服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoursePlatformServiceImpl implements CoursePlatformService {

    private final CoursePlatformMapper coursePlatformMapper;
    private final ApiProviderMapper apiProviderMapper;
    private final PlatformDockingStrategyFactory courseStrategies;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPlatform(CoursePlatform platform) {
        requireCourseProvider(platform.getQueryApiId());
        requireCourseProvider(platform.getDockApiId());
        // 检查名称是否重复
        CoursePlatform existing = coursePlatformMapper.selectOne(new LambdaQueryWrapper<CoursePlatform>()
                .eq(CoursePlatform::getName, platform.getName()));

        if (existing != null) {
            throw new BusinessException("平台名称已存在");
        }

        coursePlatformMapper.insert(platform);

        log.info("课程平台创建成功：id={}, name={}", platform.getId(), platform.getName());

        return platform.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlatform(CoursePlatform platform) {
        CoursePlatform existing = coursePlatformMapper.selectById(platform.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "课程平台不存在");
        }

        requireCourseProvider(platform.getQueryApiId() == null ? existing.getQueryApiId() : platform.getQueryApiId());
        requireCourseProvider(platform.getDockApiId() == null ? existing.getDockApiId() : platform.getDockApiId());
        coursePlatformMapper.updateById(platform);

        log.info("课程平台更新成功：id={}", platform.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlatform(Long id) {
        CoursePlatform platform = coursePlatformMapper.selectById(id);
        if (platform == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "课程平台不存在");
        }

        coursePlatformMapper.deleteById(id);

        log.info("课程平台删除成功：id={}", id);
    }

    @Override
    public IPage<CoursePlatform> queryPlatforms(String keyword, Integer status, Long categoryId, Integer page, Integer pageSize) {
        Page<CoursePlatform> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<CoursePlatform> queryWrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.like(CoursePlatform::getName, keyword);
        }

        if (status != null) {
            queryWrapper.eq(CoursePlatform::getStatus, status);
        }

        if (categoryId != null) {
            if (categoryId == 0) {
                // 0 表示查询未分类（categoryId 为 null）
                queryWrapper.isNull(CoursePlatform::getCategoryId);
            } else {
                queryWrapper.eq(CoursePlatform::getCategoryId, categoryId);
            }
        }

        queryWrapper.orderByAsc(CoursePlatform::getSortOrder);

        return coursePlatformMapper.selectPage(pageObj, queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePlatformsByCategoryId(Long categoryId) {
        LambdaQueryWrapper<CoursePlatform> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CoursePlatform::getCategoryId, categoryId);
        
        int count = coursePlatformMapper.delete(queryWrapper);
        
        log.info("批量删除课程平台成功：categoryId={}, 删除数量={}", categoryId, count);
        
        return count;
    }
    /** A catalog-only provider must never enter a paid course workflow, even via direct admin API. */
    private void requireCourseProvider(Long id) {
        if (id == null) return;
        ApiProvider provider = id > 0 ? apiProviderMapper.selectById(id) : null;
        if (provider == null || courseStrategies.getStrategy(provider.getProviderType()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "课程只能绑定支持课程业务的接口；只读插件请使用插件集成页");
        }
    }

}

