package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.vo.StatisticsResponse;
import com.course.platform.application.service.system.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计数据控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "数据统计", description = "统计数据查询接口")
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 获取统计数据
     */
    @Operation(summary = "获取统计数据", description = "获取订单、用户等统计信息")
    @GetMapping
    public Result<StatisticsResponse> getStatistics(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        StatisticsResponse statistics = statisticsService.getStatistics(userId);
        return Result.success(statistics);
    }
}

