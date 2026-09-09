package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_api_call")
public class ProjectApiCall {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long ownerId;
    private String credentialId;
    private String action;
    private String outcome;
    private LocalDateTime createTime;
}
