package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.Result;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderQueryRequest;
import com.course.platform.domain.dto.OrderExportRequest;
import com.course.platform.domain.dto.AdjustCountdownDTO;
import com.course.platform.domain.dto.CompleteOrderDTO;
import com.course.platform.domain.dto.BatchCountdownOperationDTO;
import com.course.platform.domain.dto.RestartCountdownDTO;
import com.course.platform.domain.dto.SwitchStatusDTO;
import com.course.platform.domain.dto.StartExamCountdownDTO;
import com.course.platform.domain.dto.AdjustExamCountdownDTO;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.entity.CountdownHistory;
import com.course.platform.domain.dto.CountdownHistoryDTO;
import com.course.platform.domain.vo.OrderStatisticsVO;
import com.course.platform.domain.vo.OrderExportResponse;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.support.OperationLogService;
import com.course.platform.application.service.order.OrderCountdownService;
import com.course.platform.application.service.course.CountdownConfigService;
import com.course.platform.application.service.order.CountdownHistoryService;
import com.course.platform.application.service.order.OrderExportService;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 管理员订单管理控制器
 * 提供系统管理员对自营平台的订单管理能力
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Tag(name = "管理员订单管理", description = "系统管理员订单管理接口")
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final CourseOrderMapper courseOrderMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final OrderCountdownService orderCountdownService;
    private final CountdownConfigService countdownConfigService;
    private final CountdownHistoryService countdownHistoryService;
    private  final OrderExportService orderExportService;

    /**
     * 订单状态常量获取辅助方法
     * Source: AURA-X-KYS - 统一状态管理
     */
    private static int getOrderStatus(String key) {
        return SystemVariableCache.getStatusValue("order_status", key);
    }

    /**
     * 验证管理员权限
     */
    private void checkAdmin(Long userId) {
        if (!Constants.DEFAULT_ADMIN_ID.equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 管理员查询所有订单（无权限限制）
     */
    @Operation(summary = "查询所有订单", description = "管理员查询平台所有订单")
    @PostMapping("/query-all")
    public Result<IPage<CourseOrder>> queryAllOrders(@RequestBody OrderQueryRequest request,
                                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 管理员可以查看所有订单，不添加用户ID限制
        Page<CourseOrder> page = new Page<>(request.getPage(), request.getPageSize());

        LambdaQueryWrapper<CourseOrder> queryWrapper = new LambdaQueryWrapper<>();

        // 添加查询条件
        if (StrUtil.isNotBlank(request.getOrderNo())) {
            queryWrapper.eq(CourseOrder::getOrderNo, request.getOrderNo());
        }
        if (request.getPlatformId() != null) {
            queryWrapper.eq(CourseOrder::getPlatformId, request.getPlatformId());
        }
        if (StrUtil.isNotBlank(request.getStudentAccount())) {
            // 支持学生账号模糊搜索
            queryWrapper.like(CourseOrder::getStudentAccount, request.getStudentAccount());
        }
        if (request.getOrderStatus() != null) {
            queryWrapper.eq(CourseOrder::getOrderStatus, request.getOrderStatus());
        }
        if (request.getDockStatus() != null) {
            queryWrapper.eq(CourseOrder::getDockStatus, request.getDockStatus());
        }
        if (request.getUserId() != null) {
            queryWrapper.eq(CourseOrder::getUserId, request.getUserId());
        }
        // 自营订单筛选条件
        if (request.getIsSelfOperated() != null) {
            queryWrapper.eq(CourseOrder::getIsSelfOperated, request.getIsSelfOperated());
        }

        queryWrapper.orderByDesc(CourseOrder::getCreateTime);

        IPage<CourseOrder> result = courseOrderMapper.selectPage(page, queryWrapper);
        
        // 计算基于倒计时的进度
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            for (CourseOrder order : result.getRecords()) {
                calculateProgressBasedOnCountdown(order);
            }
        }
        
        // 调试日志：检查查询结果
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            CourseOrder firstOrder = result.getRecords().get(0);
            log.info("查询到订单数据，第一个订单ID: {}, 订单编号: {}, 进度: {}", 
                    firstOrder.getId(), firstOrder.getOrderNo(), firstOrder.getProgress());
        }
        
        // 记录操作日志
        operationLogService.log(userId, "查询订单", 
                String.format("管理员查询订单列表，条件：%s", request.toString()),
                BigDecimal.ZERO, null);

        return Result.success(result);
    }

    /**
     * 获取代理账号列表
     */
    @Operation(summary = "获取代理账号列表", description = "获取所有代理账号列表供筛选使用")
    @GetMapping("/agent-accounts")
    public Result<List<Map<String, Object>>> getAgentAccounts(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 查询所有用户（代理账号）
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getNickname)
                .orderByAsc(User::getUsername)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("nickname", user.getNickname());
            result.add(map);
        }

        return Result.success(result);
    }

    /**
     * 获取订单统计信息
     */
    @Operation(summary = "获取订单统计", description = "获取平台订单统计信息")
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> getOrderStatistics(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        OrderStatisticsVO statistics = new OrderStatisticsVO();

        // 总订单数
        Long totalOrders = courseOrderMapper.selectCount(null);
        statistics.setTotalOrders(totalOrders);

        // 各状态订单数量（使用系统变量缓存）
        // Source: AURA-X-KYS - 统一状态管理
        statistics.setPendingOrders(courseOrderMapper.selectCount(
                new LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getOrderStatus, getOrderStatus("pending"))));
        
        statistics.setProcessingOrders(courseOrderMapper.selectCount(
                new LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getOrderStatus, getOrderStatus("processing"))));
        
        statistics.setCompletedOrders(courseOrderMapper.selectCount(
                new LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getOrderStatus, getOrderStatus("completed"))));
        
        statistics.setCancelledOrders(courseOrderMapper.selectCount(
                new LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getOrderStatus, getOrderStatus("cancelled"))));
        
        statistics.setFailedOrders(courseOrderMapper.selectCount(
                new LambdaQueryWrapper<CourseOrder>()
                        .eq(CourseOrder::getOrderStatus, getOrderStatus("failed"))));

        // 总金额统计 - 使用原生SQL查询
        BigDecimal totalRevenue = BigDecimal.ZERO;
        try {
            List<CourseOrder> completedOrders = courseOrderMapper.selectList(
                    new LambdaQueryWrapper<CourseOrder>()
                            .eq(CourseOrder::getOrderStatus, getOrderStatus("completed"))
                            .select(CourseOrder::getAmount));
            
            for (CourseOrder order : completedOrders) {
                if (order.getAmount() != null) {
                    totalRevenue = totalRevenue.add(order.getAmount());
                }
            }
        } catch (Exception e) {
            log.error("计算总营收失败：{}", e.getMessage());
        }
        statistics.setTotalRevenue(totalRevenue);

        // 今日订单统计
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        
        statistics.setTodayOrders(courseOrderMapper.selectCount(
                new LambdaQueryWrapper<CourseOrder>()
                        .between(CourseOrder::getCreateTime, todayStart, todayEnd)));

        // 记录操作日志
        operationLogService.log(userId, "查看统计", "管理员查看订单统计信息",
                BigDecimal.ZERO, null);

        return Result.success(statistics);
    }

    /**
     * 管理员强制修改订单状态
     */
    @Operation(summary = "强制修改订单状态", description = "管理员强制修改订单状态")
    @PostMapping("/{orderId}/force-update-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> forceUpdateOrderStatus(@PathVariable Long orderId,
                                               @RequestParam Integer newStatus,
                                               @RequestParam(required = false) String reason,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        Integer oldStatus = order.getOrderStatus();
        order.setOrderStatus(newStatus);
        courseOrderMapper.updateById(order);

        // 记录操作日志
        String operationDesc = String.format("管理员强制修改订单状态：%s -> %s，原因：%s", 
                getStatusText(oldStatus), getStatusText(newStatus), 
                reason != null ? reason : "无");
        operationLogService.log(userId, "强制修改订单状态", operationDesc,
                BigDecimal.ZERO, null);

        log.info("管理员强制修改订单状态：orderId={}, oldStatus={}, newStatus={}, reason={}, operatorId={}", 
                orderId, oldStatus, newStatus, reason, userId);

        return Result.success("订单状态修改成功");
    }

    /**
     * 管理员强制修改对接状态
     */
    @Operation(summary = "强制修改对接状态", description = "管理员强制修改对接状态")
    @PostMapping("/{orderId}/force-update-dock-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> forceUpdateDockStatus(@PathVariable Long orderId,
                                               @RequestParam Integer newStatus,
                                               @RequestParam(required = false) String reason,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        Integer oldStatus = order.getDockStatus();
        order.setDockStatus(newStatus);
        courseOrderMapper.updateById(order);

        // 记录操作日志
        String operationDesc = String.format("管理员强制修改对接状态：%s -> %s，原因：%s", 
                getDockStatusText(oldStatus), getDockStatusText(newStatus), 
                reason != null ? reason : "无");
        operationLogService.log(userId, "强制修改对接状态", operationDesc,
                BigDecimal.ZERO, null);

        log.info("管理员强制修改对接状态：orderId={}, oldStatus={}, newStatus={}, reason={}, operatorId={}", 
                orderId, oldStatus, newStatus, reason, userId);

        return Result.success("对接状态修改成功");
    }

    /**
     * 管理员添加订单备注
     */
    @Operation(summary = "添加订单备注", description = "管理员为订单添加备注")
    @PostMapping("/{orderId}/add-remark")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addOrderRemark(@PathVariable Long orderId,
                                        @RequestParam String remark,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        String oldRemark = order.getRemarks();
        String newRemark = oldRemark != null ? oldRemark + "\n[" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                "] " + remark : remark;
        
        order.setRemarks(newRemark);
        courseOrderMapper.updateById(order);

        // 记录操作日志
        operationLogService.log(userId, "添加订单备注", 
                String.format("为订单 %s 添加备注：%s", order.getOrderNo(), remark),
                BigDecimal.ZERO, null);

        return Result.success("备注添加成功");
    }

    /**
     * 管理员查看订单详情（无权限限制）
     */
    @Operation(summary = "查看订单详情", description = "管理员查看任意订单详情")
    @GetMapping("/{orderId}/detail")
    public Result<CourseOrder> getOrderDetail(@PathVariable Long orderId,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 记录操作日志
        operationLogService.log(userId, "查看订单详情", 
                String.format("管理员查看订单详情：%s", order.getOrderNo()),
                BigDecimal.ZERO, null);

        return Result.success(order);
    }

    /**
     * 管理员删除订单
     */
    @Operation(summary = "删除订单", description = "管理员删除订单")
    @DeleteMapping("/{orderId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteOrder(@PathVariable Long orderId,
                                    @RequestParam(required = false) String reason,
                                    Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 1. 查询订单是否存在
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 2. 检查订单状态，只有特定状态的订单才能删除
        if (order.getOrderStatus() == getOrderStatus("processing")) {
            throw new BusinessException("进行中的订单不能删除，请先取消订单");
        }

        // 3. 如果订单已完成，需要退回余额
        if (order.getOrderStatus() == getOrderStatus("completed")) {
            User user = userMapper.selectById(order.getUserId());
            if (user != null) {
                user.setBalance(user.getBalance().add(order.getAmount()));
                userMapper.updateById(user);
                
                // 记录余额退回日志
                operationLogService.log(order.getUserId(), "订单删除退回", 
                        String.format("管理员删除订单退回：%s，金额：%s元", order.getOrderNo(), order.getAmount()),
                        order.getAmount(), user.getBalance());
            }
        }

        // 4. 删除订单
        courseOrderMapper.deleteById(orderId);

        // 5. 记录操作日志
        operationLogService.log(userId, "删除订单", 
                String.format("管理员删除订单：%s，原因：%s", order.getOrderNo(), 
                        reason != null ? reason : "无"),
                BigDecimal.ZERO, null);

        log.info("管理员删除订单成功：orderId={}, operatorId={}, reason={}", orderId, userId, reason);

        return Result.success("订单删除成功");
    }

    /**
     * 管理员批量删除订单
     */
    @Operation(summary = "批量删除订单", description = "管理员批量删除订单")
    @DeleteMapping("/batch-delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchDeleteOrders(@RequestBody List<Long> orderIds,
                                           @RequestParam(required = false) String reason,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        BigDecimal totalRefund = BigDecimal.ZERO;

        for (Long orderId : orderIds) {
            try {
                CourseOrder order = courseOrderMapper.selectById(orderId);
                if (order != null) {
                    // 检查订单状态
                    if (order.getOrderStatus() == getOrderStatus("processing")) {
                        log.warn("跳过进行中的订单：orderId={}", orderId);
                        continue;
                    }

                    // 如果订单已完成，退回余额
                    if (order.getOrderStatus() == getOrderStatus("completed")) {
                        User user = userMapper.selectById(order.getUserId());
                        if (user != null) {
                            user.setBalance(user.getBalance().add(order.getAmount()));
                            userMapper.updateById(user);
                            totalRefund = totalRefund.add(order.getAmount());
                            
                            // 记录余额退回日志
                            operationLogService.log(order.getUserId(), "批量删除订单退回", 
                                    String.format("管理员批量删除订单退回：%s，金额：%s元", order.getOrderNo(), order.getAmount()),
                                    order.getAmount(), user.getBalance());
                        }
                    }

                    // 删除订单
                    courseOrderMapper.deleteById(orderId);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量删除订单失败：orderId={}, error={}", orderId, e.getMessage());
            }
        }

        // 记录操作日志
        operationLogService.log(userId, "批量删除订单", 
                String.format("批量删除订单：成功%d/%d，退回金额：%s元，原因：%s", 
                        successCount, orderIds.size(), totalRefund, 
                        reason != null ? reason : "无"),
                BigDecimal.ZERO, null);

        log.info("管理员批量删除订单完成：successCount={}/{}, operatorId={}", successCount, orderIds.size(), userId);

        return Result.success(String.format("批量删除完成，成功删除%d个订单", successCount));
    }

    /**
     * 管理员切换自营订单状态
     */
    @Operation(summary = "切换自营订单状态", description = "管理员切换自营订单状态并启动倒计时")
    @PostMapping("/{orderId}/toggle-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> toggleSelfOperatedOrderStatus(@PathVariable Long orderId,
                                                      @RequestParam Integer newStatus,
                                                      @RequestParam(required = false) Integer countdownDuration,
                                                      @RequestParam(required = false) Boolean autoComplete,
                                                      @RequestParam(required = false) String reason,
                                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 1. 查询订单是否存在
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 2. 检查是否为自营订单
        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        // 3. 更新订单状态
        Integer oldStatus = order.getOrderStatus();
        order.setOrderStatus(newStatus);

        // 4. 如果切换到进行中状态，启动倒计时
        if (newStatus == getOrderStatus("processing")) {
            if (countdownDuration == null || countdownDuration <= 0) {
                // 从配置中获取默认倒计时时长
                countdownDuration = countdownConfigService.getIntConfigValue("default_countdown_duration", 60);
            }
            
            LocalDateTime now = LocalDateTime.now();
            order.setCountdownDuration(countdownDuration);
            order.setCountdownStartTime(now);
            order.setCountdownEndTime(now.plusMinutes(countdownDuration));
            order.setAutoCompleteEnabled(autoComplete != null && autoComplete ? 1 : 0);
            
            log.info("自营订单倒计时启动：orderId={}, duration={}分钟, autoComplete={}", 
                    orderId, countdownDuration, autoComplete);
        } else {
            // 其他状态时清除倒计时
            order.setCountdownDuration(null);
            order.setCountdownStartTime(null);
            order.setCountdownEndTime(null);
            order.setAutoCompleteEnabled(0);
        }

        // 5. 更新订单
        courseOrderMapper.updateById(order);

        // 6. 记录操作日志
        operationLogService.log(userId, "切换自营订单状态", 
                String.format("切换自营订单状态：%s，从%d到%d，倒计时：%s分钟，原因：%s", 
                        order.getOrderNo(), oldStatus, newStatus, 
                        countdownDuration != null ? countdownDuration.toString() : "无",
                        reason != null ? reason : "无"),
                BigDecimal.ZERO, null);

        log.info("自营订单状态切换成功：orderId={}, oldStatus={}, newStatus={}, operatorId={}", 
                orderId, oldStatus, newStatus, userId);

        return Result.success("订单状态切换成功");
    }

    /**
     * 管理员调整倒计时
     */
    @Operation(summary = "调整倒计时", description = "管理员调整自营订单倒计时")
    @PostMapping("/{orderId}/adjust-countdown")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> adjustCountdown(@PathVariable Long orderId,
                                       @RequestBody AdjustCountdownDTO adjustCountdownDTO,
                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 1. 查询订单是否存在
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 2. 检查是否为自营订单且正在进行中
        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能调整倒计时");
        }

        if (order.getOrderStatus() != getOrderStatus("processing")) {
            throw new BusinessException("只有进行中的订单才能调整倒计时");
        }

        // 3. 调整倒计时
        Integer oldDuration = order.getCountdownDuration();
        Integer newDuration = adjustCountdownDTO.getNewDuration();
        LocalDateTime now = LocalDateTime.now();
        
        order.setCountdownDuration(newDuration);
        order.setCountdownStartTime(now);
        order.setCountdownEndTime(now.plusMinutes(newDuration));

        // 4. 更新订单
        courseOrderMapper.updateById(order);

        // 5. 记录操作日志
        operationLogService.log(userId, "调整倒计时", 
                String.format("调整倒计时：%s，从%d分钟到%d分钟，原因：%s", 
                        order.getOrderNo(), oldDuration, newDuration,
                        adjustCountdownDTO.getReason() != null ? adjustCountdownDTO.getReason() : "无"),
                BigDecimal.ZERO, null);

        // 6. 记录倒计时历史
        User operator = userMapper.selectById(userId);
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "adjust", 
                oldDuration, newDuration, adjustCountdownDTO.getReason(), 
                userId, operator != null ? operator.getUsername() : "系统");

        log.info("倒计时调整成功：orderId={}, oldDuration={}, newDuration={}, operatorId={}", 
                orderId, oldDuration, newDuration, userId);

        return Result.success("倒计时调整成功");
    }

    /**
     * 获取正在倒计时的订单列表
     */
    @Operation(summary = "获取倒计时订单", description = "获取所有正在倒计时的自营订单")
    @GetMapping("/countdown")
    public Result<List<CourseOrder>> getActiveCountdownOrders(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        List<CourseOrder> orders = orderCountdownService.getActiveCountdownOrders();
        return Result.success(orders);
    }

    /**
     * 获取订单剩余倒计时时间
     */
    @Operation(summary = "获取剩余倒计时", description = "获取订单剩余倒计时时间")
    @GetMapping("/{orderId}/countdown-remaining")
    public Result<Long> getRemainingCountdown(@PathVariable Long orderId,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        long remainingMinutes = orderCountdownService.getRemainingCountdownMinutes(orderId);
        return Result.success(remainingMinutes);
    }

    /**
     * 手动完成订单
     */
    @Operation(summary = "手动完成订单", description = "管理员手动完成自营订单")
    @PostMapping("/{orderId}/complete")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> completeOrder(@PathVariable Long orderId,
                                       @RequestBody(required = false) CompleteOrderDTO completeOrderDTO,
                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        orderCountdownService.completeOrder(orderId, userId);

        // 记录操作日志
        String reason = completeOrderDTO != null && completeOrderDTO.getReason() != null 
                ? completeOrderDTO.getReason() : "无";
        operationLogService.log(userId, "手动完成订单", 
                String.format("手动完成自营订单：%d，原因：%s", orderId, reason),
                BigDecimal.ZERO, null);

        // 记录倒计时历史
        CourseOrder order = courseOrderMapper.selectById(orderId);
        User operator = userMapper.selectById(userId);
        if (order != null) {
            countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "complete", 
                    order.getCountdownDuration(), 0, reason, 
                    userId, operator != null ? operator.getUsername() : "系统");
        }

        log.info("手动完成订单成功：orderId={}, operatorId={}, reason={}", orderId, userId, reason);

        return Result.success("订单完成成功");
    }

    /**
     * 重新开始倒计时
     */
    @Operation(summary = "重新开始倒计时", description = "为订单重新开始倒计时")
    @PostMapping("/{orderId}/restart-countdown")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> restartCountdown(@PathVariable Long orderId,
                                         @RequestBody RestartCountdownDTO restartCountdownDTO,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        orderCountdownService.restartCountdown(orderId, restartCountdownDTO.getDuration(), 
                userId, restartCountdownDTO.getReason());

        return Result.success("倒计时重新开始成功");
    }

    /**
     * 切换订单状态（倒计时结束后）
     */
    @Operation(summary = "切换订单状态", description = "倒计时结束后切换订单状态")
    @PostMapping("/{orderId}/switch-status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> switchOrderStatus(@PathVariable Long orderId,
                                          @RequestBody SwitchStatusDTO switchStatusDTO,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        orderCountdownService.switchOrderStatus(orderId, switchStatusDTO.getNewStatus(), 
                userId, switchStatusDTO.getReason());

        return Result.success("订单状态切换成功");
    }

    /**
     * 开始下一步任务倒计时
     */
    @Operation(summary = "开始下一步任务倒计时", description = "为待处理订单开始下一步任务倒计时")
    @PostMapping("/{orderId}/start-next-task-countdown")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> startNextTaskCountdown(@PathVariable Long orderId,
                                               @RequestBody RestartCountdownDTO restartCountdownDTO,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 调用下一步任务倒计时服务
        orderCountdownService.startNextTaskCountdown(orderId, restartCountdownDTO.getDuration(), userId, restartCountdownDTO.getReason());
        
        return Result.success("下一步任务倒计时开始成功");
    }

    /**
     * 批量倒计时操作
     */
    @Operation(summary = "批量倒计时操作", description = "批量完成订单或调整倒计时")
    @PostMapping("/batch-countdown-operation")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchCountdownOperation(@RequestBody BatchCountdownOperationDTO batchOperationDTO,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        List<Long> orderIds = batchOperationDTO.getOrderIds();
        String operationType = batchOperationDTO.getOperationType();
        String reason = batchOperationDTO.getReason();

        int successCount = 0;
        int failCount = 0;
        StringBuilder failMessages = new StringBuilder();

        for (Long orderId : orderIds) {
            try {
                CourseOrder order = courseOrderMapper.selectById(orderId);
                if (order == null) {
                    failCount++;
                    failMessages.append(String.format("订单%d不存在; ", orderId));
                    continue;
                }

                if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
                    failCount++;
                    failMessages.append(String.format("订单%d不是自营订单; ", orderId));
                    continue;
                }

                if (order.getOrderStatus() != getOrderStatus("processing")) {
                    failCount++;
                    failMessages.append(String.format("订单%d不是进行中状态; ", orderId));
                    continue;
                }

                if ("complete".equals(operationType)) {
                    // 批量完成订单
                    orderCountdownService.completeOrder(orderId, userId);
                    successCount++;
                } else if ("adjust".equals(operationType)) {
                    // 批量调整倒计时
                    Integer newDuration = batchOperationDTO.getNewDuration();
                    if (newDuration == null || newDuration < 1 || newDuration > 1440) {
                        failCount++;
                        failMessages.append(String.format("订单%d倒计时时长无效; ", orderId));
                        continue;
                    }

                    LocalDateTime now = LocalDateTime.now();
                    order.setCountdownDuration(newDuration);
                    order.setCountdownStartTime(now);
                    order.setCountdownEndTime(now.plusMinutes(newDuration));
                    courseOrderMapper.updateById(order);
                    successCount++;
                } else {
                    failCount++;
                    failMessages.append(String.format("订单%d操作类型无效; ", orderId));
                }

            } catch (Exception e) {
                failCount++;
                failMessages.append(String.format("订单%d操作失败: %s; ", orderId, e.getMessage()));
                log.error("批量操作订单失败：orderId={}, error={}", orderId, e.getMessage(), e);
            }
        }

        // 记录操作日志
        String logDesc = String.format("批量%s操作：成功%d个，失败%d个，原因：%s", 
                "complete".equals(operationType) ? "完成订单" : "调整倒计时",
                successCount, failCount, reason != null ? reason : "无");
        operationLogService.log(userId, "批量倒计时操作", logDesc, BigDecimal.ZERO, null);

        if (failCount > 0) {
            return Result.error(String.format("批量操作完成：成功%d个，失败%d个。失败详情：%s", 
                    successCount, failCount, failMessages.toString()));
        } else {
            return Result.success(String.format("批量操作成功：共处理%d个订单", successCount));
        }
    }

    /**
     * 获取订单倒计时历史记录
     */
    @Operation(summary = "获取倒计时历史", description = "获取订单的倒计时操作历史记录")
    @GetMapping("/{orderId}/countdown-history")
    public Result<List<CountdownHistory>> getCountdownHistory(@PathVariable Long orderId,
                                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        List<CountdownHistory> history = countdownHistoryService.getOrderHistory(orderId);
        return Result.success(history);
    }

    /**
     * 获取所有倒计时历史记录
     */
    @Operation(summary = "获取所有倒计时历史", description = "获取所有倒计时操作历史记录")
    @GetMapping("/countdown-history")
    public Result<List<CountdownHistory>> getAllCountdownHistory(@RequestParam(defaultValue = "1") Integer pageNum,
                                                               @RequestParam(defaultValue = "20") Integer pageSize,
                                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        List<CountdownHistory> history = countdownHistoryService.getAllHistory(pageNum, pageSize);
        return Result.success(history);
    }

    /**
     * 获取倒计时历史记录（包含账号和订单状态）
     */
    @Operation(summary = "获取倒计时历史记录", description = "获取包含账号和订单状态的倒计时历史记录")
    @GetMapping("/countdown-history-with-details")
    public Result<List<CountdownHistoryDTO>> getCountdownHistoryWithDetails(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        List<CountdownHistoryDTO> history = countdownHistoryService.getAllHistoryWithDetails(pageNum, pageSize);
        return Result.success(history);
    }

    /**
     * 管理员批量操作订单
     */
    @Operation(summary = "批量操作订单", description = "管理员批量操作订单")
    @PostMapping("/batch-operation")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> batchOperation(@RequestBody List<Long> orderIds,
                                        @RequestParam String operation,
                                        @RequestParam(required = false) String value,
                                        @RequestParam(required = false) String reason,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        int successCount = 0;
        for (Long orderId : orderIds) {
            try {
                CourseOrder order = courseOrderMapper.selectById(orderId);
                if (order != null) {
                    switch (operation) {
                        case "updateStatus":
                            order.setOrderStatus(Integer.parseInt(value));
                            break;
                        case "updateDockStatus":
                            order.setDockStatus(Integer.parseInt(value));
                            break;
                        case "addRemark":
                            String oldRemark = order.getRemarks();
                            String newRemark = oldRemark != null ? oldRemark + "\n[" + 
                                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
                                    "] " + value : value;
                            order.setRemarks(newRemark);
                            break;
                    }
                    courseOrderMapper.updateById(order);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量操作订单失败：orderId={}, operation={}, error={}", 
                        orderId, operation, e.getMessage());
            }
        }

        // 记录操作日志
        operationLogService.log(userId, "批量操作订单", 
                String.format("批量操作订单：%s，操作：%s，成功：%d/%d，原因：%s", 
                        operation, value, successCount, orderIds.size(), 
                        reason != null ? reason : "无"),
                BigDecimal.ZERO, null);

        return Result.success(String.format("批量操作完成，成功处理 %d/%d 个订单", successCount, orderIds.size()));
    }

    /**
     * 订单导出功能
     */
    @Operation(summary = "订单导出", description = "管理员导出订单信息，支持txt和xlsx格式")
    @PostMapping("/export")
    public ResponseEntity<?> exportOrders(@RequestBody OrderExportRequest request,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 查询订单列表
        List<CourseOrder> orders = new ArrayList<>();
        for (Long orderId : request.getOrderIds()) {
            CourseOrder order = courseOrderMapper.selectById(orderId);
            if (order != null) {
                orders.add(order);
            }
        }

        // 根据文件类型导出
        String fileType = request.getFileType() != null ? request.getFileType() : "txt";
        
        try {
            if ("xlsx".equalsIgnoreCase(fileType)) {
                // 导出为xlsx格式
                ByteArrayResource resource = orderExportService.exportAsXlsx(orders, request.getFormat());
                
                String filename = "订单导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
                
                // 记录操作日志
                operationLogService.log(userId, "导出订单", 
                        String.format("导出xlsx格式订单：格式%d，数量：%d，原因：%s", 
                                request.getFormat(), orders.size(), 
                                request.getReason() != null ? request.getReason() : "无"),
                        BigDecimal.ZERO, null);
                
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + 
                                new String(filename.getBytes("UTF-8"), "ISO-8859-1") + "\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(resource);
                        
            } else {
                // 导出为txt格式（原有逻辑）
                String content = orderExportService.exportAsTxt(orders, request.getFormat());
                
                OrderExportResponse response = new OrderExportResponse();
                response.setContent(content);
                response.setFormat(request.getFormat());
                response.setCount(orders.size());
                response.setExportTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                // 记录操作日志
                operationLogService.log(userId, "导出订单", 
                        String.format("导出txt格式订单：格式%d，数量：%d，原因：%s", 
                                request.getFormat(), orders.size(), 
                                request.getReason() != null ? request.getReason() : "无"),
                        BigDecimal.ZERO, null);

                log.info("管理员导出订单：orderIds={}, format={}, fileType={}, count={}, operatorId={}", 
                        request.getOrderIds(), request.getFormat(), fileType, orders.size(), userId);

                return ResponseEntity.ok(Result.success(response));
            }
        } catch (Exception e) {
            log.error("导出订单失败", e);
            return ResponseEntity.ok(Result.error("导出失败: " + e.getMessage()));
        }
    }

    /**
     * 根据格式格式化订单信息
     */
    private String formatOrderForExport(CourseOrder order, Integer format) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String password = order.getStudentPassword() != null ? order.getStudentPassword() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";

        switch (format) {
            case 1: // 学校+账号+密码+课程名字
                return String.format("%s %s %s %s", school, account, password, courseName);
            case 2: // 账号+密码+课程名字
                return String.format("%s %s %s", account, password, courseName);
            case 3: // 学校+账号+密码
                return String.format("%s %s %s", school, account, password);
            case 4: // 账号+密码
                return String.format("%s %s", account, password);
            default:
                return null;
        }
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

    /**
     * 计算基于倒计时的进度
     * 当订单处于进行中状态且有倒计时时，根据倒计时剩余时间计算进度百分比
     */
    private void calculateProgressBasedOnCountdown(CourseOrder order) {
        // 只有进行中状态的自营订单才需要计算基于倒计时的进度
        if (order.getOrderStatus() != null && order.getOrderStatus() == getOrderStatus("processing") &&
            order.getIsSelfOperated() != null && order.getIsSelfOperated() == 1 &&
            order.getCountdownStartTime() != null && order.getCountdownEndTime() != null &&
            order.getCountdownDuration() != null && order.getCountdownDuration() > 0) {
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = order.getCountdownStartTime();
            LocalDateTime endTime = order.getCountdownEndTime();
            
            // 如果倒计时已过期，进度设为100%
            if (endTime.isBefore(now)) {
                order.setProgress("100%");
                return;
            }
            
            // 计算总倒计时时长（分钟）
            long totalMinutes = order.getCountdownDuration();
            
            // 计算已过去的时间（分钟）
            long elapsedMinutes = java.time.Duration.between(startTime, now).toMinutes();
            
            // 确保已过去时间不为负数
            elapsedMinutes = Math.max(0, elapsedMinutes);
            
            // 计算进度百分比
            int progressPercent = (int) Math.min(100, Math.max(0, (elapsedMinutes * 100) / totalMinutes));
            
            // 设置进度
            order.setProgress(progressPercent + "%");
            
            log.debug("计算倒计时进度：orderId={}, 总时长={}分钟, 已过去={}分钟, 进度={}%", 
                    order.getId(), totalMinutes, elapsedMinutes, progressPercent);
        }
    }

    // ========== 考试倒计时相关接口 ==========

    /**
     * 获取正在考试倒计时的订单列表
     */
    @Operation(summary = "获取考试倒计时订单", description = "获取所有正在考试倒计时的自营订单")
    @GetMapping("/exam-countdown")
    public Result<List<CourseOrder>> getActiveExamCountdownOrders(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        List<CourseOrder> orders = orderCountdownService.getActiveExamCountdownOrders();
        return Result.success(orders);
    }

    /**
     * 获取订单剩余考试倒计时时间
     */
    @Operation(summary = "获取剩余考试倒计时", description = "获取订单剩余考试倒计时时间")
    @GetMapping("/{orderId}/exam-countdown-remaining")
    public Result<Long> getRemainingExamCountdown(@PathVariable Long orderId,
                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        long remainingMinutes = orderCountdownService.getRemainingExamCountdownMinutes(orderId);
        return Result.success(remainingMinutes);
    }

    /**
     * 手动完成考试
     */
    @Operation(summary = "手动完成考试", description = "手动完成自营订单的考试")
    @PostMapping("/{orderId}/complete-exam")
    public Result<Void> completeExam(@PathVariable Long orderId,
                                    @RequestBody CompleteOrderDTO completeOrderDTO,
                                    Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        orderCountdownService.completeExam(orderId, userId);
        return Result.success("考试完成成功");
    }

    /**
     * 开始考试倒计时
     */
    @Operation(summary = "开始考试倒计时", description = "为自营订单开始考试倒计时")
    @PostMapping("/{orderId}/start-exam-countdown")
    public Result<Void> startExamCountdown(@PathVariable Long orderId,
                                           @RequestBody StartExamCountdownDTO startExamCountdownDTO,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        orderCountdownService.startExamCountdown(orderId, 
                startExamCountdownDTO.getDuration(), 
                userId, 
                startExamCountdownDTO.getReason());
        return Result.success("考试倒计时开始成功");
    }

    /**
     * 调整考试倒计时
     */
    @Operation(summary = "调整考试倒计时", description = "调整自营订单的考试倒计时时长")
    @PostMapping("/{orderId}/adjust-exam-countdown")
    public Result<Void> adjustExamCountdown(@PathVariable Long orderId,
                                           @RequestBody AdjustExamCountdownDTO adjustExamCountdownDTO,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        checkAdmin(userId);

        // 获取当前考试倒计时时长
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Integer oldDuration = order.getExamCountdownDuration();
        Integer newDuration = adjustExamCountdownDTO.getNewDuration();

        orderCountdownService.adjustExamCountdown(orderId, newDuration, userId, adjustExamCountdownDTO.getReason());

        log.info("考试倒计时调整成功：orderId={}, oldDuration={}, newDuration={}, operatorId={}", 
                orderId, oldDuration, newDuration, userId);

        return Result.success("考试倒计时调整成功");
    }
}
