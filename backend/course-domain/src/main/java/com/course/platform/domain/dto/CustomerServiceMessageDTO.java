package com.course.platform.domain.dto;

import lombok.Data;

/**
 * 客服消息发送DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
public class CustomerServiceMessageDTO {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型：1-文本 2-图片 3-文件
     */
    private Integer messageType = 1;

    /**
     * 发送者类型：1-用户 2-客服
     */
    private Integer senderType;

    /**
     * 发送者ID
     */
    private Long senderId;
}
