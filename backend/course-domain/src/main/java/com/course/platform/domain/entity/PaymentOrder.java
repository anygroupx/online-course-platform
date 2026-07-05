package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单实体类
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Data
@TableName("payment_order")
public class PaymentOrder implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 订单标题
     */
    private String subject;

    /**
     * 订单描述
     */
    private String body;

    /**
     * 支付方式：PC-电脑网站支付 WAP-手机网站支付
     */
    private String paymentType;

    /**
     * 订单状态：PENDING-待支付 PAID-已支付 CLOSED-已关闭 REFUNDING-退款中 REFUNDED-已退款
     */
    private String status;

    /**
     * 支付宝交易号
     */
    private String alipayTradeNo;

    /**
     * 买家支付宝账号
     */
    private String buyerLogonId;

    /**
     * 买家支付宝用户ID
     */
    private String buyerUserId;

    /**
     * 支付时间
     */
    private LocalDateTime paidTime;

    /**
     * 关闭时间
     */
    private LocalDateTime closeTime;

    /**
     * 超时时间(分钟)
     */
    private Integer timeoutExpress;

    /**
     * 退款金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 同步回调地址
     */
    private String returnUrl;

    /**
     * 异步通知地址
     */
    private String notifyUrl;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
