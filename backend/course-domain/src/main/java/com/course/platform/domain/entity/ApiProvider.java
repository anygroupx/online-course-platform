package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
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
     * 密码（禁止默认序列化）
     */
    @TableField("password")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    /**
     * Token（禁止默认序列化）
     */
    @TableField("token")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String token;

    /**
     * API Key（禁止默认序列化）
     */
    @TableField("api_key")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String apiKey;

    /**
     * Cookie（禁止默认序列化）
     */
    @TableField("cookie")
    @com.fasterxml.jackson.annotation.JsonIgnore
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
     * 状态：0-禁用 1-正常
     */
    @TableField("status")
    private Integer status;

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
