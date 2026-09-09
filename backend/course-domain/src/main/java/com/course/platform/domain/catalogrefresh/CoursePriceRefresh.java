package com.course.platform.domain.catalogrefresh;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("course_price_refresh")
public class CoursePriceRefresh {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;
    private Long providerId;
    private Long providerVersion;
    private String state;
    @JsonIgnore @ToString.Exclude private String planEncrypted;
    private LocalDateTime expiresAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime appliedAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
