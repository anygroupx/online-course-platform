package com.course.platform.controller;

import com.course.platform.application.service.system.SystemVariableService;
import com.course.platform.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客户端主题变量接口。
 * 仅暴露白名单内且已启用的主题色值，不包含系统变量管理能力。
 */
@Tag(name = "客户端主题", description = "读取系统统一配置的主题颜色")
@RequestMapping("/theme")
@RequiredArgsConstructor
@RestController
public class ThemeVariableController {

    private final SystemVariableService systemVariableService;

    @Operation(summary = "获取主题颜色", description = "获取浅色与深色主题中已启用的语义颜色变量")
    @GetMapping("/variables")
    public Result<Map<String, Map<String, String>>> getThemeVariables() {
        return Result.success(systemVariableService.getEnabledThemeVariables());
    }
}
