package com.course.platform.domain.dto;

import lombok.Data;

/**
 * 充值卡密查询请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
public class CardQueryRequest {

    /**
     * 卡号
     */
    private String cardNo;

    /**
     * 状态：0-未使用 1-已使用 2-已禁用
     */
    private Integer status;

    /**
     * 使用者ID
     */
    private Long usedBy;
}
