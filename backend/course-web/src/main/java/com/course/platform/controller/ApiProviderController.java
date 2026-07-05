package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.support.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 第三方API接口管理控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "API接口管理", description = "第三方API接口配置管理（管理员）")
@RestController
@RequestMapping("/admin/api-providers")
@RequiredArgsConstructor
public class ApiProviderController {

    private final ApiProviderService apiProviderService;
    private final OperationLogService operationLogService;

    /**
     * 验证管理员权限
     */
    private void checkAdmin(Long userId) {
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 创建API接口
     */
    @Operation(summary = "创建API接口", description = "添加新的第三方API接口")
    @PostMapping
    public Result<Long> createApiProvider(@Valid @RequestBody ApiProvider apiProvider,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        Long id = apiProviderService.createApiProvider(apiProvider);
        
        // 记录操作日志
        operationLogService.log(userId, "创建API接口", 
                "创建API接口：" + apiProvider.getName(), null, null);
        
        return Result.success("API接口创建成功", id);
    }

    /**
     * 更新API接口
     */
    @Operation(summary = "更新API接口", description = "修改API接口信息")
    @PutMapping
    public Result<Void> updateApiProvider(@Valid @RequestBody ApiProvider apiProvider,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        apiProviderService.updateApiProvider(apiProvider);
        
        // 记录操作日志
        operationLogService.log(userId, "更新API接口", 
                "更新API接口：" + apiProvider.getName(), null, null);
        
        return Result.success("API接口更新成功");
    }

    /**
     * 删除API接口
     */
    @Operation(summary = "删除API接口", description = "删除指定API接口")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApiProvider(@PathVariable Long id,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        apiProviderService.deleteApiProvider(id);
        
        // 记录操作日志
        operationLogService.log(userId, "删除API接口", 
                "删除API接口ID：" + id, null, null);
        
        return Result.success("API接口删除成功");
    }

    /**
     * 查询API接口列表
     */
    @Operation(summary = "查询API接口列表", description = "分页查询API接口")
    @GetMapping
    public Result<IPage<ApiProvider>> queryApiProviders(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        IPage<ApiProvider> result = apiProviderService.queryApiProviders(keyword, status, page, pageSize);
        return Result.success(result);
    }
}

