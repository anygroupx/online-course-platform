package com.course.platform.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方订单日志的统一展示模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderOrderLog {
    private String id;
    private String title;
    private String content;
    private String status;
    private String operator;
    private String createTime;
}
