package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("service_product")
public class ServiceProduct {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;
    private String providerType;
    private String project;
    private String remoteProductId;
    private String title;
    private String description;
    private BigDecimal unitPrice;
    private Boolean enabled;
    private BigDecimal contractUnitCost;
    private java.time.LocalDate contractValidUntil;

    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String contractEvidence;

    private Long contractReviewedBy;
    private LocalDateTime contractReviewedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore private String contractProviderIdentity;
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // Service operations own their Beijing timestamps; do not invoke the global host-time update
    // filler.
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateTime;
}
