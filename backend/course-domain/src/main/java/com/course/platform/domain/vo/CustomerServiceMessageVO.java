package com.course.platform.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服消息VO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
public class CustomerServiceMessageVO {

    /**
     * 消息ID
     */
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 发送者名称
     */
    private String senderName;

    /**
     * 发送者类型：1-用户 2-客服
     */
    private Integer senderType;

    /**
     * 发送者类型名称
     */
    private String senderTypeName;

    /**
     * 消息类型：1-文本 2-图片 3-文件
     */
    private Integer messageType;

    /**
     * 消息类型名称
     */
    private String messageTypeName;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 是否已读：0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
