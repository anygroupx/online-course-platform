package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 周期阶段去重的站内通知出站记录。业务时间由服务显式使用统一Clock截断到秒。 */
@Data
@TableName("course_private_notification")
public class CoursePrivateNotification implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long termId;

    private Long userId;

    private String stage;

    private String payloadJson;

    private String status;

    private LocalDateTime availableAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime readAt;

    private Integer attempts;

    private Integer version;
}
