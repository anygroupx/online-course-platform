package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String cardNo;

    /**
     * 卡密
     */
    @NotBlank(message = "卡密不能为空")
    private String cardPassword;
}
