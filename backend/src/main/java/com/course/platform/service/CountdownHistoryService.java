package com.course.platform.service;

import com.course.platform.domain.entity.CountdownHistory;
import com.course.platform.domain.dto.CountdownHistoryDTO;

import java.util.List;

/**
 * 倒计时历史记录服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
public interface CountdownHistoryService {

    /**
     * 记录倒计时操作历史
     * 
     * @param orderId 订单ID
     * @param orderNo 订单号
     * @param operationType 操作类型
     * @param oldDuration 操作前时长
     * @param newDuration 操作后时长
     * @param reason 操作原因
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     */
    void recordHistory(Long orderId, String orderNo, String operationType, 
                      Integer oldDuration, Integer newDuration, String reason, 
                      Long operatorId, String operatorName);

    /**
     * 获取订单的倒计时历史记录
     * 
     * @param orderId 订单ID
     * @return 历史记录列表
     */
    List<CountdownHistory> getOrderHistory(Long orderId);

    /**
     * 获取所有倒计时历史记录（分页）
     * 
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 历史记录列表
     */
    List<CountdownHistory> getAllHistory(Integer pageNum, Integer pageSize);

    /**
     * 获取所有倒计时历史记录（分页，包含账号和订单状态）
     * 
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 历史记录DTO列表
     */
    List<CountdownHistoryDTO> getAllHistoryWithDetails(Integer pageNum, Integer pageSize);
}
