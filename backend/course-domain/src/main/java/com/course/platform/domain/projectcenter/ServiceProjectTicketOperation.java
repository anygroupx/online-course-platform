package com.course.platform.domain.projectcenter;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("service_project_ticket_operation")
public class ServiceProjectTicketOperation {
    @TableId(type = IdType.INPUT)
    private String id;

    private String ticketId;
    private Long actorId;
    private String action;
    private String state;
    private Long providerVersion;
    private Long ticketVersion;
    @JsonIgnore @ToString.Exclude private String payloadEncrypted;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resolutionEvidenceEncrypted;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long resolvedBy;

    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
