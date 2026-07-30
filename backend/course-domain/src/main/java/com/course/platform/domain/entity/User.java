package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户实体类
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@TableName("sys_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上级代理ID
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 用户账号
     */
    @TableField("username")
    private String username;

    /**
     * 密码（加密）
     */
    @TableField("password")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * QQ OpenID
     */
    @TableField("qq_openid")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String qqOpenid;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 账户余额
     */
    @TableField("balance")
    private BigDecimal balance;

    /**
     * 总充值金额
     */
    @TableField("total_recharge")
    private BigDecimal totalRecharge;

    /**
     * 费率倍数
     */
    @TableField("rate")
    private BigDecimal rate;

    /**
     * API密钥（兼容旧明文，逐步废弃，接口层禁止返回）
     */
    @TableField("api_key")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String apiKey;

    /**
     * API Key 哈希
     */
    @TableField("api_key_hash")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String apiKeyHash;

    /**
     * API Key 前缀（可展示）
     */
    @TableField("api_key_prefix")
    private String apiKeyPrefix;

    /**
     * API Key 作用域
     */
    @TableField("api_key_scopes")
    private String apiKeyScopes;

    /**
     * API Key 过期时间
     */
    @TableField("api_key_expire_time")
    private java.time.LocalDateTime apiKeyExpireTime;

    /**
     * 邀请码
     */
    @TableField("invite_code")
    private String inviteCode;

    /**
     * 邀请费率
     */
    @TableField("invite_rate")
    private BigDecimal inviteRate;

    /**
     * 代理公告
     */
    @TableField("notice")
    private String notice;

    /**
     * 状态：0-禁用 1-正常
     */
    @TableField("status")
    private Integer status;

    /**
     * 角色：ADMIN / CS / USER
     */
    @TableField("role")
    private String role;

    /**
     * 是否必须修改密码：0-否 1-是
     */
    @TableField("must_change_password")
    private Integer mustChangePassword;

    /**
     * 密码最后修改时间
     */
    @TableField("password_changed_at")
    private java.time.LocalDateTime passwordChangedAt;

    /**
     * 是否启用 MFA：0-否 1-是
     */
    @TableField("mfa_enabled")
    private Integer mfaEnabled;

    /**
     * TOTP 密钥（加密存储）
     */
    @TableField("mfa_secret")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String mfaSecret;

    /**
     * MFA 启用时间
     */
    @TableField("mfa_enabled_at")
    private java.time.LocalDateTime mfaEnabledAt;

    /**
     * 备用恢复码哈希，逗号分隔
     */
    @TableField("mfa_backup_codes_hash")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String mfaBackupCodesHash;

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

    /**
     * 最后登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    @TableField("last_login_ip")
    private String lastLoginIp;
}
