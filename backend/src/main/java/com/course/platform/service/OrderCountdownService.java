package com.course.platform.service;

import com.course.platform.domain.entity.CourseOrder;

import java.util.List;

/**
 * 订单倒计时服务接口
 * 处理自营订单的倒计时和自动完成功能
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于自营订单倒计时需求设计
 */
public interface OrderCountdownService {

    /**
     * 检查并处理过期的倒计时订单
     * 定时任务调用此方法
     */
    void processExpiredCountdownOrders();

    /**
     * 获取所有正在倒计时的订单
     * 
     * @return 倒计时订单列表
     */
    List<CourseOrder> getActiveCountdownOrders();

    /**
     * 手动完成订单
     * 
     * @param orderId 订单ID
     * @param operatorId 操作人ID
     */
    void completeOrder(Long orderId, Long operatorId);

    /**
     * 获取订单剩余倒计时时间（分钟）
     * 
     * @param orderId 订单ID
     * @return 剩余时间，如果已过期返回0
     */
    long getRemainingCountdownMinutes(Long orderId);

    /**
     * 重新开始倒计时
     * 
     * @param orderId 订单ID
     * @param duration 倒计时时长（分钟）
     * @param operatorId 操作人ID
     * @param reason 操作原因
     */
    void restartCountdown(Long orderId, Integer duration, Long operatorId, String reason);

    /**
     * 切换订单状态（倒计时结束后）
     * 
     * @param orderId 订单ID
     * @param newStatus 新状态
     * @param operatorId 操作人ID
     * @param reason 操作原因
     */
    void switchOrderStatus(Long orderId, Integer newStatus, Long operatorId, String reason);

    /**
     * 开始下一步任务倒计时
     * 将订单切换到进行中状态并启动新的倒计时
     * 
     * @param orderId 订单ID
     * @param duration 倒计时时长（分钟）
     * @param operatorId 操作人ID
     * @param reason 操作原因
     */
    void startNextTaskCountdown(Long orderId, Integer duration, Long operatorId, String reason);

    /**
     * 开始考试倒计时
     * 将订单切换到考试中状态并启动考试倒计时
     * 
     * @param orderId 订单ID
     * @param duration 倒计时时长（分钟）
     * @param operatorId 操作人ID
     * @param reason 操作原因
     */
    void startExamCountdown(Long orderId, Integer duration, Long operatorId, String reason);

    /**
     * 获取所有正在考试倒计时的订单
     * 
     * @return 考试倒计时订单列表
     */
    List<CourseOrder> getActiveExamCountdownOrders();

    /**
     * 手动完成考试
     * 
     * @param orderId 订单ID
     * @param operatorId 操作人ID
     */
    void completeExam(Long orderId, Long operatorId);

    /**
     * 获取订单剩余考试倒计时时间（分钟）
     * 
     * @param orderId 订单ID
     * @return 剩余时间，如果已过期返回0
     */
    long getRemainingExamCountdownMinutes(Long orderId);

    /**
     * 调整考试倒计时
     * 
     * @param orderId 订单ID
     * @param newDuration 新的倒计时时长（分钟）
     * @param operatorId 操作人ID
     * @param reason 操作原因
     */
    void adjustExamCountdown(Long orderId, Integer newDuration, Long operatorId, String reason);
}
