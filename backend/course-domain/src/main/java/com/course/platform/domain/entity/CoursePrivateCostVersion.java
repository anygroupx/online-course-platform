package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 人工核验且可追溯的真实单位成本版本。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_cost_version")
public class CoursePrivateCostVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long platformId;

    private BigDecimal unitCost;

    private String evidence;

    private Long verifiedBy;

    private LocalDateTime verifiedAt;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private String reason;

    private LocalDateTime createdAt;
}
