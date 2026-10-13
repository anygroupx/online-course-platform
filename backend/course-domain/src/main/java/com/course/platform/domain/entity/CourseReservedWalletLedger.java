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

/** 不可变专户流水；方向 1 为入账、-1 为出账，以业务身份防止重复记账。 */
@Data
@TableName("course_reserved_wallet_ledger")
public class CourseReservedWalletLedger implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long walletId;

    private Long userId;

    private String bizType;

    private String bizNo;

    private Integer direction;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String relatedOrderNo;

    private Long operatorId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
