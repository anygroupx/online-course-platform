package com.course.platform.domain.servicenotification;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_notification_preference")
public class ServiceNotificationPreference {
    @TableId(type = IdType.INPUT)
    private String orderId;

    private Long userId;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tokenEncrypted;

    private Boolean enabled;
    private Long version;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String codeHash;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String challengeDeliveryId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime challengeExpiresAt;

    private Integer verifyAttempts;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime verifiedAt;

    @JsonIgnore
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String observedJson;

    private Long sequence;
    private LocalDateTime scannedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
