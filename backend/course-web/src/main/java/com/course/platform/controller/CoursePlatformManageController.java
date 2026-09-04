package com.course.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.SecurityUtils;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.application.service.course.CoursePlatformService;
import com.course.platform.application.service.support.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 课程平台管理控制器
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "课程平台管理", description = "课程平台增删改查接口（管理员）")
@PreAuthorize("hasAuthority('platform:update')")
@RequestMapping("/admin/platforms")
@RequiredArgsConstructor
@RestController
public class CoursePlatformManageController {

    private final CoursePlatformService coursePlatformService;
    private final OperationLogService operationLogService;

    /**
     * 验证管理员权限
     */
    private void checkAdmin(Long userId) {
        SecurityUtils.requireAuthority("platform:update");
    }

    /**
     * 创建课程平台
     */
    @Operation(summary = "创建课程平台", description = "添加新的课程平台")
    @PostMapping
    public Result<Long> createPlatform(@Valid @RequestBody CoursePlatform platform,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        Long id = coursePlatformService.createPlatform(platform);

        // 记录操作日志
        operationLogService.log(userId, "创建课程平台",
                "创建课程平台：" + platform.getName(), null, null);

        return Result.success("课程平台创建成功", id);
    }

    /**
     * 更新课程平台
     */
    @Operation(summary = "更新课程平台", description = "修改课程平台信息")
    @PutMapping
    public Result<Void> updatePlatform(@Valid @RequestBody CoursePlatform platform,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        coursePlatformService.updatePlatform(platform);

        // 记录操作日志
        operationLogService.log(userId, "更新课程平台",
                "更新课程平台：" + platform.getName(), null, null);

        return Result.success("课程平台更新成功");
    }

    /**
     * 删除课程平台
     */
    @Operation(summary = "删除课程平台", description = "删除指定课程平台")
    @DeleteMapping("/{id}")
    public Result<Void> deletePlatform(@PathVariable Long id,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        coursePlatformService.deletePlatform(id);

        // 记录操作日志
        operationLogService.log(userId, "删除课程平台",
                "删除课程平台ID：" + id, null, null);

        return Result.success("课程平台删除成功");
    }

    /**
     * 查询课程平台列表
     */
    @Operation(summary = "查询课程平台列表", description = "分页查询课程平台")
    @GetMapping
    public Result<IPage<CoursePlatform>> queryPlatforms(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) Long categoryId,
                                                          @RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        IPage<CoursePlatform> result = coursePlatformService.queryPlatforms(keyword, status, categoryId, page, pageSize);
        return Result.success(result);
    }
}
