package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 只新增的权益操作与退款资格复核事件。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_term_event")
public class CoursePrivateTermEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private Long termId;

    private Long sourceTermId;

    private String eventType;

    private String beforeJson;

    private String afterJson;

    private Long sourceVersionId;

    private Long targetVersionId;

    private Long operatorId;

    private String reason;

    private String bizNo;

    private LocalDateTime happenedAt;
}
