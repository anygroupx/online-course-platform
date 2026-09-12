package com.course.platform.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户可见的订单进度日志字段白名单。
 */
@Data
@Builder
public class CourseOrderProgressLogVO {
    private Long id;
    private String progress;
    private Integer orderStatus;
    private String remarks;
    private String source;
    private LocalDateTime createTime;
}
