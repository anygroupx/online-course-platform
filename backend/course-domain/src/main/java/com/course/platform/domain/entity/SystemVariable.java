package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统变量配置实体类
 * 用于统一管理系统中的各种状态变量和配置项
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于系统变量管理需求设计
 */
@Data
@TableName("system_variable")
public class SystemVariable implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 变量ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 变量键名
     */
    @TableField("variable_key")
    private String variableKey;

    /**
     * 变量显示名称
     */
    @TableField("variable_name")
    private String variableName;

    /**
     * 变量类型：order_status,user_status,platform_status等
     */
    @TableField("variable_type")
    private String variableType;

    /**
     * 变量值
     */
    @TableField("variable_value")
    private String variableValue;

    /**
     * 变量标签/描述
     */
    @TableField("variable_label")
    private String variableLabel;

    /**
     * 排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 是否默认值：0-否 1-是
     */
    @TableField("is_default")
    private Integer isDefault;

    /**
     * 是否启用：0-禁用 1-启用
     */
    @TableField("is_enabled")
    private Integer isEnabled;

    /**
     * 显示颜色（前端使用）
     */
    @TableField("color")
    private String color;

    /**
     * 图标（前端使用）
     */
    @TableField("icon")
    private String icon;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
