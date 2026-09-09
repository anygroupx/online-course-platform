package com.course.platform.domain.projectcenter;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_project_ticket")
public class ServiceProjectTicket {
    @TableId(type = IdType.INPUT)
    private String id;

    private String accountId;
    private Long userId;
    private Long projectId;
    private Long providerId;
    @JsonIgnore private String providerIdentity;
    private String remoteProjectId;
    private String projectTitle;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remoteTicketId;

    private String state;
    @JsonIgnore @ToString.Exclude private String requestEncrypted;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String snapshotEncrypted;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String pendingOperationId;

    private Long version;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime checkedAt;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
