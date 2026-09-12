package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程订单进度变化日志。
 */
@Data
@TableName("course_order_progress_log")
public class CourseOrderProgressLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_MANUAL_REFRESH = "manual_refresh";
    public static final String SOURCE_SCHEDULED_SYNC = "scheduled_sync";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("third_order_id")
    private String thirdOrderId;

    @TableField("api_provider_id")
    private Long apiProviderId;

    @TableField("progress")
    private String progress;

    @TableField("order_status")
    private Integer orderStatus;

    @TableField("remarks")
    private String remarks;

    @TableField("source")
    private String source;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
