package com.course.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.shared.result.Result;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.mapper.CoursePlatformMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 获取课程平台列表
     */
    @Operation(summary = "获取课程平台列表", description = "获取所有可用的课程平台")
    @GetMapping
    public Result<List<CoursePlatform>> getCoursePlatforms() {
        List<CoursePlatform> list = coursePlatformMapper.selectList(new LambdaQueryWrapper<CoursePlatform>()
                .eq(CoursePlatform::getStatus, 1)
                .orderByAsc(CoursePlatform::getSortOrder));
        return Result.success(list);
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

