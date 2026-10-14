package com.course.platform.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 管理员分配权益使用公开UUID，审核主体仍只能来自登录会话。 */
@Data
@EqualsAndHashCode(callSuper=true)
public class AdminCoursePrivateApplicationRequest extends CoursePrivateApplicationRequest {
    @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private String userUid;
}
