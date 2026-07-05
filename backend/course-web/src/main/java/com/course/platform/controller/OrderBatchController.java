package com.course.platform.controller;

import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.BatchOrderOperationRequest;
import com.course.platform.domain.dto.BatchToggleStatusRequest;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.course.CountdownConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 订单批量操作控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Tag(name = "订单批量操作", description = "批量修改订单状态等操作（管理员）")
@RestController
@RequestMapping("/admin/orders/batch")
@RequiredArgsConstructor
public class OrderBatchController {

    private final CourseOrderMapper courseOrderMapper;
    private final OperationLogService operationLogService;
    private final CountdownConfigService countdownConfigService;

    /**
     * 验证管理员权限
     */
    private void checkAdmin(Long userId) {
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 批量修改订单状态
     */
    @Operation(summary = "批量修改订单状态", description = "管理员批量修改订单状态")
    @PostMapping("/update-order-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchUpdateOrderStatus(@RequestBody BatchOrderOperationRequest request,
                                                 Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        for (Long orderId : request.getOrderIds()) {
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order != null) {
                order.setOrderStatus(request.getStatus());
                courseOrderMapper.updateById(order);
                successCount++;
            }
        }

        // 记录操作日志
        String operationDesc = String.format("批量修改订单状态：%s，成功：%d/%d，原因：%s", 
                getStatusText(request.getStatus()), successCount, request.getOrderIds().size(), 
                request.getReason() != null ? request.getReason() : "无");
        operationLogService.log(userId, "批量修改订单状态", operationDesc,
                BigDecimal.ZERO, null);

        log.info("管理员批量修改订单状态：orderIds={}, status={}, successCount={}, reason={}, operatorId={}", 
                request.getOrderIds(), request.getStatus(), successCount, request.getReason(), userId);

        return Result.success(String.format("批量更新成功，处理了 %d/%d 个订单", successCount, request.getOrderIds().size()));
    }

    /**
     * 批量修改对接状态
     */
    @Operation(summary = "批量修改对接状态", description = "管理员批量修改对接状态")
    @PostMapping("/update-dock-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchUpdateDockStatus(@RequestBody BatchOrderOperationRequest request,
                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        for (Long orderId : request.getOrderIds()) {
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order != null) {
                order.setDockStatus(request.getDockStatus());
                courseOrderMapper.updateById(order);
                successCount++;
            }
        }

        // 记录操作日志
        String operationDesc = String.format("批量修改对接状态：%s，成功：%d/%d，原因：%s", 
                getDockStatusText(request.getDockStatus()), successCount, request.getOrderIds().size(), 
                request.getReason() != null ? request.getReason() : "无");
        operationLogService.log(userId, "批量修改对接状态", operationDesc,
                BigDecimal.ZERO, null);

        log.info("管理员批量修改对接状态：orderIds={}, status={}, successCount={}, reason={}, operatorId={}", 
                request.getOrderIds(), request.getDockStatus(), successCount, request.getReason(), userId);

