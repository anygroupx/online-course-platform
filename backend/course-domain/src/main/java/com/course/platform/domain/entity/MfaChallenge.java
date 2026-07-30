package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("mfa_challenge")
public class MfaChallenge implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("challenge_id")
    private String challengeId;

    @TableField("user_id")
    private Long userId;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("consumed")
    private Integer consumed;

    @TableField("create_time")
    private LocalDateTime createTime;
}
