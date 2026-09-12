package com.course.platform.service.impl;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CourseOrderProgressLog;
import com.course.platform.infra.persistence.mapper.CourseOrderProgressLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CourseOrderProgressLogServiceImplTest {

    private final CourseOrderProgressLogMapper mapper = mock(CourseOrderProgressLogMapper.class);
    private final CourseOrderProgressLogServiceImpl service = new CourseOrderProgressLogServiceImpl(mapper);

    @Test
    void unchangedSnapshotDoesNotAppendLog() {
        CourseOrder order = order("25%", 1, "执行中");

        assertFalse(service.recordIfChanged(order, "25%", 1, "执行中",
                CourseOrderProgressLog.SOURCE_MANUAL_REFRESH));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseOrderProgressLog.class));
    }

    @Test
    void changedSnapshotAppendsCurrentValuesAndSource() {
        CourseOrder order = order("50%", 1, "已完成一半");

        assertTrue(service.recordIfChanged(order, "25%", 1, "执行中",
                CourseOrderProgressLog.SOURCE_SCHEDULED_SYNC));

        ArgumentCaptor<CourseOrderProgressLog> captor = ArgumentCaptor.forClass(CourseOrderProgressLog.class);
        verify(mapper).insert(captor.capture());
        CourseOrderProgressLog saved = captor.getValue();
        assertEquals(42L, saved.getOrderId());
        assertEquals("remote-42", saved.getThirdOrderId());
        assertEquals(6L, saved.getApiProviderId());
        assertEquals("50%", saved.getProgress());
        assertEquals(1, saved.getOrderStatus());
        assertEquals("已完成一半", saved.getRemarks());
        assertEquals(CourseOrderProgressLog.SOURCE_SCHEDULED_SYNC, saved.getSource());
    }

    private CourseOrder order(String progress, Integer status, String remarks) {
        CourseOrder order = new CourseOrder();
        order.setId(42L);
        order.setThirdOrderId("remote-42");
        order.setApiProviderId(6L);
        order.setProgress(progress);
        order.setOrderStatus(status);
        order.setRemarks(remarks);
        return order;
    }
}
