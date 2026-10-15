package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 同一原专户关系内独立的权益周期与资金资格窗口。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_contract_term")
public class CoursePrivateContractTerm implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private Long userId;

    private Long platformId;

    private Long planId;

    private Long versionId;

    private Integer termNo;

    private Integer relationGeneration;

    private String operationsMode;

    private String tierCode;

    private Integer tierRank;

    private BigDecimal effectiveFixedPrice;

    private BigDecimal effectiveInitialDeposit;

    private BigDecimal renewalTarget;

    private String status;

    private String qualificationStatus;
    /** 首开、消费续期、净预存续期、升级和补授的来源不能混为同一资格。 */
    private String qualificationKind;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private LocalDateTime settlementDeadlineAt;

    private LocalDateTime activatedAt;

    private LocalDateTime qualifiedAt;

    private Long renewalSourceTermId;

    private LocalDateTime fundingWindowStart;

    private LocalDateTime fundingWindowEnd;

    /** 账户锁下保存的流水水位，防止同秒旧资金重复满足新增预存。 */
    private Long fundingStartLedgerId;

    /** 账户锁下保存的流水水位，防止同秒旧资金重复满足新增预存。 */
    private Long fundingEndLedgerId;

    private BigDecimal qualifiedDepositAmount;
    /** 已证明满足的首次/升级门槛基数，不把后续续期或新版本门槛冒充已预存。 */
    private BigDecimal qualificationBasisAmount;
    /** 本期续期窗口净新增预存，可为负，不是钱包余额或初次资格金额。 */
    private BigDecimal renewalNetDepositAmount;

    private BigDecimal settledConsumptionAmount;

    private Integer settledOrderCount;

    private BigDecimal adjustmentAmount;

    private LocalDateTime finalizedAt;

    private Boolean reviewRequired;

    private LocalDateTime supersededAt;

    private String configSnapshot;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
