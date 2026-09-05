package com.course.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.course.platform.common.result.Result;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.ProductImportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 平台对接管理控制器
 */
@Slf4j
@Tag(name = "平台对接管理", description = "平台对接相关的管理接口")
@RestController
@RequestMapping("/admin/docking")
@RequiredArgsConstructor
public class PlatformDockingController {

    private final PlatformDockingService platformDockingService;

    @Operation(summary = "查询第三方商品列表", description = "调用商品列表接口，可按远程分类ID筛选，并标记已导入商品")
    @PreAuthorize("hasAuthority('platform:read')")
    @GetMapping("/products")
    public Result<List<PlatformItem>> fetchProducts(@RequestParam Long apiProviderId,
                                                     @RequestParam(required = false) String categoryId) {
        return Result.success(platformDockingService.fetchProviderProducts(apiProviderId, categoryId));
    }

    @Operation(summary = "选择商品导入", description = "后端重新查询第三方商品数据后，仅导入所选商品")
    @PreAuthorize("hasAuthority('platform:update')")
    @PostMapping("/import-products")
    public Result<Map<String, Object>> importSelectedProducts(@Valid @RequestBody ProductImportRequest request) {
        return Result.success("导入完成", platformDockingService.importSelectedProducts(request));
    }

    @Operation(summary = "一键导入平台/课程", description = "从第三方API导入平台/课程列表")
    @PreAuthorize("hasAuthority('platform:update')")
    @PostMapping("/import-platforms")
    public Result<Map<String, Object>> importPlatforms(
            @RequestParam Long apiProviderId,
            @RequestParam(defaultValue = "1.0") BigDecimal priceMultiplier,
            @RequestParam(required = false) String targetCategoryId,
            @RequestParam(defaultValue = "true") Boolean syncCategories,
            @RequestParam(required = false) List<String> skipCategoryIds) {
        
        Map<String, Object> result = platformDockingService.importPlatforms(
            apiProviderId, priceMultiplier, targetCategoryId, syncCategories, skipCategoryIds);
        return Result.success("导入完成", result);
    }

    @Operation(summary = "批量同步订单进度", description = "从第三方API批量同步订单进度（增量）")
    @PreAuthorize("hasAuthority('order:update')")
    @PostMapping("/batch-sync")
    public Result<Map<String, Object>> batchSyncOrderProgress(
            @RequestParam Long apiProviderId,
            @RequestParam(required = false) Long timestampSeconds,
            @RequestParam(defaultValue = "0") Integer offset) {
        
        Map<String, Object> result = platformDockingService.batchSyncOrderProgress(
            apiProviderId, timestampSeconds, offset);
        return Result.success("同步完成", result);
    }
}
