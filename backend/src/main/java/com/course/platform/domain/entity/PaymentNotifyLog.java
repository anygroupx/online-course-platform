package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付异步通知日志实体类
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLog implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 支付宝交易号
     */
    private String alipayTradeNo;

    /**
     * 通知参数(JSON)
     */
    private String notifyParams;

    /**
     * 通知类型
     */
    private String notifyType;

    /**
     * 交易状态
     */
    private String tradeStatus;

    /**
     * 签名验证结果：0-失败 1-成功
     */
    private Integer verifyResult;

    /**
     * 验证消息
     */
    private String verifyMessage;

    /**
     * 处理状态：0-待处理 1-处理成功 2-处理失败
     */
    private Integer processStatus;

    /**
     * 处理消息
     */
    private String processMessage;

    /**
     * 处理时间
     */
    private LocalDateTime processTime;

    /**
     * 响应内容
     */
    private String responseContent;

    /**
     * 请求IP
     */
    private String requestIp;

    /**
     * 请求时间
     */
    private LocalDateTime requestTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
