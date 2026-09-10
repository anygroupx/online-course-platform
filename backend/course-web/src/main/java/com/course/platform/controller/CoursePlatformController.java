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
import java.util.Map;
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
        Map<Long, String> enabledCategories = enabledCategories();
        Set<Long> enabledCategoryIds = enabledCategories.keySet();
        List<CoursePlatform> list = coursePlatformMapper.selectList(new LambdaQueryWrapper<CoursePlatform>()
                .eq(CoursePlatform::getStatus, 1)
                .and(w -> {
                    w.isNull(CoursePlatform::getCategoryId);
                    if (!enabledCategoryIds.isEmpty()) {
                        w.or().in(CoursePlatform::getCategoryId, enabledCategoryIds);
                    }
                })
                .orderByAsc(CoursePlatform::getSortOrder));
        list.forEach(platform -> decorate(platform, enabledCategories.get(platform.getCategoryId())));
        return Result.success(list);
    }

    private Map<Long, String> enabledCategories() {
        return platformCategoryMapper.selectList(new LambdaQueryWrapper<PlatformCategory>()
                .eq(PlatformCategory::getStatus, 1)).stream()
                .collect(Collectors.toMap(PlatformCategory::getId, PlatformCategory::getName));
    }

    /**
     * 获取课程平台详情
     */
    @Operation(summary = "获取课程平台详情", description = "根据ID获取课程平台详细信息")
    @GetMapping("/{id}")
    public Result<CoursePlatform> getCoursePlatform(@PathVariable Long id) {
        CoursePlatform platform = coursePlatformMapper.selectById(id);
        if (platform != null) {
            PlatformCategory category = platform.getCategoryId() == null
                    ? null
                    : platformCategoryMapper.selectById(platform.getCategoryId());
            decorate(platform, category == null ? null : category.getName());
        }
        return Result.success(platform);
    }

    private void decorate(CoursePlatform platform, String categoryName) {
        if (platform.getDisplayName() == null) {
            platform.setDisplayName(platform.getName());
        }
        platform.setCategoryName(categoryName);
    }
}
