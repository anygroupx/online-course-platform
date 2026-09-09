package com.course.platform.domain.projectcenter;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("service_project")
public class ServiceProject {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;
    private String remoteProjectId;
    private String title;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal unitPrice;
    private BigDecimal unitCost;
    private LocalDate validUntil;
    @JsonIgnore @ToString.Exclude private String priceEvidence;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @JsonIgnore @ToString.Exclude private String providerIdentity;
    private Boolean enabled;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
