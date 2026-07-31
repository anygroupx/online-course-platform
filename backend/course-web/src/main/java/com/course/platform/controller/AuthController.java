package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.application.service.auth.AuthService;
import com.course.platform.security.TurnstileVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "认证接口", description = "用户登录、登出等认证相关接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TurnstileVerifier turnstileVerifier;

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "用户名密码登录，返回JWT Token")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 必须在校验账号密码之前消费 Turnstile 单次令牌，阻断自动化撞库。
        turnstileVerifier.verify(request.getTurnstileToken(), "login");
        LoginResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 用户登出
     */
    @Operation(summary = "用户登出", description = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        return Result.success("登出成功");
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的基本信息")
    @GetMapping("/current")
    public Result<Long> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(userId);
    }

    /**
     * 刷新Token
     */
    @Operation(summary = "刷新Token", description = "使用Refresh Token获取新的Access Token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody com.course.platform.domain.dto.RefreshRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return Result.success("Token刷新成功", response);
    }
}
