package com.course.platform.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 套餐规则输入白名单；序列化快照只保存验证后的配置，不执行表达式。 */
@Data
public class CoursePrivatePlanRules {
    @NotBlank @Pattern(regexp="PERMANENT_PREPAID|TIME_PREPAID|PERFORMANCE_RENEWAL|TIERED_PRICING")
    private String mode;
    @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2)
    private BigDecimal fixedPrice;
    @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2)
    private BigDecimal initialDepositAmount=BigDecimal.ZERO;
    @Min(1) @Max(3650) private Integer validityDays;
    @NotBlank @Pattern(regexp="NEW_DEPOSIT|EXISTING_BALANCE")
    private String initialFundingPolicy="NEW_DEPOSIT";
    private LocalDateTime activationFrom;
    @NotBlank @Pattern(regexp="NONE|NEW_DEPOSIT|PERFORMANCE|MANUAL")
    private String renewalPolicy="NONE";
    @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2)
    private BigDecimal renewalDepositAmount=BigDecimal.ZERO;
    @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2)
    private BigDecimal renewalConsumptionTarget=BigDecimal.ZERO;
    @Min(0) @Max(365) private int settlementGraceDays=7;
    private boolean renewalAuto;
    /** 只有冻结套餐明确公示允许补授，运营才能经独立权限人工授予下一期。 */
    private boolean allowManualGrant;
    @NotBlank @Pattern(regexp="SAME_VERSION|LATEST_PUBLISHED")
    private String renewalPricePolicy="SAME_VERSION";
    @Min(0) @Max(3650) private int renewalBeforeDays=7;
    @Min(0) @Max(3650) private int renewalAfterDays=7;
    @NotBlank @Pattern(regexp="DISABLED|NEXT_TERM|REVIEW_IMMEDIATE")
    private String upgradePolicy="NEXT_TERM";
    @NotBlank @Pattern(regexp="DIFFERENCE|FULL_TARGET|EXISTING_BALANCE")
    private String upgradeDepositPolicy="DIFFERENCE";
    private boolean upgradeAuto;
    private boolean allowGeneralCheckout;
    @NotBlank @Pattern(regexp="KEEP_BALANCE") private String expirationPolicy="KEEP_BALANCE";
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
}
