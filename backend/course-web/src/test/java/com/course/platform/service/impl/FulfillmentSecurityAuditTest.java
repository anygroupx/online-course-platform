package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.SecurityAuditLog;
import com.course.platform.infra.persistence.mapper.SecurityAuditLogMapper;
import com.course.platform.security.SecurityAlertNotifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FulfillmentSecurityAuditTest {
    @Test
    void sensitiveReadAuditContainsOnlyIdentifiersAndFailsClosedWithoutStorageErrorDetails() {
        var mapper = mock(SecurityAuditLogMapper.class);
        var notifier = mock(SecurityAlertNotifier.class);
        var service = new SecurityAuditServiceImpl(mapper, notifier);
        when(mapper.insert(any(SecurityAuditLog.class))).thenReturn(1);
        service.recordFulfillmentRead(8L, "order-id");
        var record = ArgumentCaptor.forClass(SecurityAuditLog.class);
        verify(mapper).insert(record.capture());
        assertEquals("SERVICE_ORDER_FULFILLMENT_READ", record.getValue().getEventType());
        assertEquals("orderId=order-id,operatorId=8", record.getValue().getDetail());
        assertEquals(8L, record.getValue().getUserId());
        assertNotNull(record.getValue().getCreateTime());
        assertNull(record.getValue().getUsername());
        assertNull(record.getValue().getRequestPath());
        when(mapper.insert(any(SecurityAuditLog.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.recordFulfillmentRead(8L, "order-id"));
        when(mapper.insert(any(SecurityAuditLog.class))).thenThrow(new IllegalStateException("private-storage-detail"));
        var error = assertThrows(BusinessException.class, () -> service.recordFulfillmentRead(8L, "order-id"));
        assertFalse(error.toString().contains("private-storage-detail"));
        assertNull(error.getCause());
        verifyNoInteractions(notifier);
    }
}
