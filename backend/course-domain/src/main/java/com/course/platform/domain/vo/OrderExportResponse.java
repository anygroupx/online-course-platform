package com.course.platform.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单导出响应对象
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Schema(description = "订单导出响应")
public class OrderExportResponse {

    @Schema(description = "导出内容")
    private String content;

    @Schema(description = "导出格式")
    private Integer format;

    @Schema(description = "导出订单数量")
    private Integer count;

    @Schema(description = "导出时间")
    private String exportTime;
}
