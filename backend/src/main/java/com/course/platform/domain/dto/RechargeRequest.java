package com.course.platform.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class RechargeRequest {

    /**
     * 目标用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long targetUserId;

    /**
     * 充值金额
     */
    @NotNull(message = "充值金额不能为空")
    @Min(value = 10, message = "最低充值金额为10元")
    private BigDecimal amount;
}

