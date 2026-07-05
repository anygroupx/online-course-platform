package com.course.platform.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

/**
 * 重新开始倒计时请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Data
public class RestartCountdownDTO {

    /**
     * 倒计时时长（分钟）
     */
    @NotNull(message = "倒计时时长不能为空")
    @Min(value = 1, message = "倒计时时长不能少于1分钟")
    @Max(value = 1440, message = "倒计时时长不能超过1440分钟（24小时）")
    private Integer duration;

    /**
     * 操作原因
     */
    private String reason;

    /**
     * 是否启用自动完成
     */
    private Boolean autoCompleteEnabled;

    /**
     * 自动完成状态
     */
    private Integer autoCompleteStatus;
}
