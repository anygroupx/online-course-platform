package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.application.service.integration.PluginIntegrationService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.vo.plugin.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "插件集成", description = "研究矩阵与经审核的只读连接器；不支持安装、下单或资金操作")
@RestController
@RequestMapping("/admin/plugin-integrations")
@PreAuthorize("hasAuthority('api-provider:update')")
@RequiredArgsConstructor
public class PluginIntegrationController {
    private final PluginIntegrationService service;

    @GetMapping
    @Operation(summary = "查看全部插件研究与已实现能力")
    public ResponseEntity<Result<List<PluginIntegrationDescriptor>>> list() {
        return readOnly(service.listIntegrations());
    }

    @GetMapping("/{pluginId}/providers")
    @Operation(summary = "分页列出此插件类型的接口配置摘要")
    public ResponseEntity<Result<IPage<PluginProviderOption>>> providers(@PathVariable String pluginId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return readOnly(service.listProviders(pluginId, new PluginPageQuery(page, pageSize, keyword)));
    }

    @GetMapping("/{pluginId}/providers/{providerId}/catalog")
    @Operation(summary = "从已验证启用的接口只读查询商品或所选项目报价")
    public ResponseEntity<Result<List<PluginProduct>>> catalog(@PathVariable String pluginId,
            @PathVariable Long providerId, @RequestParam(required = false) String project) {
        return readOnly(service.fetchCatalog(pluginId, providerId, project));
    }

    @GetMapping("/{pluginId}/providers/{providerId}/schools")
    @Operation(summary = "极光学校只读检索；不查询或提交学生信息")
    public ResponseEntity<Result<PluginSchoolPage>> schools(@PathVariable String pluginId,
            @PathVariable Long providerId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword) {
        return readOnly(service.searchSchools(pluginId, providerId, new PluginPageQuery(page, pageSize, keyword)));
    }

    private <T> ResponseEntity<Result<T>> readOnly(T result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(result));
    }
}
