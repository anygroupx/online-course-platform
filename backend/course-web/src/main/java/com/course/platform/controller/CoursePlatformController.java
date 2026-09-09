package com.course.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.PlatformCategory;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.PlatformCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程平台控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "课程管理", description = "课程平台列表、价格查询等接口")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CoursePlatformController {

    private final CoursePlatformMapper coursePlatformMapper;
    private final PlatformCategoryMapper platformCategoryMapper;

    /**
     * 获取课程平台列表
     */
    @Operation(summary = "获取课程平台列表", description = "获取所有可用的课程平台")
    @GetMapping
    public Result<List<CoursePlatform>> getCoursePlatforms() {
        Set<Long> enabledCategoryIds = enabledCategoryIds();
        List<CoursePlatform> list = coursePlatformMapper.selectList(new LambdaQueryWrapper<CoursePlatform>()
                .eq(CoursePlatform::getStatus, 1)
                .and(w -> {
                    w.isNull(CoursePlatform::getCategoryId);
                    if (!enabledCategoryIds.isEmpty()) {
                        w.or().in(CoursePlatform::getCategoryId, enabledCategoryIds);
                    }
                })
                .orderByAsc(CoursePlatform::getSortOrder));
        return Result.success(list);
    }

    private Set<Long> enabledCategoryIds() {
        return platformCategoryMapper.selectList(new LambdaQueryWrapper<PlatformCategory>()
                .eq(PlatformCategory::getStatus, 1)).stream()
                .map(PlatformCategory::getId).collect(Collectors.toSet());
    }

    /**
     * 获取课程平台详情
     */
    @Operation(summary = "获取课程平台详情", description = "根据ID获取课程平台详细信息")
    @GetMapping("/{id}")
    public Result<CoursePlatform> getCoursePlatform(@PathVariable Long id) {
        CoursePlatform platform = coursePlatformMapper.selectById(id);
        return Result.success(platform);
    }
}

