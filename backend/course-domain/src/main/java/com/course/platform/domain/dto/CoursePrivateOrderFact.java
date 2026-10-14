package com.course.platform.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 原关联、订单快照及两类账本连接的结算事实，不接受客户端赋值。 */
@Data
public class CoursePrivateOrderFact {
    private Long id;
    private Long termId;
    private Long contractId;
    private Long userId;
    private Long platformId;
    private Long orderId;
    private String orderNo;
    private BigDecimal orderedAmount;
    private LocalDateTime paidAt;
    private BigDecimal orderAmount;
    private Long orderUserId;
    private Long orderPlatformId;
    private Long orderContractId;
    private Long orderWalletId;
    private Long orderTermId;
    private Long orderPlanVersionId;
    private String orderOperationsMode;
    private String orderPriceSource;
    private String orderPaymentSource;
    private Integer orderStatus;
    private Boolean archived;
    private LocalDateTime fulfilledAt;
    private String fulfillmentSource;
    private LocalDateTime completionConfirmedAt;
    private String completionConfirmationSource;
    private LocalDateTime orderRefundedAt;
    private Boolean originalDebitExists;
    private Boolean refundExists;
    private BigDecimal refundAmount;
}
