package com.course.platform.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统变量创建请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于系统变量管理需求设计
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemVariableCreateRequest {

    /**
     * 变量键名
     */
    @NotBlank(message = "变量键名不能为空")
    private String variableKey;

    /**
     * 变量显示名称
     */
    @NotBlank(message = "变量显示名称不能为空")
    private String variableName;

    /**
     * 变量类型
     */
    @NotBlank(message = "变量类型不能为空")
    private String variableType;

    /**
     * 变量值
     */
    @NotBlank(message = "变量值不能为空")
    private String variableValue;

    /**
     * 变量标签/描述
     */
    private String variableLabel;

    /**
     * 排序
     */
    private Integer sortOrder = 0;

    /**
     * 是否默认值：0-否 1-是
     */
    private Integer isDefault = 0;

    /**
     * 是否启用：0-禁用 1-启用
     */
    private Integer isEnabled = 1;

    /**
     * 显示颜色
     */
    private String color;

    /**
     * 图标
     */
    private String icon;
}
