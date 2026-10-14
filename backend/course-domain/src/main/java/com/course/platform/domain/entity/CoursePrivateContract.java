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

/** 代理与课程的一对一密价协议，历史价格通过版本与审计保留。 */
@Data
@TableName("course_private_contract")
public class CoursePrivateContract implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long platformId;

    private BigDecimal fixedPrice;

    private BigDecimal minInitialDeposit;

    private String status;

    private LocalDateTime activatedAt;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Integer version;

    /** 同一资金绑定保留历史代次，当前权益通过独立周期发布。 */
    private String operationsMode;
    private Long currentTermId;
    private Integer relationGeneration;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
