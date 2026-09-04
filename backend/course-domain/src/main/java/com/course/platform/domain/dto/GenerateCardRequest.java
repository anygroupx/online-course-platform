package com.course.platform.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
    @Max(value = 100, message = "一次最多生成100张卡密")
    private Integer count;

    /**
     * 面额
     */
    @NotNull(message = "面额不能为空")
    @DecimalMin(value = "1.00", message = "面额至少为1元")
    @DecimalMax(value = "99999999.99", message = "面额超出允许范围")
    @Digits(integer = 8, fraction = 2, message = "面额最多8位整数和2位小数")
    private BigDecimal amount;
}
