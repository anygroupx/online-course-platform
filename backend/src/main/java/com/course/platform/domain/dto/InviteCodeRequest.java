package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 邀请码设置请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class InviteCodeRequest {

    /**
     * 邀请费率
     */
    @NotNull(message = "邀请费率不能为空")
    private BigDecimal inviteRate;

    /**
     * 自定义邀请码（可选）
     */
    private String customInviteCode;
}

