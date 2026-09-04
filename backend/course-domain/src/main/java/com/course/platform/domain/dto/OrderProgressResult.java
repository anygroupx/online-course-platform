package com.course.platform.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单进度查询结果DTO
 */
@Data
@Builder
public class OrderProgressResult {
    /**
     * 进度描述 (如 "50%", "已完成")
     */
    private String progress;

    /**
     * 订单状态 (对应Constants中的状态码)
     */
    private Integer orderStatus;

    /**
     * 备注信息
     */
    private String remarks;

    /**
     * 课程开始时间
     */
    private LocalDateTime courseStartTime;

    /**
     * 课程结束时间
     */
    private LocalDateTime courseEndTime;

    /**
     * 考试开始时间
     */
    private LocalDateTime examStartTime;

    /**
     * 考试结束时间
     */
    private LocalDateTime examEndTime;

    // ==================== 批量更新时的订单标识字段 ====================
    /**
     * 学生账号（用于批量更新时匹配订单）
     */
    private String studentAccount;

    /**
     * 学生密码（用于批量更新时匹配订单）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String studentPassword;

    /**
     * 课程名称（用于批量更新时匹配订单）
     */
    private String courseName;

    /**
     * 第三方订单ID（如果第三方接口返回）
     */
    private String thirdOrderId;
}
