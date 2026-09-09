package com.course.platform.domain.projectcenter;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("service_project_account")
public class ServiceProjectAccount {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;
    private Long projectId;
    private Long providerId;
    @JsonIgnore @ToString.Exclude private String providerIdentity;
    private String remoteProjectId;
    @JsonIgnore @ToString.Exclude private String remoteCustomerId;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerKeyEncrypted;

    private String state;
    private BigDecimal unitPrice;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal remoteBalance;

    private BigDecimal refundableUnits;
    private BigDecimal refundBudget;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime balanceCheckedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingOperationId;

    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
