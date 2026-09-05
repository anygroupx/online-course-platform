package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 第三方API接口实体类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@TableName("api_provider")
public class ApiProvider implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_PENDING = 2;

    /**
     * 接口ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 接口类型标识
     */
    @TableField("provider_type")
    private String providerType;

    /**
     * 接口名称
     */
    @TableField("name")
    private String name;

    /**
     * API地址
     */
    @TableField("api_url")
    private String apiUrl;

    /**
     * 账号
     */
    @TableField("username")
    private String username;

    /**
     * 密码（请求可写、响应不序列化）
     */
    @TableField("password")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * Token（请求可写、响应不序列化）
     */
    @TableField("token")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token;

    /**
     * API Key（请求可写、响应不序列化）
     */
    @TableField("api_key")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    /**
     * Cookie（请求可写、响应不序列化）
     */
    @TableField("cookie")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String cookie;

    /**
     * 接口余额
     */
    @TableField("balance")
    private BigDecimal balance;

    /**
     * 上次同步时间戳（秒）
     */
    @TableField("last_sync_time")
    private Long lastSyncTime;

    /**
     * 状态：0-禁用 1-已启用 2-待验证/待启用
     */
    @TableField("status")
    private Integer status;

    /** Incremented for configuration/status changes to prevent stale test results authorizing new targets. */
    @TableField("config_version")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long configVersion;

    @TableField("verified_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime verifiedAt;

    @TableField("verified_by")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long verifiedBy;

    @TableField("checked_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime checkedAt;

    @TableField("last_check_reason")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String lastCheckReason;

    @TableField("last_check_error_id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String lastCheckErrorId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
