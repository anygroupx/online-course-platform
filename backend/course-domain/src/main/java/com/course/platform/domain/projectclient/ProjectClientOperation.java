package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("project_client_operation")
public class ProjectClientOperation {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long ownerId;
    private String requestId;
    @JsonIgnore @ToString.Exclude private String requestHash;
    private String clientId;
    private Long projectId;
    private String projectTitle;
    private String label;
    private Long projectVersion;
    private Long clientVersion;
    private String action;
    private String state;
    private BigDecimal units;
    private BigDecimal unitPrice;
    private BigDecimal amount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal clientBalanceAfter;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal walletBalanceAfter;

    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
