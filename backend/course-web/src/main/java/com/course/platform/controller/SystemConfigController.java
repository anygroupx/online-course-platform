package com.course.platform.controller;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.result.Result;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器。
 */
@Tag(name = "系统配置", description = "系统参数配置接口")
@RequestMapping("/system/config")
@RequiredArgsConstructor
@RestController
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取所有配置。
     */
    @Operation(summary = "获取所有配置", description = "获取系统所有配置项")
    @PreAuthorize("hasAuthority('system-config:read')")
    @GetMapping
    public Result<List<SystemConfig>> getAllConfigs() {
        SecurityUtils.requireAuthority(SecurityAuthorities.SYSTEM_CONFIG_READ);
        List<SystemConfig> configs = systemConfigService.getAllConfigs();
        return Result.success(configs);
    }

    /**
     * 更新配置。
     */
    @Operation(summary = "更新配置", description = "批量更新系统配置")
    @PreAuthorize("hasAuthority('system-config:update')")
    @PutMapping
    public Result<Void> updateConfigs(@RequestBody Map<String, String> configs) {
        SecurityUtils.requireAuthority(SecurityAuthorities.SYSTEM_CONFIG_UPDATE);
        systemConfigService.updateConfigs(configs);
        return Result.success("配置更新成功");
    }

    /**
     * 重置单个配置为默认值。
     */
    @Operation(summary = "重置单个配置", description = "将指定配置项恢复为系统默认值")
    @PreAuthorize("hasAuthority('system-config:update')")
    @PostMapping("/reset/{configKey}")
    public Result<Void> resetConfig(@PathVariable String configKey) {
        SecurityUtils.requireAuthority(SecurityAuthorities.SYSTEM_CONFIG_UPDATE);
        systemConfigService.resetConfig(configKey);
        return Result.success("配置重置成功");
    }

    /**
     * 重置全部默认配置。
     */
    @Operation(summary = "重置全部配置", description = "将全部已知系统配置恢复为默认值")
    @PreAuthorize("hasAuthority('system-config:update')")
    @PostMapping("/reset-all")
    public Result<Map<String, Object>> resetAllConfigs() {
        SecurityUtils.requireAuthority(SecurityAuthorities.SYSTEM_CONFIG_UPDATE);
        int count = systemConfigService.resetAllConfigs();
        Map<String, Object> data = new HashMap<>(2);
        data.put("count", count);
        return Result.success("全部配置已重置为默认值", data);
    }
}
