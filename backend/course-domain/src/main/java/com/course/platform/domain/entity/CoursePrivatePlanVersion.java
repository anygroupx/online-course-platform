package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 不可变发布规则与成本审批快照。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_plan_version")
public class CoursePrivatePlanVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long platformId;

    private Integer versionNo;

    private BigDecimal fixedPrice;

    private BigDecimal initialDepositAmount;

    private Integer validityDays;

    private String initialFundingPolicy;

    private LocalDateTime activationFrom;

    private String renewalPolicy;

    private BigDecimal renewalDepositAmount;

    private BigDecimal renewalConsumptionTarget;

    private Integer settlementGraceDays;

    private Boolean renewalAuto;

    private String renewalPricePolicy;

    private Integer renewalBeforeDays;

    private Integer renewalAfterDays;

    private String upgradePolicy;

    private String upgradeDepositPolicy;

    private Boolean allowGeneralCheckout;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;

    private Long costVersionId;

    private Long riskApprovalId;

    private Long legacyContractId;

    private LocalDateTime publishedAt;

    private Long createdBy;

    private LocalDateTime createdAt;

    private String configSnapshot;
}
