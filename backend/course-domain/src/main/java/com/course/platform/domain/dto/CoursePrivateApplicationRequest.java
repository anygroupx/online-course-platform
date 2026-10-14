package com.course.platform.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 本人申请输入白名单，不接收拥有者、价格、余额、审核结果或周期日期。 */
@Data
public class CoursePrivateApplicationRequest {
    @NotNull @Positive private Long planId;
    @NotNull @Positive private Long planVersionId;
    @NotBlank @Pattern(regexp="OPEN|RENEW|UPGRADE|REJOIN") private String applicationType="OPEN";
    @Positive private Long sourceTermId;
    @NotBlank @Pattern(regexp="ON_ACTIVATION|NEXT_TERM|IMMEDIATE|REVIEW_IMMEDIATE") private String effectivePolicy="NEXT_TERM";
    private boolean acknowledgeLegacyChange;
    @NotBlank @Size(max=255) private String reason;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String requestKey;
}
