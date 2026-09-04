package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.LoginRequest;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserMapper;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.application.service.auth.AuthService;
import com.course.platform.security.TurnstileVerifier;
import com.course.platform.security.AuthCookieService;
import com.course.platform.security.LoginProtectionDecision;
import com.course.platform.security.LoginProtectionService;
import com.course.platform.security.RateLimitExceededException;
import com.course.platform.shared.util.ServletUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
    private final UserMapper userMapper;
    private final AuthCookieService authCookieService;
    private final LoginProtectionService loginProtectionService;

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "用户名密码登录，返回JWT Token")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse httpResponse) {
        String clientIp = ServletUtil.getClientIp();
        LoginProtectionDecision protection = loginProtectionService.check(request.getUsername(), clientIp);
        if (!protection.allowed()) {
            throw new RateLimitExceededException(protection.retryAfterSeconds());
        }
        // 在校验账号密码之前消费 Turnstile 单次令牌，阻断自动化撞库。
        turnstileVerifier.verify(request.getTurnstileToken(), "login", protection.challengeRequired());
        LoginResponse response;
        try {
            response = authService.login(request);
            loginProtectionService.recordSuccess(request.getUsername());
        } catch (BusinessException ex) {
            if (ResultCode.USERNAME_OR_PASSWORD_ERROR.getCode().equals(ex.getCode())) {
                loginProtectionService.recordFailure(request.getUsername(), clientIp);
            }
            throw ex;
        }
        if (!Boolean.TRUE.equals(response.getMfaRequired())) {
            authCookieService.issue(httpResponse, response.getRefreshToken());
        } else {
            authCookieService.noStore(httpResponse);
        }
        return Result.success("登录成功", response);
    }

    /**
     * 用户登出
     */
    @Operation(summary = "用户登出", description = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication, HttpServletResponse response) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        authCookieService.clear(response);
        return Result.success("登出成功");
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的基本信息")
    @GetMapping("/current")
    public Result<String> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return Result.success(user.getUid());
    }

    /**
     * 刷新Token
     */
    @Operation(summary = "刷新Token", description = "使用Refresh Token获取新的Access Token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse httpResponse) {
        String refreshToken = authCookieService.requireRefreshToken(request);
        authCookieService.requireValidCsrf(request);
        try {
            LoginResponse response = authService.refresh(refreshToken);
            authCookieService.issue(httpResponse, response.getRefreshToken());
            return Result.success("Token刷新成功", response);
        } catch (BusinessException e) {
            authCookieService.clear(httpResponse);
            throw e;
        }
    }
}