        return Result.success(String.format("批量更新成功，处理了 %d/%d 个订单", successCount, request.getOrderIds().size()));
    }

    /**
     * 批量添加备注
     */
    @Operation(summary = "批量添加备注", description = "管理员为多个订单批量添加备注")
    @PostMapping("/add-remarks")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchAddRemarks(@RequestBody BatchOrderOperationRequest request,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        for (Long orderId : request.getOrderIds()) {
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order != null) {
                String oldRemark = order.getRemarks();
                String newRemark = oldRemark != null ? 
                        oldRemark + "\n[" + timestamp + "] " + request.getRemark() : 
                        "[" + timestamp + "] " + request.getRemark();
                order.setRemarks(newRemark);
                courseOrderMapper.updateById(order);
                successCount++;
            }
        }

        // 记录操作日志
        operationLogService.log(userId, "批量添加备注", 
                String.format("为 %d/%d 个订单批量添加备注：%s", successCount, request.getOrderIds().size(), request.getRemark()),
                BigDecimal.ZERO, null);

        return Result.success(String.format("批量添加备注成功，处理了 %d/%d 个订单", successCount, request.getOrderIds().size()));
    }

    /**
     * 批量补单
     */
    @Operation(summary = "批量补单", description = "管理员批量执行补单操作")
    @PostMapping("/retry-orders")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchRetryOrders(@RequestBody BatchOrderOperationRequest request,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        for (Long orderId : request.getOrderIds()) {
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order != null && order.getRetryCount() < 5) {
                // 重置订单状态为待处理
                order.setOrderStatus(SystemVariableCache.getStatusValue("order_status", "pending"));
                order.setDockStatus(SystemVariableCache.getStatusValue("dock_status", "pending"));
                order.setRetryCount(order.getRetryCount() + 1);
                courseOrderMapper.updateById(order);
                successCount++;
            }
        }

        // 记录操作日志
        String operationDesc = String.format("批量补单：成功：%d/%d，原因：%s", 
                successCount, request.getOrderIds().size(), request.getReason() != null ? request.getReason() : "无");
        operationLogService.log(userId, "批量补单", operationDesc,
                BigDecimal.ZERO, null);

        return Result.success(String.format("批量补单成功，处理了 %d/%d 个订单", successCount, request.getOrderIds().size()));
    }

    /**
     * 批量状态切换（支持倒计时）
     */
    @Operation(summary = "批量状态切换", description = "管理员批量切换订单状态，支持倒计时配置")
    @PostMapping("/toggle-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchToggleStatus(@RequestBody BatchToggleStatusRequest request,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        int selfOperatedCount = 0;
        int countdownStartedCount = 0;
        
        for (Long orderId : request.getOrderIds()) {
            try {
                CourseOrder order = courseOrderMapper.selectById(orderId);
                if (order != null) {
                    Integer oldStatus = order.getOrderStatus();
                    order.setOrderStatus(request.getNewStatus());
                    
                    // 如果是自营订单且切换到进行中状态，启动倒计时
                    if (order.getIsSelfOperated() != null && order.getIsSelfOperated() == 1 && 
                        request.getNewStatus() == SystemVariableCache.getStatusValue("order_status", "processing")) {
                        selfOperatedCount++;
                        
                        // 设置倒计时参数
                        Integer countdownDuration = request.getCountdownDuration();
                        if (countdownDuration == null || countdownDuration <= 0) {
                            countdownDuration = countdownConfigService.getIntConfigValue("default_countdown_duration", 60);
                        }
                        
                        LocalDateTime now = LocalDateTime.now();
                        order.setCountdownDuration(countdownDuration);
                        order.setCountdownStartTime(now);
                        order.setCountdownEndTime(now.plusMinutes(countdownDuration));
                        order.setAutoCompleteEnabled(request.getAutoComplete() != null && request.getAutoComplete() ? 1 : 0);
                        
                        countdownStartedCount++;
                        log.info("批量状态切换启动倒计时：orderId={}, duration={}分钟, autoComplete={}", 
                                orderId, countdownDuration, request.getAutoComplete());
                    } else if (request.getNewStatus() != SystemVariableCache.getStatusValue("order_status", "processing")) {
                        // 其他状态时清除倒计时
                        order.setCountdownDuration(null);
                        order.setCountdownStartTime(null);
                        order.setCountdownEndTime(null);
                        order.setAutoCompleteEnabled(0);
                    }
                    
                    courseOrderMapper.updateById(order);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量状态切换失败：orderId={}, error={}", orderId, e.getMessage(), e);
            }
        }

        // 记录操作日志
        String operationDesc = String.format("批量状态切换：%s，成功：%d/%d，自营订单：%d，启动倒计时：%d，原因：%s", 
                getStatusText(request.getNewStatus()), successCount, request.getOrderIds().size(), 
                selfOperatedCount, countdownStartedCount, request.getReason() != null ? request.getReason() : "无");
        operationLogService.log(userId, "批量状态切换", operationDesc, BigDecimal.ZERO, null);

        log.info("管理员批量状态切换：orderIds={}, newStatus={}, successCount={}, selfOperatedCount={}, countdownStartedCount={}, reason={}, operatorId={}", 
                request.getOrderIds(), request.getNewStatus(), successCount, selfOperatedCount, countdownStartedCount, request.getReason(), userId);

        return Result.success(String.format("批量状态切换成功，处理了 %d/%d 个订单，其中 %d 个自营订单启动了倒计时", 
                successCount, request.getOrderIds().size(), countdownStartedCount));
    }

    /**
     * 获取订单状态文本
     * Source: 使用SystemVariableCache动态获取状态名称
     */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return SystemVariableCache.getStatusName("order_status", String.valueOf(status));
    }

    /**
     * 获取对接状态文本
     * Source: 使用SystemVariableCache动态获取状态名称
     */
    private String getDockStatusText(Integer status) {
        if (status == null) return "未知";
        return SystemVariableCache.getStatusName("dock_status", String.valueOf(status));
    }
}

