package com.course.platform.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * 创建支付订单请求DTO
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Data
@Schema(description = "创建支付订单请求")
public class CreatePaymentRequest {

    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0.01", message = "支付金额必须大于0.01元")
    @Schema(description = "支付金额", example = "100.00")
    private BigDecimal amount;

    @NotBlank(message = "支付方式不能为空")
    @Pattern(regexp = "^(PC|WAP)$", message = "支付方式只能是PC或WAP")
    @Schema(description = "支付方式：PC-电脑网站支付 WAP-手机网站支付", example = "PC")
    private String paymentType;

    @Schema(description = "同步回调地址(可选,不传则使用配置中的默认地址)", example = "https://example.com/payment/callback")
    private String returnUrl;

    @Schema(description = "订单标题(可选,不传则使用默认标题)", example = "账户充值")
    private String subject;

    @Schema(description = "订单描述(可选)", example = "在线网课平台账户充值")
    private String body;
}
