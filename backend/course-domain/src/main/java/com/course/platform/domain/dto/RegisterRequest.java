package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class RegisterRequest {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128")
    private String password;

    /**
     * 邀请码
     */
    @NotBlank(message = "邀请码不能为空")
    @Size(max = 20, message = "邀请码长度不能超过20")
    private String inviteCode;

    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    /**
     * Cloudflare Turnstile 单次验证令牌
     */
    @Size(max = 2048, message = "人机验证令牌格式错误")
    private String turnstileToken;
}
