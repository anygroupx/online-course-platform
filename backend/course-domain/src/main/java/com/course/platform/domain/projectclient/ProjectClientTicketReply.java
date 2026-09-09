package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@ToString(onlyExplicitlyIncluded = true)
@TableName("project_client_ticket_reply")
public class ProjectClientTicketReply {
    @TableId(type = IdType.INPUT)
    @ToString.Include
    private String id;
    private String ticketId;
    private Long ticketVersion;
    private String author;
    private String content;
    private String imageId;
    private LocalDateTime createTime;
}
