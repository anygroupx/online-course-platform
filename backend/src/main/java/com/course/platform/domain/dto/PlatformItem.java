package com.course.platform.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 第三方平台/课程项 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformItem {
    /**
     * 第三方ID (cid)
     */
    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 分类ID (fenlei)
     */
    private String categoryId;

    /**
     * 分类名称 (category_name)
     */
    private String categoryName;

    /**
     * 类型 (27, 29, etc.)
     */
    private String type;
    
    /**
     * 描述/内容
     */
    private String content;
}
