package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.shared.constant.Constants;
import com.course.platform.shared.exception.BusinessException;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.mapper.CourseOrderMapper;
import com.course.platform.mapper.UserMapper;
import com.course.platform.service.CountdownConfigService;
import com.course.platform.service.CountdownHistoryService;
import com.course.platform.service.OperationLogService;
import com.course.platform.service.OrderCountdownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单倒计时服务实现类
 * 处理自营订单的倒计时和自动完成功能
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于自营订单倒计时需求设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCountdownServiceImpl implements OrderCountdownService {

    private final CourseOrderMapper courseOrderMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final CountdownConfigService countdownConfigService;
    private final CountdownHistoryService countdownHistoryService;

    /**
     * 获取订单状态值
     */
    private static int getOrderStatus(String key) {
        return SystemVariableCache.getStatusValue("order_status", key);
    }

    /**
     * 获取对接状态值
     */
    private static int getDockStatus(String key) {
        return SystemVariableCache.getStatusValue("dock_status", key);
    }

    /**
     * 定时检查并处理过期的倒计时订单
     * 每分钟执行一次
     * 过期订单会进入历史记录，保持可见性供后续操作
     */
    @Override
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    @Transactional(rollbackFor = Exception.class)
    public void processExpiredCountdownOrders() {
        try {
            // 查询所有正在倒计时且已过期的订单
            List<CourseOrder> expiredOrders = courseOrderMapper.selectList(new LambdaQueryWrapper<CourseOrder>()
                    .eq(CourseOrder::getIsSelfOperated, 1)
                    .eq(CourseOrder::getOrderStatus, getOrderStatus("processing"))
                    .isNotNull(CourseOrder::getCountdownEndTime)
                    .le(CourseOrder::getCountdownEndTime, LocalDateTime.now()));

            if (expiredOrders.isEmpty()) {
                return;
            }

            log.info("发现{}个过期的倒计时订单，开始处理", expiredOrders.size());

            for (CourseOrder order : expiredOrders) {
                try {
                    // 检查是否启用自动完成
                    boolean autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("auto_complete_enabled", true);
                    boolean orderAutoComplete = order.getAutoCompleteEnabled() != null && order.getAutoCompleteEnabled() == 1;
                    
                    if (autoCompleteEnabled && orderAutoComplete) {
                        // 获取配置的自动完成状态
                        Integer autoCompleteStatus = countdownConfigService.getIntConfigValue("auto_complete_status", getOrderStatus("completed"));
                        
                        // 记录历史 - 自动完成
                        countdownHistoryService.recordHistory(order.getId(), order.getOrderNo(), "auto_complete", 
                                order.getCountdownDuration(), 0, "系统自动完成", 0L, "系统");
                        
                        // 自动完成订单
                        completeOrderInternal(order, 0L, "系统自动完成", autoCompleteStatus);
                        log.info("订单自动完成：orderId={}, orderNo={}, 状态={}", order.getId(), order.getOrderNo(), autoCompleteStatus);
                    } else {
                        // 记录历史 - 倒计时过期，等待手动处理
                        countdownHistoryService.recordHistory(order.getId(), order.getOrderNo(), "expired", 
                                order.getCountdownDuration(), 0, "倒计时已结束，等待手动处理", 0L, "系统");
                        
                        // 标记为需要手动处理，但保持倒计时信息
                        order.setOrderStatus(getOrderStatus("pending"));
                        order.setRemarks((order.getRemarks() != null ? order.getRemarks() + "\n" : "") +
                                String.format("[%s] 倒计时已结束，订单进入待考试状态", 
                                        LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                        courseOrderMapper.updateById(order);
                        log.info("订单倒计时结束，进入待考试状态：orderId={}, orderNo={}", order.getId(), order.getOrderNo());
                    }
                } catch (Exception e) {
                    log.error("处理过期订单失败：orderId={}, error={}", order.getId(), e.getMessage(), e);
                }
            }

            log.info("过期倒计时订单处理完成，共处理{}个订单", expiredOrders.size());

        } catch (Exception e) {
            log.error("定时处理过期倒计时订单失败：{}", e.getMessage(), e);
        }
    }

    @Override
    public List<CourseOrder> getActiveCountdownOrders() {
        // 查询所有有普通倒计时记录的订单，包括已过期的，保持可见性
        List<CourseOrder> orders = courseOrderMapper.selectList(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getIsSelfOperated, 1)
                .in(CourseOrder::getOrderStatus, 
                    getOrderStatus("processing"), 
                    getOrderStatus("pending"))
                .isNotNull(CourseOrder::getCountdownEndTime)
                .orderByAsc(CourseOrder::getCountdownEndTime));
        
        log.debug("获取到{}个倒计时订单，其中过期订单数：{}", 
            orders.size(), 
            orders.stream().filter(o -> o.getCountdownEndTime().isBefore(java.time.LocalDateTime.now())).count());
        
        return orders;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long orderId, Long operatorId) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("processing")) {
            throw new BusinessException("只有进行中的订单才能完成");
        }

        // 记录历史 - 手动完成订单
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "manual_complete", 
                order.getCountdownDuration(), 0, "手动完成订单", operatorId, getOperatorName(operatorId));

        completeOrderInternal(order, operatorId, "手动完成");
    }

    @Override
    public long getRemainingCountdownMinutes(Long orderId) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null || order.getCountdownEndTime() == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = order.getCountdownEndTime();

        if (endTime.isBefore(now)) {
            return 0; // 已过期
        }

        return java.time.Duration.between(now, endTime).toMinutes();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartCountdown(Long orderId, Integer duration, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("processing")) {
            throw new BusinessException("只有进行中的订单才能重新开始倒计时");
        }

        // 如果没有指定时长，使用配置的默认时长
        if (duration == null || duration <= 0) {
            duration = countdownConfigService.getIntConfigValue("default_countdown_duration", 60);
        }

        // 获取配置的自动完成设置
        boolean autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("auto_complete_enabled", true);

        // 记录历史 - 重新开始倒计时
        Integer oldDuration = order.getCountdownDuration();
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "restart", 
                oldDuration, duration, reason, operatorId, getOperatorName(operatorId));

        // 更新倒计时
        LocalDateTime now = LocalDateTime.now();
        order.setCountdownDuration(duration);
        order.setCountdownStartTime(now);
        order.setCountdownEndTime(now.plusMinutes(duration));
        order.setAutoCompleteEnabled(autoCompleteEnabled ? 1 : 0); // 使用配置的自动完成设置
        
        courseOrderMapper.updateById(order);

        log.info("重新开始倒计时：orderId={}, duration={}分钟, autoComplete={}, operatorId={}, reason={}", 
                orderId, duration, autoCompleteEnabled, operatorId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchOrderStatus(Long orderId, Integer newStatus, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        // 修改状态检查逻辑：允许待处理状态的订单切换状态
        if (order.getOrderStatus() != getOrderStatus("processing") && order.getOrderStatus() != getOrderStatus("pending")) {
            throw new BusinessException("只有进行中或待处理状态的订单才能切换状态");
        }

        // 记录历史
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "status_switch", 
                order.getCountdownDuration(), null, reason, operatorId, getOperatorName(operatorId));

        // 更新订单状态
        order.setOrderStatus(newStatus);
        order.setCountdownEndTime(LocalDateTime.now()); // 标记倒计时结束时间
        
        courseOrderMapper.updateById(order);

        log.info("切换订单状态：orderId={}, newStatus={}, operatorId={}, reason={}", 
                orderId, newStatus, operatorId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startNextTaskCountdown(Long orderId, Integer duration, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("pending")) {
            throw new BusinessException("只有待处理状态的订单才能开始下一步任务倒计时");
        }

        // 如果没有指定时长，使用配置的默认时长
        if (duration == null || duration <= 0) {
            duration = countdownConfigService.getIntConfigValue("default_countdown_duration", 60);
        }

        // 获取配置的自动完成设置
        boolean autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("auto_complete_enabled", true);

        // 记录历史 - 开始下一步任务倒计时
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "next_task_start", 
                order.getCountdownDuration(), duration, reason, operatorId, getOperatorName(operatorId));

        // 更新订单状态为进行中，并启动新的倒计时
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(getOrderStatus("processing"));
        order.setCountdownDuration(duration);
        order.setCountdownStartTime(now);
        order.setCountdownEndTime(now.plusMinutes(duration));
        order.setAutoCompleteEnabled(autoCompleteEnabled ? 1 : 0); // 使用配置的自动完成设置
        
        courseOrderMapper.updateById(order);

        log.info("开始下一步任务倒计时：orderId={}, duration={}分钟, autoComplete={}, operatorId={}, reason={}", 
                orderId, duration, autoCompleteEnabled, operatorId, reason);
    }

    /**
     * 开始下一步任务倒计时（支持自定义自动完成设置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void startNextTaskCountdown(Long orderId, Integer duration, Boolean autoCompleteEnabled, 
                                       Integer autoCompleteStatus, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("pending")) {
            throw new BusinessException("只有待处理状态的订单才能开始下一步任务倒计时");
        }

        // 如果没有指定时长，使用配置的默认时长
        if (duration == null || duration <= 0) {
            duration = countdownConfigService.getIntConfigValue("default_countdown_duration", 60);
        }

        // 使用传入的自动完成设置，如果没有传入则使用配置
        if (autoCompleteEnabled == null) {
            autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("auto_complete_enabled", true);
        }

        // 记录历史 - 开始下一步任务倒计时
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "next_task_start", 
                order.getCountdownDuration(), duration, reason, operatorId, getOperatorName(operatorId));

        // 更新订单状态为进行中，并启动新的倒计时
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(getOrderStatus("processing"));
        order.setCountdownDuration(duration);
        order.setCountdownStartTime(now);
        order.setCountdownEndTime(now.plusMinutes(duration));
        order.setAutoCompleteEnabled(autoCompleteEnabled ? 1 : 0);
        
        courseOrderMapper.updateById(order);

        log.info("开始下一步任务倒计时：orderId={}, duration={}分钟, autoComplete={}, operatorId={}, reason={}", 
                orderId, duration, autoCompleteEnabled, operatorId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startExamCountdown(Long orderId, Integer duration, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("pending")) {
            throw new BusinessException("只有待处理状态的订单才能开始考试倒计时");
        }

        // 如果没有指定时长，使用配置的默认考试倒计时时长
        if (duration == null || duration <= 0) {
            duration = countdownConfigService.getIntConfigValue("default_exam_countdown_duration", 120);
        }

        // 获取配置的考试自动完成设置
        boolean autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("exam_auto_complete_enabled", true);

        // 记录历史 - 开始考试倒计时
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "exam_start", 
                order.getExamCountdownDuration(), duration, reason, operatorId, getOperatorName(operatorId));

        // 更新订单状态为考试中，并启动考试倒计时
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(getOrderStatus("exam_processing"));
        order.setExamCountdownDuration(duration);
        order.setExamCountdownStartTime(now);
        order.setExamCountdownEndTime(now.plusMinutes(duration));
        order.setExamAutoCompleteEnabled(autoCompleteEnabled ? 1 : 0);
        
        courseOrderMapper.updateById(order);

        log.info("开始考试倒计时：orderId={}, duration={}分钟, autoComplete={}, operatorId={}, reason={}", 
                orderId, duration, autoCompleteEnabled, operatorId, reason);
    }

    @Override
    public List<CourseOrder> getActiveExamCountdownOrders() {
        return courseOrderMapper.selectList(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getIsSelfOperated, 1)
                .in(CourseOrder::getOrderStatus, getOrderStatus("exam_processing"), getOrderStatus("exam_pending"))
                .isNotNull(CourseOrder::getExamCountdownEndTime)
                // 包含已过期的订单，让用户能看到并选择下一步操作
                .orderByAsc(CourseOrder::getExamCountdownEndTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeExam(Long orderId, Long operatorId) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("exam_processing")) {
            throw new BusinessException("只有考试中的订单才能完成考试");
        }

        // 记录历史 - 手动完成考试
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "exam_complete", 
                order.getExamCountdownDuration(), 0, "手动完成考试", operatorId, getOperatorName(operatorId));

        completeExamInternal(order, operatorId, "手动完成考试");
    }

    @Override
    public long getRemainingExamCountdownMinutes(Long orderId) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null || order.getExamCountdownEndTime() == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = order.getExamCountdownEndTime();
        
        if (now.isAfter(endTime)) {
            return 0;
        }

        return java.time.Duration.between(now, endTime).toMinutes();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustExamCountdown(Long orderId, Integer newDuration, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("exam_processing")) {
            throw new BusinessException("只有考试中的订单才能调整考试倒计时");
        }

        Integer oldDuration = order.getExamCountdownDuration();
        
        // 记录历史 - 调整考试倒计时
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "exam_adjust", 
                oldDuration, newDuration, reason, operatorId, getOperatorName(operatorId));

        // 更新考试倒计时
        LocalDateTime now = LocalDateTime.now();
        order.setExamCountdownDuration(newDuration);
        order.setExamCountdownStartTime(now);
        order.setExamCountdownEndTime(now.plusMinutes(newDuration));
        
        courseOrderMapper.updateById(order);

        log.info("调整考试倒计时：orderId={}, oldDuration={}, newDuration={}, operatorId={}, reason={}", 
                orderId, oldDuration, newDuration, operatorId, reason);
    }

    /**
     * 内部完成考试方法
     */
    private void completeExamInternal(CourseOrder order, Long operatorId, String reason) {
        // 更新订单状态为考试完成
        order.setOrderStatus(getOrderStatus("exam_completed"));
        
        // 清除考试倒计时信息
        order.setExamCountdownDuration(null);
        order.setExamCountdownStartTime(null);
        order.setExamCountdownEndTime(null);
        order.setExamAutoCompleteEnabled(0);
        
        // 设置考试完成时间
        order.setExamEndTime(LocalDateTime.now());
        
        courseOrderMapper.updateById(order);

        // 记录操作日志
        if (operatorId != null && operatorId > 0) {
            operationLogService.log(operatorId, "完成自营订单考试",
                    String.format("订单%s考试完成", order.getOrderNo()),
                    BigDecimal.ZERO, null);
        }

        log.info("考试完成：orderId={}, orderNo={}, reason={}", order.getId(), order.getOrderNo(), reason);
    }

    /**
     * 获取操作人姓名
     */
    private String getOperatorName(Long operatorId) {
        if (operatorId == null || operatorId == 0) {
            return "系统";
        }
        
        try {
            User user = userMapper.selectById(operatorId);
            return user != null ? user.getUsername() : "未知用户";
        } catch (Exception e) {
            log.warn("获取操作人姓名失败：operatorId={}", operatorId);
            return "未知用户";
        }
    }

    /**
     * 内部完成订单方法
     */
    private void completeOrderInternal(CourseOrder order, Long operatorId, String reason) {
        completeOrderInternal(order, operatorId, reason, getOrderStatus("completed"));
    }

    private void completeOrderInternal(CourseOrder order, Long operatorId, String reason, Integer targetStatus) {
        // 1. 更新订单状态
        order.setOrderStatus(targetStatus);
        order.setDockStatus(getDockStatus("success"));
        order.setCountdownEndTime(LocalDateTime.now()); // 标记倒计时结束时间
        order.setProgress("100%");
        order.setFinishedChapters(order.getTotalChapters());

        // 2. 更新订单
        courseOrderMapper.updateById(order);

        // 3. 记录操作日志
        String logDesc = String.format("完成自营订单：%s，原因：%s", order.getOrderNo(), reason);
        operationLogService.log(operatorId, "完成订单", logDesc, BigDecimal.ZERO, null);

        log.info("自营订单完成：orderId={}, orderNo={}, operatorId={}, reason={}", 
                order.getId(), order.getOrderNo(), operatorId, reason);
    }
}
