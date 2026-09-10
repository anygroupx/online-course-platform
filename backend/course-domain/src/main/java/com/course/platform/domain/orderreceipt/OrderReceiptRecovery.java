package com.course.platform.domain.orderreceipt;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@TableName("course_order_receipt_recovery")
public class OrderReceiptRecovery {
    @TableId(type = IdType.INPUT) private String id;
    private Long actorId;
    private Long orderId;
    private Long ownerId;
    private Long providerId;
    private String orderNo;
    private String courseName;
    private String receiptId;
    private String state;
    @JsonIgnore @ToString.Exclude private String requestHash;
    @JsonIgnore @ToString.Exclude private String orderHash;
    @JsonIgnore @ToString.Exclude private String platformHash;
    @JsonIgnore @ToString.Exclude private String providerHash;
    @JsonIgnore @ToString.Exclude private String sourceIdentity;
    @JsonIgnore @ToString.Exclude private String evidenceEncrypted;
    private LocalDateTime expiresAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
