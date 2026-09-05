package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Password-confirmed self-service rotation; never include this request in logs. */
@Getter
@Setter
public class ApiKeyRotateRequest {
    @NotBlank(message = "请输入当前登录密码")
    @Size(max = 128, message = "密码长度不正确")
    private String currentPassword;
}
