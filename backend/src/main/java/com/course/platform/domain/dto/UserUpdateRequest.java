package com.course.platform.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户更新请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class UserUpdateRequest {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 费率
     */
    private BigDecimal rate;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 邀请费率
     */
    private BigDecimal inviteRate;
}

