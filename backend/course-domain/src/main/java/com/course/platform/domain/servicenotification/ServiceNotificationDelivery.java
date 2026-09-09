package com.course.platform.domain.servicenotification;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_notification_delivery")
public class ServiceNotificationDelivery {
    @TableId(type = IdType.INPUT)
    private String id;

    private String orderId;
    private Long userId;
    private Long preferenceVersion;
    private Long sequence;
    private String kind;
    private String state;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String payloadEncrypted;

    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
