package com.course.platform.controller;

import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.SystemConfig;
import com.course.platform.application.service.system.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "系统配置", description = "系统参数配置接口")
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
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
        Long userId = (Long) authentication.getPrincipal();

        // 只有管理员可以修改配置
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        systemConfigService.updateConfigs(configs);
        return Result.success("配置更新成功");
    }
}

