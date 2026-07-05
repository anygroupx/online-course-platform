package com.course.platform.controller;

import com.course.platform.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 用于 Docker 健康检查和服务监控
 *
 * @author AI Assistant
 * @since 2025-12-09
 */
@Tag(name = "健康检查", description = "系统健康检查接口")
@RestController
@RequestMapping("")
public class HealthController {

    /**
     * 健康检查端点
     * Docker 容器使用此端点进行健康检查
     */
    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "online-course-platform");
        data.put("timestamp", LocalDateTime.now());
        return Result.success(data);
    }

    /**
     * 简单ping端点
     */
    @Operation(summary = "Ping", description = "简单的连通性测试")
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
