package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值卡密实体类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Data
@TableName("recharge_card")
public class RechargeCard implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 卡密ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 卡号
     */
    @TableField("card_no")
    private String cardNo;

    /**
     * 卡密
     */
    @TableField("card_password")
    private String cardPassword;

    /**
     * 面额
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 状态：0-未使用 1-已使用 2-已禁用
     */
    @TableField("status")
    private Integer status;

    /**
     * 使用者ID
     */
    @TableField("used_by")
    private Long usedBy;

    /**
     * 使用时间
     */
    @TableField("used_time")
    private LocalDateTime usedTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
