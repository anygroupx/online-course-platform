package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程平台实体类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@TableName("course_platform")
public class CoursePlatform implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台名称
     */
    @TableField("name")
    private String name;

    /**
     * 分类ID
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 查询参数标识
     */
    @TableField("query_param")
    private String queryParam;

    /**
     * 对接参数标识
     */
    @TableField("dock_param")
    private String dockParam;

    /**
     * 基础价格
     */
    @TableField("base_price")
    private BigDecimal basePrice;

    /**
     * 查询接口ID
     */
    @TableField("query_api_id")
    private Long queryApiId;

    /**
     * 对接接口ID
     */
    @TableField("dock_api_id")
    private Long dockApiId;

    /**
     * 费率计算方式：MULTIPLY-乘法 ADD-加法
     */
    @TableField("rate_type")
    private String rateType;

    /**
     * 密码生成规则：{account}表示账号，如{account}@ZII
     */
    @TableField("password_rule")
    private String passwordRule;

    /**
     * 是否启用自动生成密码：0-禁用 1-启用
     */
    @TableField("password_enabled")
    private Integer passwordEnabled;

    /**
     * 是否自营平台：0-否 1-是
     */
    @TableField("is_self_operated")
    private Integer isSelfOperated;

    /**
     * 说明
     */
    @TableField("description")
    private String description;

    /**
     * 排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 状态：0-下架 1-上架
     */
    @TableField("status")
    private Integer status;

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

