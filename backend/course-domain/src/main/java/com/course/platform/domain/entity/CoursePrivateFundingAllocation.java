package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 转入资金资格唯一认领，不扣款或冻结本金。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_funding_allocation")
public class CoursePrivateFundingAllocation implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long termId;

    private Long walletId;

    private Long userId;

    private Long ledgerId;

    private BigDecimal amount;

    private LocalDateTime createdAt;
}
