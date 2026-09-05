package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.dto.ApiProviderSaveRequest;
import com.course.platform.domain.dto.ApiProviderStatusRequest;
import com.course.platform.domain.vo.ProviderConnectionTestResult;
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

import java.math.BigDecimal;

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
    private final PlatformDockingService platformDockingService;

    private void checkAdmin(Long userId) {
        SecurityUtils.requireAuthority("api-provider:update");
    }

    @Operation(summary = "创建API接口", description = "添加新的第三方API接口")
    @PostMapping
    public Result<Long> createApiProvider(@Valid @RequestBody ApiProviderSaveRequest request,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        ApiProvider apiProvider = request.toProvider();
        Long id = apiProviderService.createApiProvider(apiProvider);
        operationLogService.log(userId, "创建API接口",
                "创建API接口：" + apiProvider.getName(), null, null);
        return Result.success("API接口已保存，请测试连接后启用", id);
    }

    @Operation(summary = "更新API接口", description = "修改API接口信息")
    @PutMapping
    public Result<Void> updateApiProvider(@Valid @RequestBody ApiProviderSaveRequest request,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        ApiProvider apiProvider = request.toProvider();
        apiProviderService.updateApiProvider(apiProvider);
        operationLogService.log(userId, "更新API接口",
                "更新API接口：" + apiProvider.getName(), null, null);
        return Result.success("API接口更新成功");
    }

    @Operation(summary = "测试连接", description = "使用保存的凭据执行只读余额/商品查询，不自动启用接口")
    @PostMapping("/{id}/test-connection")
    public Result<ProviderConnectionTestResult> testConnection(@PathVariable Long id,
                                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        ProviderConnectionTestResult result = apiProviderService.testConnection(id, userId);
        operationLogService.log(userId, "测试API接口连接", "API接口ID：" + id, null, null);
        return Result.success("连接测试通过，可启用接口", result);
    }

    @Operation(summary = "启用或禁用接口", description = "启用要求当前配置已通过连接测试；禁用不访问第三方")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody ApiProviderStatusRequest request,
                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        apiProviderService.updateStatus(id, request.status());
        operationLogService.log(userId, "更新API接口状态",
                "API接口ID：" + id + "，状态：" + request.status(), null, null);
        return Result.success(request.status() == ApiProvider.STATUS_ACTIVE ? "接口已启用" : "接口已禁用");
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

    @Operation(summary = "查询接口余额", description = "调用第三方余额接口并保存最新余额")
    @PostMapping("/{id}/balance")
    public Result<BigDecimal> refreshBalance(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);
        BigDecimal balance = platformDockingService.refreshProviderBalance(id);
        operationLogService.log(userId, "查询API接口余额",
                "查询API接口余额：ID=" + id + "，余额=" + balance, null, null);
        return Result.success("余额查询成功", balance);
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
