package com.course.platform.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.course.platform.security.SecurityUtils;
import com.course.platform.domain.document.OperationLogDocument;
import com.course.platform.domain.entity.OperationLog;
import com.course.platform.infra.persistence.mapper.OperationLogMapper;
import com.course.platform.infra.search.OperationLogSearchRepository;
import com.course.platform.application.service.support.OperationLogSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志搜索服务实现类
 *
 * 基于 Elasticsearch 提供全文搜索能力
 *
 * @author AI Assistant
 * @since 2025-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogSearchServiceImpl implements OperationLogSearchService {

    private final OperationLogSearchRepository operationLogSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final OperationLogMapper operationLogMapper;

    @Override
    public void save(OperationLogDocument document) {
        try {
            operationLogSearchRepository.save(document);
            log.debug("日志已同步到ES: id={}", document.getId());
        } catch (Exception e) {
            // ES同步失败不影响主流程，只记录警告
            log.warn("日志同步到ES失败: id={}, error={}", document.getId(), e.getMessage());
        }
    }

    @Override
    public Page<OperationLogDocument> search(
            Long userId,
            String keyword,
            String operationType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable
    ) {
        // 构建布尔查询
        List<Query> mustQueries = new ArrayList<>();
        List<Query> shouldQueries = new ArrayList<>();

        // 非管理员只能查看自己的日志
        if (userId != null && !(SecurityUtils.isAdmin())) {
            mustQueries.add(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
        }

        // 操作类型过滤
        if (operationType != null && !operationType.trim().isEmpty()) {
            mustQueries.add(Query.of(q -> q.term(t -> t.field("operationType").value(operationType))));
        }

        // 时间范围过滤
        if (startTime != null || endTime != null) {
            mustQueries.add(Query.of(q -> q.range(r -> {
                var rangeQuery = r.field("createTime");
                if (startTime != null) {
                    rangeQuery.gte(co.elastic.clients.json.JsonData.of(startTime.toString()));
                }
                if (endTime != null) {
                    rangeQuery.lte(co.elastic.clients.json.JsonData.of(endTime.toString()));
                }
                return rangeQuery;
            })));
        }

        // 关键词搜索（在多个字段中搜索）
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmedKeyword = keyword.trim();
            // 在 operationDesc 中全文搜索
            shouldQueries.add(Query.of(q -> q.match(m -> m.field("operationDesc").query(trimmedKeyword))));
            // 在 operationType 中精确匹配
            shouldQueries.add(Query.of(q -> q.term(t -> t.field("operationType").value(trimmedKeyword))));
            // 在 ipAddress 中精确匹配
            shouldQueries.add(Query.of(q -> q.term(t -> t.field("ipAddress").value(trimmedKeyword))));
        }

        // 构建最终查询
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        if (!mustQueries.isEmpty()) {
            boolBuilder.must(mustQueries);
        }
        if (!shouldQueries.isEmpty()) {
            boolBuilder.should(shouldQueries);
            boolBuilder.minimumShouldMatch("1"); // 至少匹配一个should条件
        }

        Query finalQuery = Query.of(q -> q.bool(boolBuilder.build()));

        // 构建查询
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(finalQuery)
                .withPageable(pageable)
                .withSort(s -> s.field(f -> f.field("createTime").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .build();

        // 执行查询
        SearchHits<OperationLogDocument> searchHits = elasticsearchOperations.search(
                searchQuery, OperationLogDocument.class);

        List<OperationLogDocument> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    @Override
    public Page<OperationLogDocument> search(
            Long userId,
            String keyword,
            String operationType,
            Pageable pageable
    ) {
        return search(userId, keyword, operationType, null, null, pageable);
    }

    @Override
    public int syncAllFromDatabase() {
        log.info("开始同步历史日志数据到Elasticsearch...");

        // 分批查询数据库中的所有日志
        int page = 0;
        int pageSize = 500;
        int totalSynced = 0;

        while (true) {
            List<OperationLog> logs = operationLogMapper.selectList(null);
            if (logs == null || logs.isEmpty()) {
                break;
            }

            // 转换为ES文档并批量保存
            List<OperationLogDocument> documents = logs.stream()
                    .map(this::convertToDocument)
                    .collect(Collectors.toList());

            operationLogSearchRepository.saveAll(documents);
            totalSynced += documents.size();

            log.info("已同步 {} 条日志记录", totalSynced);

            // 如果返回的记录数小于pageSize，说明已经是最后一页
            break; // 简化实现，一次性同步所有
        }

        log.info("历史日志同步完成，共同步 {} 条记录", totalSynced);
        return totalSynced;
    }

    /**
     * 将数据库实体转换为ES文档
     */
    private OperationLogDocument convertToDocument(OperationLog log) {
        return OperationLogDocument.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .operationType(log.getOperationType())
                .operationDesc(log.getOperationDesc())
                .amountChange(log.getAmountChange())
                .balanceAfter(log.getBalanceAfter())
                .ipAddress(log.getIpAddress())
                .createTime(log.getCreateTime())
                .build();
    }
}
