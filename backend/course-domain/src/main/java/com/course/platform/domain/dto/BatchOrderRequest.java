package com.course.platform.domain.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 批量订单创建请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Schema(description = "批量订单创建请求")
public class BatchOrderRequest {
    
    @Schema(description = "平台ID", required = true)
    private Long platformId;
    
    @Schema(description = "订单列表", required = true)
    private List<BatchOrderItem> orders;
    
    /**
     * 批量订单项
     */
    @Data
    @Schema(description = "批量订单项")
    public static class BatchOrderItem {
        /** 每笔独立请求身份和服务端报价一致性校验。 */
        private String clientRequestId;
        private java.math.BigDecimal expectedAmount;
        private Integer expectedContractVersion;
        private String expectedPriceSource;
        private String expectedPaymentSource;
        
        @Schema(description = "学校名称", required = true)
        private String schoolName;
        
        @Schema(description = "学生姓名", required = true)
        private String studentName;
        
        @Schema(description = "学生账号", required = true)
        private String studentAccount;
        
        @Schema(description = "学生密码", required = true)
        private String studentPassword;
        
        @Schema(description = "课程ID", required = true)
        private String courseId;
        
        @Schema(description = "课程名称", required = true)
        private String courseName;
    }
}