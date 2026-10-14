package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 草稿和成本版本绑定的独立风险审批。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_risk_approval")
public class CoursePrivateRiskApproval implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Integer draftVersion;

    private Long costVersionId;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String status;

    private String reason;

    private String bizNo;
}
