package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@ToString(onlyExplicitlyIncluded = true)
@TableName("project_client_ticket")
public class ProjectClientTicket {
    @TableId(type = IdType.INPUT)
    @ToString.Include
    private String id;
    private Long ownerId;
    private String clientId;
    private Long projectId;
    private String projectTitle;
    private String kind;
    private String title;
    private String description;
    private BigDecimal requestedAmount;
    private String status;
    private Long version;
    private String reviewResult;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
