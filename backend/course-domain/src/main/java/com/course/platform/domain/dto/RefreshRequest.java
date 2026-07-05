package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh Token请求DTO
 * 
 * @author AI Assistant
 * @since 2025-11-25
 */
@Data
public class RefreshRequest {

    /**
     * Refresh Token
     */
    @NotBlank(message = "Refresh Token不能为空")
    private String refreshToken;
}
