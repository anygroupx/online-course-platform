package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.vo.ApiProviderVO;
import com.course.platform.security.SecurityUtils;
import com.course.platform.security.SensitiveDataMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 第三方API接口管理控制器
 */
@Tag(name = "API接口管理", description = "第三方API接口配置管理（管理员）")
@PreAuthorize("hasAuthority('api-provider:update')")
@RestController
@RequestMapping("/admin/api-providers")
@RequiredArgsConstructor
public class ApiProviderController {

    private final ApiProviderService apiProviderService;
    private final OperationLogService operationLogService;

    private void checkAdmin(Long userId) {
        SecurityUtils.requireAuthority("api-provider:update");
    }

    @Operation(summary = "创建API接口", description = "添加新的第三方API接口")
    @PostMapping
    public Result<Long> createApiProvider(@Valid @RequestBody ApiProvider apiProvider,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        Long id = apiProviderService.createApiProvider(apiProvider);
        operationLogService.log(userId, "创建API接口",
                "创建API接口：" + apiProvider.getName(), null, null);
        return Result.success("API接口创建成功", id);
    }

    @Operation(summary = "更新API接口", description = "修改API接口信息")
    @PutMapping
    public Result<Void> updateApiProvider(@Valid @RequestBody ApiProvider apiProvider,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        apiProviderService.updateApiProvider(apiProvider);
        operationLogService.log(userId, "更新API接口",
                "更新API接口：" + apiProvider.getName(), null, null);
        return Result.success("API接口更新成功");
    }

    @Operation(summary = "删除API接口", description = "删除指定API接口")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApiProvider(@PathVariable Long id,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        apiProviderService.deleteApiProvider(id);
        operationLogService.log(userId, "删除API接口",
                "删除API接口ID：" + id, null, null);
        return Result.success("API接口删除成功");
    }

    @Operation(summary = "查询API接口列表", description = "分页查询API接口")
    @GetMapping
    public Result<IPage<ApiProviderVO>> queryApiProviders(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        IPage<ApiProvider> result = apiProviderService.queryApiProviders(keyword, status, page, pageSize);
        Page<ApiProviderVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(SensitiveDataMasker::toApiProviderVO).toList());
        return Result.success(voPage);
    }
}
