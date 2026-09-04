package com.course.platform.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
     * 代理账号 UUID
     */
    private String userUid;

    /**
     * 代理账号名称
     */
    private String username;

    /**
     * 当前页
     */
    @NotNull(message = "当前页不能为空")
    @Min(value = 1, message = "当前页必须大于等于1")
    private Integer page = 1;

    /**
     * 每页数量
     */
    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;
    
    /**
     * 是否自营订单（1=自营，0=非自营）
     */
    private Integer isSelfOperated;
}
