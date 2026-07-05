package com.course.platform.infra.search;

import com.course.platform.domain.document.OperationLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 操作日志 Elasticsearch Repository
 * 
 * 提供基础的 CRUD 操作和自定义查询方法
 * 
 * @author AI Assistant
 * @since 2025-12-22
 */
@Repository
public interface OperationLogSearchRepository extends ElasticsearchRepository<OperationLogDocument, Long> {

    /**
     * 根据用户ID查询日志
     */
    List<OperationLogDocument> findByUserId(Long userId);

    /**
     * 根据操作类型查询日志
     */
    List<OperationLogDocument> findByOperationType(String operationType);

    /**
     * 根据IP地址查询日志
     */
    List<OperationLogDocument> findByIpAddress(String ipAddress);
}
