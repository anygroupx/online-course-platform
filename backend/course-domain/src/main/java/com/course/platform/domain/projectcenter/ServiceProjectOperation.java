package com.course.platform.domain.projectcenter;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("service_project_operation")
public class ServiceProjectOperation {
    @TableId(type = IdType.INPUT)
    private String id;

    private String accountId;
    private Long userId;
    private Long projectId;
    private String projectTitle;
    private Long projectVersion;
    private Long providerVersion;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long accountVersion;

    private String action;
    private String state;
    private BigDecimal units;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;
    private BigDecimal amount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal balanceAfter;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorCategory;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long resolvedBy;

    @JsonIgnore @ToString.Exclude private String resolutionEvidence;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
