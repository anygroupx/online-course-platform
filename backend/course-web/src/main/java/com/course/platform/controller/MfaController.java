package com.course.platform.controller;

import com.course.platform.application.service.security.MfaService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.dto.MfaCodeRequest;
import com.course.platform.domain.dto.MfaConfirmSetupRequest;
import com.course.platform.domain.dto.MfaVerifyLoginRequest;
import com.course.platform.domain.vo.LoginResponse;
import com.course.platform.domain.vo.MfaSetupVO;
import com.course.platform.domain.vo.MfaStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MFA", description = "管理员 TOTP 多因素认证")
@RestController
@RequestMapping("/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('mfa:manage')")
    @Operation(summary = "查询 MFA 状态")
    public Result<MfaStatusVO> status(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(mfaService.status(userId));
    }

    @PostMapping("/setup")
    @PreAuthorize("hasAuthority('mfa:manage')")
    @Operation(summary = "开始绑定 MFA")
    public Result<MfaSetupVO> setup(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(mfaService.beginSetup(userId));
    }

    @PostMapping("/setup/confirm")
    @PreAuthorize("hasAuthority('mfa:manage')")
    @Operation(summary = "确认绑定 MFA")
    public Result<Void> confirm(@Valid @RequestBody MfaConfirmSetupRequest request,
                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        mfaService.confirmSetup(userId, request);
        return Result.success("MFA 已启用");
    }

    @PostMapping("/disable")
    @PreAuthorize("hasAuthority('mfa:manage')")
    @Operation(summary = "关闭 MFA")
    public Result<Void> disable(@Valid @RequestBody MfaCodeRequest request,
                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        mfaService.disable(userId, request);
        return Result.success("MFA 已关闭");
    }

    @PostMapping("/verify")
    @Operation(summary = "登录 MFA 二次验证")
    public Result<LoginResponse> verify(@Valid @RequestBody MfaVerifyLoginRequest request) {
        return Result.success("MFA 验证成功", mfaService.verifyLogin(request));
    }
}
