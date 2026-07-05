package com.course.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.OperationLog;

import java.math.BigDecimal;

/**
 * 操作日志服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface OperationLogService {

    /**
     * 记录操作日志
     * 
     * @param userId 用户ID
     * @param operationType 操作类型
     * @param operationDesc 操作描述
     * @param amountChange 金额变动
     * @param balanceAfter 操作后余额
     */
    void log(Long userId, String operationType, String operationDesc, 
             BigDecimal amountChange, BigDecimal balanceAfter);

    /**
     * 分页查询操作日志
     * 
     * @param userId 用户ID
     * @param operationType 操作类型
     * @param keyword 搜索关键词（模糊匹配操作描述）
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 日志分页数据
     */
    IPage<OperationLog> queryLogs(Long userId, String operationType, String keyword, Integer page, Integer pageSize);
}

