package com.course.platform.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 订单导出请求对象
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Schema(description = "订单导出请求")
public class OrderExportRequest {

    @Schema(description = "订单ID列表", required = true)
    @NotEmpty(message = "订单ID列表不能为空")
    private List<Long> orderIds;

    @Schema(description = "导出格式：1-学校+账号+课程，2-账号+课程，3-学校+账号，4-账号（永不导出密码）", required = true)
    @NotNull(message = "导出格式不能为空")
    private Integer format;

    @Schema(description = "文件格式：txt或xlsx，默认txt")
    private String fileType = "txt";

    @Schema(description = "导出原因")
    private String reason;
}
