package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.common.constant.Constants;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.application.service.order.CountdownHistoryService;
import com.course.platform.application.service.course.CountdownConfigService;
import com.course.platform.application.service.platform.ExamCountdownService;
import com.course.platform.application.service.support.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试倒计时服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于考试倒计时管理需求设计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamCountdownServiceImpl implements ExamCountdownService {

    private final CourseOrderMapper courseOrderMapper;
    private final UserMapper userMapper;
    private final CountdownHistoryService countdownHistoryService;
    private final CountdownConfigService countdownConfigService;
    private final OperationLogService operationLogService;

    /**
     * 获取订单状态值
     */
    private static int getOrderStatus(String key) {
        return SystemVariableCache.getStatusValue("order_status", key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processExpiredExamCountdownOrders() {
        try {
            // 查询所有已过期的考试倒计时订单
            List<CourseOrder> expiredOrders = courseOrderMapper.selectList(
                new LambdaQueryWrapper<CourseOrder>()
                    .eq(CourseOrder::getIsSelfOperated, 1)
                    .in(CourseOrder::getOrderStatus, getOrderStatus("exam_processing"))
                    .isNotNull(CourseOrder::getExamCountdownEndTime)
                    .lt(CourseOrder::getExamCountdownEndTime, LocalDateTime.now())
            );

            log.info("发现{}个过期的考试倒计时订单", expiredOrders.size());

            for (CourseOrder order : expiredOrders) {
                try {
                    // 检查是否启用自动完成
                    boolean autoCompleteEnabled = order.getExamAutoCompleteEnabled() != null && 
                                                order.getExamAutoCompleteEnabled() == 1;
                    
                    if (autoCompleteEnabled) {
                        // 自动完成考试
                        Integer autoCompleteStatus = countdownConfigService.getIntConfigValue("exam_auto_complete_status", getOrderStatus("exam_completed"));
                        
                        // 记录历史 - 考试自动完成
                        countdownHistoryService.recordHistory(order.getId(), order.getOrderNo(), "exam_complete", 
                                order.getExamCountdownDuration(), 0, "考试倒计时结束，自动完成考试", 0L, "系统");
                        
                        completeExamInternal(order, 0L, "考试倒计时结束，自动完成");
                        
                        log.info("考试倒计时结束，自动完成订单：orderId={}, orderNo={}, status={}", 
                                order.getId(), order.getOrderNo(), autoCompleteStatus);
                    } else {
                        // 记录历史 - 考试倒计时过期，等待手动处理
                        countdownHistoryService.recordHistory(order.getId(), order.getOrderNo(), "exam_expired", 
                                order.getExamCountdownDuration(), 0, "考试倒计时已结束，等待手动处理", 0L, "系统");
                        
                        // 标记为需要手动处理，但保持考试倒计时信息
                        order.setOrderStatus(getOrderStatus("exam_pending"));
                        order.setRemarks((order.getRemarks() != null ? order.getRemarks() + "\n" : "") +
                                String.format("[%s] 考试倒计时已结束，订单进入待考试状态", 
                                        LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                        courseOrderMapper.updateById(order);
                        log.info("考试倒计时结束，进入待考试状态：orderId={}, orderNo={}", order.getId(), order.getOrderNo());
                    }
                } catch (Exception e) {
                    log.error("处理过期考试订单失败：orderId={}, error={}", order.getId(), e.getMessage(), e);
                }
            }

            log.info("过期考试倒计时订单处理完成，共处理{}个订单", expiredOrders.size());

        } catch (Exception e) {
            log.error("定时处理过期考试倒计时订单失败：{}", e.getMessage(), e);
        }
    }

    @Override
    public List<CourseOrder> getActiveExamCountdownOrders() {
        // 查询所有有考试倒计时记录的订单，包括已过期的，保持可见性
        return courseOrderMapper.selectList(new LambdaQueryWrapper<CourseOrder>()
                .eq(CourseOrder::getIsSelfOperated, 1)
                .in(CourseOrder::getOrderStatus, 
                    getOrderStatus("exam_processing"), 
                    getOrderStatus("exam_pending"))
                .isNotNull(CourseOrder::getExamCountdownEndTime)
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
    public void startExamCountdown(Long orderId, Integer duration, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        if (order.getOrderStatus() != getOrderStatus("exam_pending")) {
            throw new BusinessException("只有待考试状态的订单才能开始考试倒计时");
        }

        // 如果没有指定时长，使用配置的默认时长
        if (duration == null || duration <= 0) {
            duration = countdownConfigService.getIntConfigValue("default_exam_countdown_duration", 120);
        }

        // 获取配置的自动完成设置
        boolean autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("exam_auto_complete_enabled", true);

        // 记录历史 - 开始考试倒计时
        countdownHistoryService.recordHistory(orderId, order.getOrderNo(), "exam_start", 
                order.getExamCountdownDuration(), duration, reason, operatorId, getOperatorName(operatorId));

        // 更新订单状态为考试中，并启动新的考试倒计时
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchToExamStatus(Long orderId, Integer newStatus, Long operatorId, String reason) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (order.getIsSelfOperated() == null || order.getIsSelfOperated() != 1) {
            throw new BusinessException("只有自营订单才能使用此功能");
        }

        Integer oldStatus = order.getOrderStatus();
        order.setOrderStatus(newStatus);

        // 如果切换到考试中状态，启动考试倒计时
        if (newStatus == getOrderStatus("exam_processing")) {
            Integer duration = countdownConfigService.getIntConfigValue("default_exam_countdown_duration", 120);
            boolean autoCompleteEnabled = countdownConfigService.getBooleanConfigValue("exam_auto_complete_enabled", true);
            
            LocalDateTime now = LocalDateTime.now();
            order.setExamCountdownDuration(duration);
            order.setExamCountdownStartTime(now);
            order.setExamCountdownEndTime(now.plusMinutes(duration));
            order.setExamAutoCompleteEnabled(autoCompleteEnabled ? 1 : 0);
            
            log.info("自营订单考试倒计时启动：orderId={}, duration={}分钟, autoComplete={}", 
                    orderId, duration, autoCompleteEnabled);
        } else {
            // 其他状态时清除考试倒计时
            order.setExamCountdownDuration(null);
            order.setExamCountdownStartTime(null);
            order.setExamCountdownEndTime(null);
            order.setExamAutoCompleteEnabled(0);
        }

        // 更新订单
        courseOrderMapper.updateById(order);

        // 记录操作日志
        operationLogService.log(operatorId, "切换自营订单考试状态",
                String.format("订单%s从状态%d切换到状态%d", order.getOrderNo(), oldStatus, newStatus),
                BigDecimal.ZERO, null);

        log.info("切换订单考试状态：orderId={}, oldStatus={}, newStatus={}, operatorId={}, reason={}", 
                orderId, oldStatus, newStatus, operatorId, reason);
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
        if (operatorId == null || operatorId <= 0) {
            return "系统";
        }
        
        try {
            User operator = userMapper.selectById(operatorId);
            return operator != null ? operator.getUsername() : "未知用户";
        } catch (Exception e) {
            log.warn("获取操作人姓名失败：operatorId={}", operatorId);
            return "未知用户";
        }
    }
}
