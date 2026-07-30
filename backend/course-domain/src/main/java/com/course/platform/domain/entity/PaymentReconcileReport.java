package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("payment_reconcile_report")
public class PaymentReconcileReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("biz_date")
    private LocalDate bizDate;

    @TableField("status")
    private String status;

    @TableField("paid_order_count")
    private Integer paidOrderCount;

    @TableField("paid_order_amount")
    private BigDecimal paidOrderAmount;

    @TableField("ledger_credit_count")
    private Integer ledgerCreditCount;

    @TableField("ledger_credit_amount")
    private BigDecimal ledgerCreditAmount;

    @TableField("missing_ledger_count")
    private Integer missingLedgerCount;

    @TableField("extra_ledger_count")
    private Integer extraLedgerCount;

    @TableField("amount_diff")
    private BigDecimal amountDiff;

    @TableField("detail_json")
    private String detailJson;

    @TableField("create_time")
    private LocalDateTime createTime;
}
