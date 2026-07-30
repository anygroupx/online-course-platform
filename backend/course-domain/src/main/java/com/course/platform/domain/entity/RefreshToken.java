package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refresh Token实体类
 *
 * @author AI Assistant
 * @since 2025-11-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("refresh_token")
public class RefreshToken {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Refresh Token 明文（兼容旧数据，逐步废弃）
     */
    private String token;

    /**
     * Token 哈希
     */
    private String tokenHash;

    /**
     * Token 家族 ID
     */
    private String tokenFamilyId;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 撤销时间
     */
    private LocalDateTime revokedAt;

    /**
     * 被替换后的 token 哈希
     */
    private String replacedBy;

    /**
     * 最后使用 IP
     */
    private String lastUsedIp;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
