package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@ToString(onlyExplicitlyIncluded = true)
@TableName("project_client_ticket_command")
public class ProjectClientTicketCommand {
    @TableId(type = IdType.INPUT)
    @ToString.Include
    private String id;
    private Long ownerId;
    private String actorSubject;
    private String requestId;
    private String requestHash;
    private String ticketId;
    private String action;
    private Long ticketVersion;
    private LocalDateTime createTime;
}
