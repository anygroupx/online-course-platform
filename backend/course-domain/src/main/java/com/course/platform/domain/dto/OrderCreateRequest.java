package com.course.platform.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单创建请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCreateRequest {

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
     * 学生姓名
     */
    private String studentName;

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

    /**
     * 课程ID
     */
    private String courseId;

    /**
     * 课程名称
     */
    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    /**
     * 是否秒刷
     */
    private Boolean isFastMode;
}

