package com.course.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.constant.Constants;
import com.course.platform.security.SecurityUtils;
import com.course.platform.common.result.Result;
import com.course.platform.domain.document.OperationLogDocument;
import com.course.platform.domain.entity.OperationLog;
import com.course.platform.application.service.support.OperationLogSearchService;
import com.course.platform.application.service.support.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 操作日志控制器
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "日志管理", description = "操作日志查询接口")
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/logs")
@RequiredArgsConstructor
@RestController
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final OperationLogSearchService operationLogSearchService;

    /**
     * 查询操作日志（数据库版本，作为降级方案）
     */
    @Operation(summary = "查询操作日志", description = "分页查询操作日志，支持模糊搜索")
    @GetMapping
    public Result<IPage<OperationLog>> queryLogs(@RequestParam(required = false) String operationType,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        IPage<OperationLog> result = operationLogService.queryLogs(userId, operationType, keyword, page, pageSize);
        return Result.success(result);
    }

    /**
     * ES全文搜索日志（支持时间范围）
     */
    @Operation(summary = "ES全文搜索日志", description = "使用Elasticsearch进行全文搜索，支持时间范围过滤")
    @GetMapping("/search")
    public Result<Page<OperationLogDocument>> searchLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Page<OperationLogDocument> result = operationLogSearchService.search(
                userId, keyword, operationType, startTime, endTime, PageRequest.of(page, size));
        return Result.success(result);
    }

    /**
     * 同步历史日志到ES（仅管理员）
     */
    @Operation(summary = "同步历史日志到ES", description = "将数据库中的历史日志同步到Elasticsearch（仅管理员）")
    @PostMapping("/sync-to-es")
    public Result<Integer> syncToElasticsearch(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SecurityUtils.requireAdmin();
        int count = operationLogSearchService.syncAllFromDatabase();
        return Result.success("同步完成，共同步 " + count + " 条记录", count);
    }
}
