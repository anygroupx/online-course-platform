package com.course.platform.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理员协议配置白名单；余额、操作人和激活时间只能由服务端产生。 */
@Data
public class CourseContractRequest {
    // 管理端优先使用公开UUID，内部ID仅兼容已授权调用；两者同传必须匹配。
    @Size(max = 36, message = "用户UUID最多36位")
    private String userUid;

    @Positive(message = "用户ID必须为正数")
    private Long userId;

    @NotNull(message = "课程商品ID不能为空")
    @Positive(message = "课程商品ID必须为正数")
    private Long platformId;

    @NotNull(message = "协议价格不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "协议价格必须大于0")
    @DecimalMax(value = "9999999999.99", message = "协议价格超出金额上限")
    @Digits(integer = 10, fraction = 2, message = "协议价格最多两位小数")
    private BigDecimal fixedPrice;

    @NotNull(message = "首次预存门槛不能为空")
    @DecimalMin(value = "0", message = "首次预存门槛不能为负数")
    @DecimalMax(value = "9999999999.99", message = "首次预存门槛超出金额上限")
    @Digits(integer = 10, fraction = 2, message = "首次预存门槛最多两位小数")
    private BigDecimal minInitialDeposit;

    @NotBlank(message = "协议状态不能为空")
    @Pattern(regexp = "PENDING_ACTIVATION|ACTIVE|SUSPENDED|EXPIRED|TERMINATED", message = "协议状态不合法")
    private String status;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    // 新建时允许无版本，更新必须由服务校验并执行乐观锁。
    @Min(value = 0, message = "协议版本不能为负数")
    private Integer version;

    @NotBlank(message = "操作原因不能为空")
    @Size(max = 255, message = "操作原因最多255字")
    private String reason;
}
