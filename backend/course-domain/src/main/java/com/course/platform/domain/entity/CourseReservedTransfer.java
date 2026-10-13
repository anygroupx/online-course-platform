package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 已成功的普通余额与专户划转；转出必须记录授权管理员的批准身份。 */
@Data
@TableName("course_reserved_transfer")
public class CourseReservedTransfer implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long walletId;

    private Long userId;

    private BigDecimal amount;

    private String type;

    private String requestKey;

    private String bizNo;

    private String status;

    private Long operatorId;

    private Long approvedBy;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime completedAt;
}
