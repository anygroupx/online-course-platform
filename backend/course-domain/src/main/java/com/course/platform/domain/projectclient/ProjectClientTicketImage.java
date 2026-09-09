package com.course.platform.domain.projectclient;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
@ToString(onlyExplicitlyIncluded = true)
@TableName("project_client_ticket_image")
public class ProjectClientTicketImage {
    @TableId(type = IdType.INPUT)
    @ToString.Include
    private String id;
    private String ticketId;
    private Long ticketVersion;
    private Integer width;
    private Integer height;
    private Integer byteSize;
    private String sha256;
    @TableField(select = false)
    private String contentEncrypted;
    private LocalDateTime createTime;
}
