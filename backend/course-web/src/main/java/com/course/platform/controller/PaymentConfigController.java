package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.PaymentConfig;
import com.course.platform.application.service.payment.PaymentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付配置管理控制器(仅管理员)
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Slf4j
@Tag(name = "支付配置管理", description = "支付配置管理接口(仅管理员)")
@RestController
@RequestMapping("/payment/config")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentConfigController {

    @Autowired
    private PaymentConfigService paymentConfigService;

    @Operation(summary = "获取配置列表", description = "获取所有支付配置列表")
    @GetMapping
    public Result<List<PaymentConfig>> getAllConfigs() {
        List<PaymentConfig> configs = paymentConfigService.getAllConfigs();
        
        // 脱敏处理 - 不返回完整私钥
        configs.forEach(config -> {
            if (config.getPrivateKey() != null && config.getPrivateKey().length() > 20) {
                config.setPrivateKey(config.getPrivateKey().substring(0, 20) + "...(已隐藏)");
            }
        });
        
        return Result.success(configs);
    }

    @Operation(summary = "获取配置详情", description = "根据ID获取配置详情(包含完整私钥)")
    @GetMapping("/{id}")
    public Result<PaymentConfig> getConfig(@PathVariable Long id) {
        PaymentConfig config = paymentConfigService.getById(id);
        
        if (config == null) {
            return Result.error("配置不存在");
        }
        
        return Result.success(config);
    }

    @Operation(summary = "创建配置", description = "创建新的支付配置")
    @PostMapping
    public Result<String> createConfig(@Valid @RequestBody PaymentConfig config) {
        boolean success = paymentConfigService.create(config);
        
        if (success) {
            return Result.success("创建成功");
        } else {
            return Result.error("创建失败");
        }
    }

    @Operation(summary = "更新配置", description = "更新支付配置")
    @PutMapping("/{id}")
    public Result<String> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody PaymentConfig config) {
        
        config.setId(id);
        boolean success = paymentConfigService.update(config);
        
        if (success) {
            return Result.success("更新成功");
        } else {
            return Result.error("更新失败");
        }
    }

    @Operation(summary = "激活配置", description = "激活指定的支付配置")
    @PutMapping("/{id}/activate")
    public Result<String> activateConfig(@PathVariable Long id) {
        boolean success = paymentConfigService.activate(id);
        
        if (success) {
            return Result.success("激活成功");
        } else {
            return Result.error("激活失败");
        }
    }

    @Operation(summary = "删除配置", description = "删除支付配置")
    @DeleteMapping("/{id}")
    public Result<String> deleteConfig(@PathVariable Long id) {
        boolean success = paymentConfigService.delete(id);
        
        if (success) {
            return Result.success("删除成功");
        } else {
            return Result.error("删除失败");
        }
    }
}
