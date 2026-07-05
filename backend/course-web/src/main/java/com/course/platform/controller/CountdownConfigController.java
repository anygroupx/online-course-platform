package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.CountdownConfig;
import com.course.platform.application.service.course.CountdownConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 倒计时配置控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@RestController
@RequestMapping("/admin/countdown-config")
@RequiredArgsConstructor
public class CountdownConfigController {

    private final CountdownConfigService countdownConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping("/all")
    public Result<Map<String, String>> getAllConfigs() {
        try {
            Map<String, String> configs = countdownConfigService.getAllConfigs();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取倒计时配置失败", e);
            return Result.error("获取配置失败");
        }
    }

    /**
     * 获取配置列表
     */
    @GetMapping("/list")
    public Result<List<CountdownConfig>> getConfigList() {
        try {
            List<CountdownConfig> configs = countdownConfigService.getAllConfigList();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取倒计时配置列表失败", e);
            return Result.error("获取配置列表失败");
        }
    }

    /**
     * 更新配置
     */
    @PostMapping("/update")
    public Result<Void> updateConfig(@RequestBody CountdownConfig config) {
        try {
            countdownConfigService.updateConfig(config);
            return Result.success();
        } catch (Exception e) {
            log.error("更新倒计时配置失败", e);
            return Result.error("更新配置失败");
        }
    }

    /**
     * 批量更新配置
     */
    @PostMapping("/batch-update")
    public Result<Void> batchUpdateConfigs(@RequestBody Map<String, String> configs) {
        try {
            countdownConfigService.setConfigValues(configs);
            return Result.success();
        } catch (Exception e) {
            log.error("批量更新倒计时配置失败", e);
            return Result.error("批量更新配置失败");
        }
    }

    /**
     * 重置为默认配置
     */
    @PostMapping("/reset")
    public Result<Void> resetToDefault() {
        try {
            countdownConfigService.resetToDefault();
            return Result.success();
        } catch (Exception e) {
            log.error("重置倒计时配置失败", e);
            return Result.error("重置配置失败");
        }
    }

    /**
     * 获取考试倒计时配置
     */
    @GetMapping("/exam-configs")
    public Result<Map<String, String>> getExamConfigs() {
        try {
            Map<String, String> configs = countdownConfigService.getExamCountdownConfigs();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取考试倒计时配置失败", e);
            return Result.error("获取考试配置失败");
        }
    }

    /**
     * 更新考试倒计时配置
     */
    @PostMapping("/exam-configs")
    public Result<Void> updateExamConfigs(@RequestBody Map<String, String> configs) {
        try {
            countdownConfigService.updateExamCountdownConfigs(configs);
            return Result.success();
        } catch (Exception e) {
            log.error("更新考试倒计时配置失败", e);
            return Result.error("更新考试配置失败");
        }
    }
}
