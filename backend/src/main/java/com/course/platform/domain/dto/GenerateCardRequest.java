package com.course.platform.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生成充值卡密请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
public class GenerateCardRequest {

    /**
     * 卡密数量
     */
    @NotNull(message = "卡密数量不能为空")
    @Min(value = 1, message = "卡密数量至少为1")
    private Integer count;

    /**
     * 面额
     */
    @NotNull(message = "面额不能为空")
    @Min(value = 1, message = "面额至少为1元")
    private BigDecimal amount;
}
