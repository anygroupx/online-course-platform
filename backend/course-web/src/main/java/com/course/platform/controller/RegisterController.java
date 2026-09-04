package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.InviteCodeRequest;
import com.course.platform.domain.dto.RegisterRequest;
import com.course.platform.application.service.auth.RegisterService;
import com.course.platform.security.TurnstileVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 用户注册控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "用户注册", description = "用户注册和邀请码管理接口")
@RestController
@RequestMapping("/register")
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterService registerService;
    private final TurnstileVerifier turnstileVerifier;

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "通过邀请码注册新用户")
    @PostMapping
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        // 注册入库前强制完成服务端验证，前端组件本身不能作为安全判据。
        turnstileVerifier.verify(request.getTurnstileToken(), "register");
        String uid = registerService.register(request);
        return Result.success("注册成功", uid);
    }

    /**
     * 设置邀请码
     */
    @Operation(summary = "设置邀请码", description = "生成或更新邀请码和邀请费率")
    @PostMapping("/invite-code")
    public Result<String> setupInviteCode(@Valid @RequestBody InviteCodeRequest request,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String inviteCode = registerService.setupInviteCode(userId, request);
        return Result.success("邀请码设置成功", inviteCode);
    }

    /**
     * 验证邀请码
     */
    @Operation(summary = "验证邀请码", description = "检查邀请码是否有效")
    @GetMapping("/validate-invite-code")
    public Result<Boolean> validateInviteCode(@RequestParam String inviteCode) {
        boolean valid = registerService.validateInviteCode(inviteCode);
        return Result.success(valid);
    }
}
