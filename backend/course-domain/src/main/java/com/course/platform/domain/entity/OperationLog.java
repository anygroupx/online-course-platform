package com.course.platform.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@TableName("operation_log")
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    @JsonIgnore
    private Long userId;

    /**
     * 操作类型
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 操作描述
     */
    @TableField("operation_desc")
    private String operationDesc;

    /**
     * 金额变动
     */
    @TableField("amount_change")
    private BigDecimal amountChange;

    /**
     * 操作后余额
     */
    @TableField("balance_after")
    private BigDecimal balanceAfter;

    /**
     * IP地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

