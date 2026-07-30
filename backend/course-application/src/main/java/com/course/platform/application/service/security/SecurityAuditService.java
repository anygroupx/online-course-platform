package com.course.platform.application.service.security;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.SecurityAuditLog;

public interface SecurityAuditService {
    void record(String eventType, String severity, Long userId, String username,
                String path, String method, String message, String detail);

    IPage<SecurityAuditLog> query(String eventType, String severity, Integer page, Integer pageSize);
}
