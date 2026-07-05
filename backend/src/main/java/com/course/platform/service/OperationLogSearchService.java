package com.course.platform.service;

import com.course.platform.domain.document.OperationLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * 操作日志搜索服务接口
 * 
 * 提供基于 Elasticsearch 的全文搜索能力
 * 
 * @author AI Assistant
 * @since 2025-12-22
 */
public interface OperationLogSearchService {

    /**
     * 保存日志到 Elasticsearch
     * 
     * @param document 日志文档
     */
    void save(OperationLogDocument document);

    /**
     * 全文搜索日志
     * 
     * @param userId 用户ID（null 表示管理员查询所有）
     * @param keyword 搜索关键词（在 operationType 和 operationDesc 中搜索）
     * @param operationType 操作类型过滤（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    Page<OperationLogDocument> search(
            Long userId,
            String keyword,
            String operationType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable
    );

    /**
     * 简单关键词搜索（向后兼容）
     * 
     * @param userId 用户ID
     * @param keyword 关键词
     * @param operationType 操作类型
     * @param pageable 分页参数
     * @return 搜索结果分页
     */
    Page<OperationLogDocument> search(
            Long userId,
            String keyword,
            String operationType,
            Pageable pageable
    );

    /**
     * 同步历史数据到 Elasticsearch
     * 
     * @return 同步的记录数
     */
    int syncAllFromDatabase();
}
