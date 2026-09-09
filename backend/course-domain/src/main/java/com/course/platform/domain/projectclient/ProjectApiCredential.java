package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("project_api_credential")
public class ProjectApiCredential {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long ownerId;
    private String subject;
    private String accessMode;
    private Long version;

    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String keyHash;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String prefix;

    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
