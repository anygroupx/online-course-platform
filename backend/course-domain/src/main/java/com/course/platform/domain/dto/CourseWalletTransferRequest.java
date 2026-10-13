package com.course.platform.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 划转请求只接收金额、幂等键和原因，账户归属由服务端校验。 */
@Data
public class CourseWalletTransferRequest {
    @NotNull(message = "划转金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "划转金额必须大于0")
    @DecimalMax(value = "9999999999.99", message = "划转金额超出金额上限")
    @Digits(integer = 10, fraction = 2, message = "划转金额最多两位小数")
    private BigDecimal amount;

    @NotBlank(message = "幂等请求键不能为空")
    @Size(max = 64, message = "幂等请求键最多64位")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "幂等请求键只能包含字母数字及_-，长度为1到64位")
    private String requestKey;

    @Size(max = 255, message = "划转原因最多255字")
    private String reason;
}
