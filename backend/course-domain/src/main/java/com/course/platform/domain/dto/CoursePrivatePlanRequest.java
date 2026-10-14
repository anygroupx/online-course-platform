package com.course.platform.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

/** 管理员套餐草稿，不接受发布人、成本审核结果或资金余额赋值。 */
@Data
public class CoursePrivatePlanRequest {
    @NotNull @Positive private Long platformId;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String planCode;
    @NotBlank @Size(max=128) private String planName;
    @Size(max=2000) private String description;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String familyCode="STANDARD";
    @Size(max=64) private String tierCode;
    @Min(0) private int tierRank;
    private boolean visibleToAgents;
    @Pattern(regexp="AUTO|REVIEW") private String applicationMode="REVIEW";
    @Min(0) @Max(1000000) private int sortOrder;
    @Min(0) private Integer expectedDraftVersion;
    @NotBlank @Size(max=255) private String reason;
    @NotNull @Valid private CoursePrivatePlanRules rules;
}
