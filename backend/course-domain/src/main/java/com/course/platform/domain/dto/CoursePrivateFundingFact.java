package com.course.platform.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 来自同额成功划转及账本连接的资金事实，不接受公开接口输入。 */
@Data
public class CoursePrivateFundingFact {
    private Long ledgerId;
    private Long walletId;
    private Long userId;
    private String transferType;
    private BigDecimal amount;
    private LocalDateTime completedAt;
    private Long allocatedTermId;
}
