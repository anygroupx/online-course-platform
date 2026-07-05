package com.course.platform.domain.dto;

import lombok.Data;

/**
 * 订单查询请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class OrderQueryRequest {

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 平台ID
     */
    private Long platformId;

    /**
     * 学生账号
     */
    private String studentAccount;

    /**
     * 订单状态
     */
    private Integer orderStatus;

    /**
     * 对接状态
     */
    private Integer dockStatus;

    /**
     * 代理账号ID
     */
    private Long userId;

    /**
     * 代理账号名称
     */
    private String username;

    /**
     * 当前页
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 10;
    
    /**
     * 是否自营订单（1=自营，0=非自营）
     */
    private Integer isSelfOperated;
}

