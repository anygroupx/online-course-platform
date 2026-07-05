package com.course.platform.controller;

import com.course.platform.shared.result.Result;
import com.course.platform.domain.dto.CustomerServiceMessageDTO;
import com.course.platform.domain.entity.CustomerServiceSession;
import com.course.platform.domain.vo.CustomerServiceMessageVO;
import com.course.platform.domain.vo.CustomerServiceSessionVO;
import com.course.platform.service.CustomerServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客服管理控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Slf4j
@RestController
@RequestMapping("/customer-service")
@RequiredArgsConstructor
@Tag(name = "客服管理", description = "在线客服相关接口")
public class CustomerServiceController {

    private final CustomerServiceService customerServiceService;

    /**
     * 创建或获取用户会话
     */
    @PostMapping("/session")
    @Operation(summary = "创建或获取用户会话", description = "用户创建或获取客服会话")
    public Result<CustomerServiceSession> createOrGetSession() {
        Long userId = getCurrentUserId();
        CustomerServiceSession session = customerServiceService.createOrGetSession(userId);
        return Result.success(session);
    }

    /**
     * 发送消息
     */
    @PostMapping("/message")
    @Operation(summary = "发送消息", description = "用户或客服发送消息")
    public Result<Boolean> sendMessage(@RequestBody CustomerServiceMessageDTO messageDTO) {
        log.info("收到发送消息请求：{}", messageDTO);
        Long userId = getCurrentUserId();
        log.info("当前用户ID：{}", userId);
        Boolean success = customerServiceService.sendMessage(messageDTO, userId);
        log.info("消息发送结果：{}", success);
        return Result.success(success);
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/session/{sessionId}/messages")
    @Operation(summary = "获取会话消息列表", description = "获取指定会话的消息列表")
    public Result<List<CustomerServiceMessageVO>> getSessionMessages(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        
        List<CustomerServiceMessageVO> messages = customerServiceService.getSessionMessages(sessionId);
        return Result.success(messages);
    }

    /**
     * 标记消息为已读
     */
    @PostMapping("/session/{sessionId}/read")
    @Operation(summary = "标记消息为已读", description = "用户标记消息为已读")
    public Result<Boolean> markMessagesAsRead(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        
        Long userId = getCurrentUserId();
        Boolean success = customerServiceService.markMessagesAsRead(sessionId, userId);
        return Result.success(success);
    }

    /**
     * 获取用户未读消息数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "获取未读消息数量", description = "获取用户未读消息数量")
    public Result<Integer> getUnreadCount() {
        Long userId = getCurrentUserId();
        Integer count = customerServiceService.getUnreadCount(userId);
        return Result.success(count);
    }

    /**
     * 结束会话
     */
    @PostMapping("/session/{sessionId}/end")
    @Operation(summary = "结束会话", description = "用户结束客服会话")
    public Result<Boolean> endSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        
        Long userId = getCurrentUserId();
        Boolean success = customerServiceService.endSession(sessionId, userId);
        return Result.success(success);
    }

    /**
     * 分配客服（管理员接口）
     */
    @PostMapping("/session/{sessionId}/assign")
    @Operation(summary = "分配客服", description = "管理员为会话分配客服")
    public Result<Boolean> assignCustomerService(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "客服ID") @RequestParam Long customerServiceId) {
        
        Boolean success = customerServiceService.assignCustomerService(sessionId, customerServiceId);
        return Result.success(success);
    }

    /**
     * 获取所有会话列表（管理端）
     */
    @GetMapping("/admin/sessions")
    @Operation(summary = "获取所有会话列表", description = "管理端获取所有用户会话")
    public Result<List<CustomerServiceSessionVO>> getAllSessions(
            @Parameter(description = "会话状态") @RequestParam(required = false) Integer status) {
        
        List<CustomerServiceSessionVO> sessions = customerServiceService.getAllSessions(status);
        return Result.success(sessions);
    }

    /**
     * 客服接入会话（管理端）
     */
    @PostMapping("/admin/session/{sessionId}/take")
    @Operation(summary = "接入会话", description = "客服接入用户会话")
    public Result<Boolean> takeSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        
        Long userId = getCurrentUserId();
        Boolean success = customerServiceService.takeSession(sessionId, userId);
        return Result.success(success);
    }

    /**
     * 获取当前用户ID
     * 从JWT认证中获取用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                // JWT过滤器中将userId设置为principal
                if (authentication.getPrincipal() instanceof Long) {
                    return (Long) authentication.getPrincipal();
                }
                // 兼容字符串形式的userId
                return Long.parseLong(authentication.getPrincipal().toString());
            } catch (Exception e) {
                log.error("获取用户ID失败: {}", e.getMessage());
                throw new RuntimeException("用户未登录或认证信息无效");
            }
        }
        throw new RuntimeException("用户未登录");
    }
}
