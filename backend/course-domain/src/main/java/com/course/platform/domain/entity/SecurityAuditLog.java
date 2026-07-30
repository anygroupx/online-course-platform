package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("security_audit_log")
public class SecurityAuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("event_type")
    private String eventType;

    @TableField("severity")
    private String severity;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("request_path")
    private String requestPath;

    @TableField("http_method")
    private String httpMethod;

    @TableField("message")
    private String message;

    @TableField("detail")
    private String detail;

    @TableField("trace_id")
    private String traceId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
