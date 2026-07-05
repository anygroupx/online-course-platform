package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 倒计时历史记录实体类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Data
@TableName("countdown_history")
public class CountdownHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 订单号
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 操作类型：start-开始倒计时, adjust-调整倒计时, complete-完成订单, expire-过期
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * 操作前时长（分钟）
     */
    @TableField("old_duration")
    private Integer oldDuration;

    /**
     * 操作后时长（分钟）
     */
    @TableField("new_duration")
    private Integer newDuration;

    /**
     * 操作原因
     */
    @TableField("reason")
    private String reason;

    /**
     * 操作人ID
     */
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 操作人姓名
     */
    @TableField("operator_name")
    private String operatorName;

    /**
     * 操作时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
