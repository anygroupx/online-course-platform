package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaCodeRequest {
    @NotBlank(message = "验证码不能为空")
    private String code;
}
