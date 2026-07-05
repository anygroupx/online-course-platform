package com.course.platform.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 查课请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class QueryCourseRequest {

    /**
     * 课程平台ID
     */
    @NotNull(message = "课程平台ID不能为空")
    private Long platformId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 学生账号
     */
    @NotBlank(message = "学生账号不能为空")
    private String studentAccount;

    /**
     * 学生密码
     */
    @NotBlank(message = "学生密码不能为空")
    private String studentPassword;
}

