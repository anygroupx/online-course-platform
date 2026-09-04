package com.course.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 登录响应VO
 *
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Token
     */
    private String token;

    /**
     * 对外用户 UUID
     */
    private String uid;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 费率
     */
    private BigDecimal rate;

    /**
     * 是否管理员
     */
    private Boolean isAdmin;

    /**
     * 角色
     */
    private String role;

    /**
     * 是否必须修改密码
     */
    private Boolean mustChangePassword;

    /**
     * Refresh Token
     */
    private String refreshToken;

    /**
     * 是否需要 MFA 二次验证
     */
    private Boolean mfaRequired;

    /**
     * 当前账号是否已启用 MFA
     */
    private Boolean mfaEnabled;

    /**
     * MFA 挑战 ID（mfaRequired=true 时返回）
     */
    private String mfaChallengeId;
}
