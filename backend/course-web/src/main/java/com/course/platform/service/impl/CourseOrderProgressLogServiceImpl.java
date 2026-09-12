package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.order.CourseOrderProgressLogService;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CourseOrderProgressLog;
import com.course.platform.infra.persistence.mapper.CourseOrderProgressLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 本地订单进度日志服务。
 */
@Service
@RequiredArgsConstructor
public class CourseOrderProgressLogServiceImpl implements CourseOrderProgressLogService {

    private final CourseOrderProgressLogMapper progressLogMapper;

    @Override
    public boolean recordIfChanged(CourseOrder currentOrder,
                                   String previousProgress,
                                   Integer previousOrderStatus,
                                   String previousRemarks,
                                   String source) {
        if (currentOrder == null || currentOrder.getId() == null) {
            return false;
        }
        boolean changed = !Objects.equals(previousProgress, currentOrder.getProgress())
                || !Objects.equals(previousOrderStatus, currentOrder.getOrderStatus())
                || !Objects.equals(previousRemarks, currentOrder.getRemarks());
        if (!changed) {
            return false;
        }

        CourseOrderProgressLog progressLog = new CourseOrderProgressLog();
        progressLog.setOrderId(currentOrder.getId());
        progressLog.setThirdOrderId(currentOrder.getThirdOrderId());
        progressLog.setApiProviderId(currentOrder.getApiProviderId());
        progressLog.setProgress(currentOrder.getProgress());
        progressLog.setOrderStatus(currentOrder.getOrderStatus());
        progressLog.setRemarks(currentOrder.getRemarks());
        progressLog.setSource(source);
        progressLogMapper.insert(progressLog);
        return true;
    }

    @Override
    public List<CourseOrderProgressLog> listByOrderId(Long orderId) {
        return progressLogMapper.selectList(new LambdaQueryWrapper<CourseOrderProgressLog>()
                .eq(CourseOrderProgressLog::getOrderId, orderId)
                .orderByDesc(CourseOrderProgressLog::getCreateTime)
                .orderByDesc(CourseOrderProgressLog::getId));
    }
}
