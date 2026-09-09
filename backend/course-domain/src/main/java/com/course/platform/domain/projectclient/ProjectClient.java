package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("project_client")
public class ProjectClient {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long ownerId;
    private Long projectId;
    private String projectTitle;
    private String label;
    private String status;
    private Long version;
    private BigDecimal balance;
    private BigDecimal unitPrice;
    private BigDecimal refundableUnits;
    private BigDecimal refundBudget;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
