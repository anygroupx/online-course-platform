package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("service_order_operation")
public class ServiceOperation {
    @TableId(type = IdType.INPUT)
    private String id;

    private String orderId;
    private Long userId;
    private Long productId;
    private Long productVersion;
    private Long providerVersion;
    private Long orderVersion;
    private String accountSessionId;
    private Long accountSessionVersion;
    private String action;
    private String state;
    private Integer quantity;
    private BigDecimal distance;

    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String scheduleJson;

    private BigDecimal unitCharge;
    private BigDecimal amount;
    private String accountLabel;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorCategory;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String payloadEncrypted;

    private Long resolvedBy;
    @JsonIgnore @ToString.Exclude private String resolutionNote;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // Service operations own their Beijing timestamps; do not invoke the global host-time update
    // filler.
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateTime;
}
