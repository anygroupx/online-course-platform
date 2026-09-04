package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户充值卡密请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
public class CardRechargeRequest {

    /**
     * 卡号
     */
    @NotBlank(message = "卡号不能为空")
    @Pattern(regexp = "^[0-9]{16}$", message = "卡号必须为16位数字")
    private String cardNo;

    /**
     * 卡密
     */
    @NotBlank(message = "卡密不能为空")
    @Size(min = 8, max = 64, message = "卡密长度必须为8到64位")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "卡密格式无效")
    private String cardPassword;
}
