package com.course.platform.controller;

import com.course.platform.common.result.Result;
import com.course.platform.application.service.auth.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API密钥控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Tag(name = "API密钥管理", description = "API密钥开通接口")
@RestController
@RequestMapping("/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * 开通API密钥
     */
    @Operation(summary = "开通API密钥", description = "为自己或下级开通API密钥")
    @PostMapping("/enable")
    public ResponseEntity<Result<String>> enableApiKey(@RequestParam Integer type,
                                                        @RequestParam(required = false) String targetUserUid,
                                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String apiKey = apiKeyService.enableApiKey(userId, type, targetUserUid);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(Result.success("API密钥开通成功（仅显示一次）", apiKey));
    }
}

