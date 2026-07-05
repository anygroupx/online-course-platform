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
     * 用户ID
     */
    private Long userId;

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
     * Refresh Token
     */
    private String refreshToken;
}

