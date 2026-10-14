package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 付款时确定归属的周期订单及可信结算投影。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_term_order")
public class CoursePrivateTermOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long termId;

    private Long contractId;

    private Long userId;

    private Long platformId;

    private Long orderId;

    private String orderNo;

    private BigDecimal orderedAmount;

    private BigDecimal qualifiedAmount;

    private String qualificationStatus;

    private LocalDateTime paidAt;

    private LocalDateTime completedAt;
    /** 结算投影明确区分实际完成与截止前确认，不伪造completedAt。 */
    private LocalDateTime completionConfirmedAt;
    private String completionBasis;

    private LocalDateTime refundedAt;

    private LocalDateTime lastReconciledAt;
}
