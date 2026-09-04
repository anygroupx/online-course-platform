package com.course.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 普通用户客服会话响应，只暴露不可枚举的会话标识和业务状态。
 */
@Data
@Builder
public class CustomerServiceSessionResponse {
    private String sessionId;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
