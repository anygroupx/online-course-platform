package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaVerifyLoginRequest {
    @NotBlank(message = "challengeId不能为空")
    private String challengeId;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
