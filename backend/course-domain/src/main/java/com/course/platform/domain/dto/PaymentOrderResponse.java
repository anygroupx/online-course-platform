package com.course.platform.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单响应DTO
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付订单响应")
public class PaymentOrderResponse {

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "支付金额")
    private BigDecimal amount;

    @Schema(description = "订单标题")
    private String subject;

    @Schema(description = "支付方式")
    private String paymentType;

    @Schema(description = "订单状态")
    private String status;

    @Schema(description = "支付宝交易号")
    private String alipayTradeNo;

    @Schema(description = "买家支付宝账号")
    private String buyerLogonId;

    @Schema(description = "支付时间")
    private LocalDateTime paidTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "支付表单HTML(创建订单时返回)")
    private String paymentForm;
}
