package com.course.platform.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客服会话VO（管理端）
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class CustomerServiceSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 会话标识
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 客服ID
     */
    private Long customerServiceId;

    /**
     * 客服昵称
     */
    private String customerServiceName;

    /**
     * 会话状态：1-等待中 2-进行中 3-已结束
     */
    private Integer status;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageTime;

    /**
     * 最后一条消息内容
     */
    private String lastMessageContent;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
