package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 课程运营套餐草稿及可见性，不含真实资金。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_plan")
public class CoursePrivatePlan implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long platformId;

    private String planCode;

    private String planName;

    private String description;

    private String mode;

    private String familyCode;

    private String tierCode;

    private Integer tierRank;

    private Boolean visibleToAgents;

    private String applicationMode;

    private String status;

    private Integer sortOrder;

    private Integer draftVersion;

    private String draftConfigJson;

    private Long currentVersionId;

    private Long createdBy;

    private Long updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
