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
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;

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

