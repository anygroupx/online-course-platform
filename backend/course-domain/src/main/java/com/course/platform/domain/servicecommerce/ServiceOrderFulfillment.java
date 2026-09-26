package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** Encrypted, least-privilege material used only to fulfill a self-operated order. */
@Data
@TableName("service_order_fulfillment")
public class ServiceOrderFulfillment {
    @TableId(type = IdType.INPUT)
    private String orderId;

    @JsonIgnore
    @ToString.Exclude
    private String payloadEncrypted;

    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
