package com.course.platform.domain.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 批量订单操作请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Schema(description = "批量订单操作请求")
public class BatchOrderOperationRequest {
    
    @Schema(description = "订单ID列表", required = true)
    private List<Long> orderIds;
    
    @Schema(description = "订单状态")
    private Integer status;
    
    @Schema(description = "对接状态")
    private Integer dockStatus;
    
    @Schema(description = "备注内容")
    private String remark;
    
    @Schema(description = "操作原因")
    private String reason;
}
