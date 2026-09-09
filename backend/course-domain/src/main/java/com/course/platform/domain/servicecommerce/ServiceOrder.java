package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("service_order")
public class ServiceOrder {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;
    private Long productId;
    private Long providerId;
    private Long providerVersion;
    private String providerIdentity;
    private String providerType;
    private String project;
    private String remoteProductId;
    private String externalOrderNo;
    private String externalSubOrderNo;
    private String title;
    private String accountLabel;
    private String status;
    private Integer quantity;
    private Integer completed;
    private BigDecimal distance;

    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String scheduleJson;

    private BigDecimal unitCharge;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private Long version;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingOperationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // Service operations own their Beijing timestamps; do not invoke the global host-time update
    // filler.
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateTime;
}
