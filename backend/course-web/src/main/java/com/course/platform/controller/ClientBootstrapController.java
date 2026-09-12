package com.course.platform.controller;

import com.course.platform.application.service.system.SystemConfigService;
import com.course.platform.common.constant.SystemConfigDefaults;
import com.course.platform.common.result.Result;
import com.course.platform.domain.vo.ClientBootstrapVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端启动配置接口。
 *
 * <p>使用固定字段白名单，禁止直接返回系统配置实体或完整配置列表。</p>
 */
@Tag(name = "客户端配置", description = "读取客户端启动所需的公开配置")
@RequestMapping("/client")
@RequiredArgsConstructor
@RestController
public class ClientBootstrapController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "获取客户端启动配置", description = "获取公开品牌信息和会话刷新开关")
    @GetMapping("/bootstrap")
    public Result<ClientBootstrapVO> bootstrap() {
        ClientBootstrapVO.Branding branding = new ClientBootstrapVO.Branding(
                getValueOrDefault("site_name"),
                getValueOrDefault("site_keywords"),
                getValueOrDefault("site_description")
        );
        ClientBootstrapVO.Session session = new ClientBootstrapVO.Session(
                systemConfigService.getConfigValueAsBoolean("auto_refresh_token_enabled", true)
        );
        return Result.success(new ClientBootstrapVO(branding, session));
    }

    private String getValueOrDefault(String configKey) {
        String value = systemConfigService.getConfigValue(configKey);
        return value != null ? value : SystemConfigDefaults.getDefaultValue(configKey);
    }
}
