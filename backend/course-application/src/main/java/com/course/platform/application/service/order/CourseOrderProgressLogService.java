package com.course.platform.application.service.order;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CourseOrderProgressLog;

import java.util.List;

/**
 * 订单进度日志的追加和读取服务。
 */
public interface CourseOrderProgressLogService {

    /**
     * 仅当进度、订单状态或备注发生变化时追加一条快照。
     */
    boolean recordIfChanged(CourseOrder currentOrder,
                            String previousProgress,
                            Integer previousOrderStatus,
                            String previousRemarks,
                            String source);

    /**
     * 按记录时间倒序读取指定订单的进度日志。
     */
    List<CourseOrderProgressLog> listByOrderId(Long orderId);
}
