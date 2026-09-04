package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.aqks.AqksLoginResult;
import com.course.platform.application.service.platform.AqksStudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AQKS自动刷课控制器
 *
 * 管理员接口，用于管理自营订单的自动刷课任务
 *
 * @author AI Assistant
 * @since 2025-12-20
 */
@Slf4j
@PreAuthorize("hasAuthority('order:update')")
@RestController
@RequestMapping("/admin/aqks")
@RequiredArgsConstructor
@Tag(name = "AQKS自动刷课管理", description = "管理自营订单的自动刷课任务")
public class AqksStudyController {

    private final AqksStudyService aqksStudyService;

    /**
     * 启动自动刷课任务
     */
    @PostMapping("/start/{orderId}")
    @Operation(summary = "启动自动刷课", description = "为指定订单启动自动刷课任务")
    public Result<String> startAutoStudy(
            @PathVariable @Parameter(description = "订单ID") Long orderId) {
        log.info("[AQKS控制器] 启动自动刷课: orderId={}", orderId);

        boolean success = aqksStudyService.startAutoStudy(orderId);
        if (success) {
            return Result.success("自动刷课任务已启动");
        } else {
            return Result.error("任务已在运行或学习已完成");
        }
    }

    /**
     * 停止自动刷课任务
     */
    @PostMapping("/stop/{orderId}")
    @Operation(summary = "停止自动刷课", description = "停止指定订单的自动刷课任务")
    public Result<String> stopAutoStudy(
            @PathVariable @Parameter(description = "订单ID") Long orderId) {
        log.info("[AQKS控制器] 停止自动刷课: orderId={}", orderId);

        boolean success = aqksStudyService.stopAutoStudy(orderId);
        if (success) {
            return Result.success("自动刷课任务已停止");
        } else {
            return Result.error("任务不存在或已停止");
        }
    }

    /**
     * 手动刷一次时长
     */
    @PostMapping("/add-time/{orderId}")
    @Operation(summary = "手动刷时长", description = "为指定订单手动增加学习时长（单位：秒）")
    public Result<String> addStudyTimeOnce(
            @PathVariable @Parameter(description = "订单ID") Long orderId,
            @RequestParam(defaultValue = "10") @Parameter(description = "增加的时长（秒），默认10秒") Integer seconds) {
        log.info("[AQKS控制器] 手动刷时长: orderId={}, seconds={}", orderId, seconds);

        boolean success = aqksStudyService.addStudyTimeOnce(orderId, seconds);
        if (success) {
            return Result.success("时长增加成功: +" + seconds + "秒");
        } else {
            return Result.error("刷时长失败");
        }
    }

    /**
     * 获取学习状态
     */
    @GetMapping("/status/{orderId}")
    @Operation(summary = "获取学习状态", description = "获取订单的实时学习状态")
    public Result<AqksLoginResult> getStudyStatus(
            @PathVariable @Parameter(description = "订单ID") Long orderId) {
        log.info("[AQKS控制器] 获取学习状态: orderId={}", orderId);

        AqksLoginResult status = aqksStudyService.getStudyStatus(orderId);
        return Result.success(status);
    }

    /**
     * 检查任务是否在运行
     */
    @GetMapping("/running/{orderId}")
    @Operation(summary = "检查任务状态", description = "检查自动刷课任务是否在运行")
    public Result<Boolean> isTaskRunning(
            @PathVariable @Parameter(description = "订单ID") Long orderId) {
        boolean running = aqksStudyService.isAutoStudyRunning(orderId);
        return Result.success(running);
    }

    /**
     * 批量检查任务运行状态（优化版）
     * Source: AURA-X-KYS - 批量查询优化，减少网络请求
     */
    @PostMapping("/running/batch")
    @Operation(summary = "批量检查任务状态", description = "批量检查多个订单的自动刷课任务运行状态")
    public Result<java.util.Map<Long, Boolean>> batchCheckRunningStatus(
            @RequestBody @Parameter(description = "订单ID列表") java.util.List<Long> orderIds) {
        log.info("[AQKS控制器] 批量检查运行状态: orderIds数量={}", orderIds.size());

        java.util.Map<Long, Boolean> result = new java.util.HashMap<>();
        for (Long orderId : orderIds) {
            boolean running = aqksStudyService.isAutoStudyRunning(orderId);
            result.put(orderId, running);
        }

        return Result.success(result);
    }

    /**
     * 获取运行中的任务数量
     */
    @GetMapping("/running-count")
    @Operation(summary = "获取运行任务数", description = "获取当前正在运行的自动刷课任务数量")
    public Result<Integer> getRunningTaskCount() {
        int count = aqksStudyService.getRunningTaskCount();
        return Result.success(count);
    }

    /**
     * 获取AQKS统计数据
     * 返回运行中任务数、待考试订单数、已完成订单数、自营订单总数
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取AQKS统计", description = "获取自营订单的统计数据")
    public Result<java.util.Map<String, Integer>> getStatistics() {
        log.info("[AQKS控制器] 获取统计数据");

        java.util.Map<String, Integer> stats = aqksStudyService.getAqksStatistics();
        return Result.success(stats);
    }

    /**
     * 检查单个订单的考试状态
     *
     * 手动触发检查指定订单的考试成绩，
     * 并根据结果自动更新订单状态和备注
     */
    @PostMapping("/check-exam/{orderId}")
    @Operation(summary = "检查考试状态", description = "检查指定订单的考试状态并更新")
    public Result<com.course.platform.domain.dto.aqks.AqksExamInfo> checkExamStatus(
            @PathVariable @Parameter(description = "订单ID") Long orderId) {
        log.info("[AQKS控制器] 检查考试状态: orderId={}", orderId);

        com.course.platform.domain.dto.aqks.AqksExamInfo examInfo =
                aqksStudyService.checkAndUpdateExamStatus(orderId);

        if (examInfo != null) {
            return Result.success(examInfo);
        } else {
            return Result.error("未获取到考试信息");
        }
    }

    /**
     * 批量同步考试状态
     *
     * 手动触发批量同步所有待考试/考试中订单的考试状态
     */
    @PostMapping("/sync-exam-status")
    @Operation(summary = "批量同步考试状态", description = "同步所有待考试/考试中订单的考试状态")
    public Result<java.util.Map<String, Object>> syncExamStatus() {
        log.info("[AQKS控制器] 批量同步考试状态");

        java.util.Map<String, Object> result = aqksStudyService.syncExamStatusForPendingOrders();
        return Result.success(result);
    }
}
