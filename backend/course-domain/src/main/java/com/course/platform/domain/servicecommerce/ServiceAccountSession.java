package com.course.platform.domain.servicecommerce;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_account_session")
public class ServiceAccountSession {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;
    private Long productId;
    private Long productVersion;
    private Long providerVersion;
    private String mode;
    private String state;
    private String accountLabel;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String snapshotEncrypted;

    private Long version;
    private LocalDateTime rulesRefreshAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
