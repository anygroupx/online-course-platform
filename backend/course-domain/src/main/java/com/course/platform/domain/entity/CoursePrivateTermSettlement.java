package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 不可覆盖的逐次结算事实及幂等指纹。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_term_settlement")
public class CoursePrivateTermSettlement implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long termId;

    private Integer revisionNo;

    private String evaluationType;

    private BigDecimal settledAmount;

    private Integer orderCount;

    private BigDecimal targetAmount;

    private Integer pendingCount;

    private String result;

    private LocalDateTime evaluatedAt;

    private LocalDateTime createdAt;

    private String operationFingerprint;
}
