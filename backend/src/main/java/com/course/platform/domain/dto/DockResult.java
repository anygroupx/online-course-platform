package com.course.platform.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对接结果DTO
 */
@Data
@Builder
public class DockResult {
    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 第三方订单ID
     */
    private String thirdOrderId;

    /**
     * 响应代码
     */
    private Integer code;
    
    /**
     * 快速构建成功结果
     */
    public static DockResult success(String message, String thirdOrderId) {
        return DockResult.builder()
                .success(true)
                .message(message)
                .thirdOrderId(thirdOrderId)
                .code(1)
                .build();
    }

    /**
     * 快速构建失败结果
     */
    public static DockResult fail(String message) {
        return DockResult.builder()
                .success(false)
                .message(message)
                .code(-1)
                .build();
    }
}
