package com.course.platform.domain.event;

import java.time.LocalDateTime;

/** 原事实事务提交后触发核验，事件时间来自实际操作，不用监听器时间伪造履约。 */
public record CourseOrderFactsChangedEvent(Long orderId,String eventId,String source,Long operatorId,String reason,String beforeJson,String afterJson,LocalDateTime happenedAt){}
