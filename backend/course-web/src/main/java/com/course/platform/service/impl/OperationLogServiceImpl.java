package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.SecurityUtils;
import com.course.platform.domain.document.OperationLogDocument;
import com.course.platform.domain.entity.OperationLog;
import com.course.platform.infra.persistence.mapper.OperationLogMapper;
import com.course.platform.application.service.support.OperationLogSearchService;
import com.course.platform.application.service.support.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 操作日志服务实现类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final OperationLogSearchService operationLogSearchService;

    @Override
    public void log(Long userId, String operationType, String operationDesc,
                   BigDecimal amountChange, BigDecimal balanceAfter) {
        try {
            OperationLog logEntity = new OperationLog();
            logEntity.setUserId(userId);
            logEntity.setOperationType(operationType);
            logEntity.setOperationDesc(operationDesc);
            logEntity.setAmountChange(amountChange != null ? amountChange : BigDecimal.ZERO);
            logEntity.setBalanceAfter(balanceAfter);

            // 手动设置创建时间，确保时间字段被正确填充
            logEntity.setCreateTime(LocalDateTime.now());

            // 获取真实IP地址
            try {
                logEntity.setIpAddress(com.course.platform.shared.util.ServletUtil.getClientIp());
            } catch (Exception e) {
                logEntity.setIpAddress("127.0.0.1");
            }

            operationLogMapper.insert(logEntity);
            log.info("操作日志记录成功：userId={}, type={}, desc={}", userId, operationType, operationDesc);

            // 同步到 Elasticsearch（异步，不影响主流程）
            try {
                OperationLogDocument document = OperationLogDocument.builder()
                        .id(logEntity.getId())
                        .userId(logEntity.getUserId())
                        .operationType(logEntity.getOperationType())
                        .operationDesc(logEntity.getOperationDesc())
                        .amountChange(logEntity.getAmountChange())
                        .balanceAfter(logEntity.getBalanceAfter())
                        .ipAddress(logEntity.getIpAddress())
                        .createTime(logEntity.getCreateTime())
                        .build();
                operationLogSearchService.save(document);
            } catch (Exception esError) {
                log.warn("ES同步失败（不影响主流程）：{}", esError.getMessage());
            }
        } catch (Exception e) {
            log.error("记录操作日志失败：userId={}, type={}, error={}", userId, operationType, e.getMessage(), e);
        }
    }

    @Override
    public IPage<OperationLog> queryLogs(Long userId, String operationType, String keyword, Integer page, Integer pageSize) {
        Page<OperationLog> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<OperationLog> queryWrapper = new LambdaQueryWrapper<>();

        // 非管理员只能查看自己的日志
        if (!(SecurityUtils.isAdmin() || Constants.DEFAULT_ADMIN_ID.equals(userId))) {
            queryWrapper.eq(OperationLog::getUserId, userId);
        }

        if (operationType != null && !operationType.trim().isEmpty()) {
            queryWrapper.eq(OperationLog::getOperationType, operationType);
        }

        // 模糊搜索：支持在操作描述中搜索关键词
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like(OperationLog::getOperationDesc, keyword.trim());
        }

        queryWrapper.orderByDesc(OperationLog::getCreateTime);

        return operationLogMapper.selectPage(pageObj, queryWrapper);
    }
}
