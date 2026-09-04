package com.course.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.SecurityUtils;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.SystemConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "系统配置", description = "系统参数配置接口")
@PreAuthorize("hasAuthority('system-config:update')")
@RequestMapping("/system/config")
@RequiredArgsConstructor
@RestController
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取所有配置
     */
    @Operation(summary = "获取所有配置", description = "获取系统所有配置项")
    @GetMapping
    public Result<List<SystemConfig>> getAllConfigs() {
        List<SystemConfig> configs = systemConfigService.getAllConfigs();
        return Result.success(configs);
    }

    /**
     * 更新配置
     */
    @Operation(summary = "更新配置", description = "批量更新系统配置")
    @PutMapping
    public Result<Void> updateConfigs(@RequestBody Map<String, String> configs,
                                       Authentication authentication) {
        requireAdmin(authentication);
        systemConfigService.updateConfigs(configs);
        return Result.success("配置更新成功");
    }

    /**
     * 重置单个配置为默认值
     */
    @Operation(summary = "重置单个配置", description = "将指定配置项恢复为系统默认值")
    @PostMapping("/reset/{configKey}")
    public Result<Void> resetConfig(@PathVariable String configKey,
                                    Authentication authentication) {
        requireAdmin(authentication);
        systemConfigService.resetConfig(configKey);
        return Result.success("配置重置成功");
    }

    /**
     * 重置全部默认配置
     */
    @Operation(summary = "重置全部配置", description = "将全部已知系统配置恢复为默认值")
    @PostMapping("/reset-all")
    public Result<Map<String, Object>> resetAllConfigs(Authentication authentication) {
        requireAdmin(authentication);
        int count = systemConfigService.resetAllConfigs();
        Map<String, Object> data = new HashMap<>(2);
        data.put("count", count);
        return Result.success("全部配置已重置为默认值", data);
    }

    private void requireAdmin(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SecurityUtils.requireAuthority("system-config:update");
    }
}