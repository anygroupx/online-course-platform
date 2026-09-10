package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平台分类实体类
 */
@Data
@TableName("platform_category")
public class PlatformCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分类名称
     */
    @TableField("name")
    private String name;

    /**
     * 分类价格倍率，为空时使用导入或刷新时传入的全局倍率
     */
    @TableField(value = "price_multiplier", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal priceMultiplier;

    /**
     * 排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 状态：0-禁用 1-启用
     */
    @TableField("status")
    private Integer status;

    /**
     * 远程API的分类ID
     */
    @TableField("remote_category_id")
    private String remoteCategoryId;

    /**
     * 关联的API提供商ID
     */
    @TableField("remote_api_provider_id")
    private Long remoteApiProviderId;

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
