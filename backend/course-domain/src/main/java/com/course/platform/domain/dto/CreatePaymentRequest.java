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
    @DecimalMax(value = "99999999.99", message = "支付金额超出允许范围")
    @Digits(integer = 8, fraction = 2, message = "支付金额最多8位整数和2位小数")
    @Schema(description = "支付金额", example = "100.00")
    private BigDecimal amount;

    @NotBlank(message = "支付方式不能为空")
    @Pattern(regexp = "^(PC|WAP)$", message = "支付方式只能是PC或WAP")
    @Schema(description = "支付方式：PC-电脑网站支付 WAP-手机网站支付", example = "PC")
    private String paymentType;

    @Schema(description = "订单标题(可选,不传则使用默认标题)", example = "账户充值")
    @Size(max = 100, message = "订单标题不能超过100个字符")
    private String subject;

    @Schema(description = "订单描述(可选)", example = "在线网课平台账户充值")
    @Size(max = 300, message = "订单描述不能超过300个字符")
    private String body;
}
