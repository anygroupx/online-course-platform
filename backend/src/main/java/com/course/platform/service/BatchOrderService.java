package com.course.platform.service;

import com.course.platform.domain.dto.BatchOrderRequest;

import java.util.List;

/**
 * 批量订单服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface BatchOrderService {

    /**
     * 批量创建订单
     * 
     * @param request 批量订单请求
     * @param userId 用户ID
     * @return 订单ID列表
     */
    List<Long> batchCreateOrders(BatchOrderRequest request, Long userId);
}

