package com.course.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.SecurityUtils;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.SystemVariableCreateRequest;
import com.course.platform.domain.dto.SystemVariableUpdateRequest;
import com.course.platform.domain.entity.SystemVariable;
import com.course.platform.application.service.system.SystemVariableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统变量管理控制器
 * 提供系统变量的增删改查功能
 *
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于系统变量管理需求设计
 */
@Tag(name = "系统变量管理", description = "系统变量配置管理接口")
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/variables")
@RequiredArgsConstructor
@RestController
public class SystemVariableController {

    private final SystemVariableService systemVariableService;

    /**
     * 验证管理员权限
     */
    private void checkAdmin(Long userId) {
        SecurityUtils.requireAdmin();
    }

    /**
     * 创建系统变量
     */
    @Operation(summary = "创建系统变量", description = "创建新的系统变量配置")
    @PostMapping
    public Result<Long> createVariable(@Validated @RequestBody SystemVariableCreateRequest request,
                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        Long variableId = systemVariableService.createVariable(request, userId);
        return Result.success(variableId);
    }

    /**
     * 更新系统变量
     */
    @Operation(summary = "更新系统变量", description = "更新系统变量配置")
    @PutMapping
    public Result<Void> updateVariable(@Validated @RequestBody SystemVariableUpdateRequest request,
                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        systemVariableService.updateVariable(request, userId);
        return Result.success("系统变量更新成功");
    }

    /**
     * 删除系统变量
     */
    @Operation(summary = "删除系统变量", description = "删除系统变量配置")
    @DeleteMapping("/{variableId}")
    public Result<Void> deleteVariable(@PathVariable Long variableId,
                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        systemVariableService.deleteVariable(variableId, userId);
        return Result.success("系统变量删除成功");
    }

    /**
     * 根据类型查询变量列表
     */
    @Operation(summary = "根据类型查询变量", description = "根据变量类型查询变量列表")
    @GetMapping("/type/{variableType}")
    public Result<List<SystemVariable>> getVariablesByType(@PathVariable String variableType) {
        List<SystemVariable> variables = systemVariableService.getVariablesByType(variableType);
        return Result.success(variables);
    }

    /**
     * 分页查询变量
     */
    @Operation(summary = "分页查询变量", description = "分页查询系统变量")
    @GetMapping
    public Result<IPage<SystemVariable>> queryVariables(@RequestParam(required = false) String variableType,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        IPage<SystemVariable> result = systemVariableService.queryVariables(variableType, page, pageSize);
        return Result.success(result);
    }

    /**
     * 获取变量详情
     */
    @Operation(summary = "获取变量详情", description = "根据ID获取变量详情")
    @GetMapping("/{variableId}")
    public Result<SystemVariable> getVariableById(@PathVariable Long variableId) {
        SystemVariable variable = systemVariableService.getVariableById(variableId);
        return Result.success(variable);
    }

    /**
     * 启用/禁用变量
     */
    @Operation(summary = "切换变量状态", description = "启用或禁用系统变量")
    @PostMapping("/{variableId}/toggle")
    public Result<Void> toggleVariableStatus(@PathVariable Long variableId,
                                             @RequestParam Boolean enabled,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        systemVariableService.toggleVariableStatus(variableId, enabled, userId);
        return Result.success(enabled ? "变量启用成功" : "变量禁用成功");
    }

    /**
     * 设置默认变量
     */
    @Operation(summary = "设置默认变量", description = "设置指定变量为默认值")
    @PostMapping("/{variableId}/set-default")
    public Result<Void> setDefaultVariable(@PathVariable Long variableId,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        systemVariableService.setDefaultVariable(variableId, userId);
        return Result.success("默认变量设置成功");
    }

    /**
     * 获取所有变量类型
     */
    @Operation(summary = "获取变量类型列表", description = "获取系统中所有变量类型")
    @GetMapping("/types")
    public Result<List<String>> getVariableTypes() {
        // 这里可以从数据库查询所有不同的变量类型
        List<String> types = List.of(
            "order_status", "dock_status", "user_status", "platform_status",
            "card_status", "announcement_type", "session_status", "message_type"
        );
        return Result.success(types);
    }
}