package com.course.platform.application.service.security;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.SecurityAuditLog;

public interface SecurityAuditService {
    void record(String eventType, String severity, Long userId, String username,
                String path, String method, String message, String detail);

    /** Must persist successfully before sensitive fulfillment data is returned. */
    void recordFulfillmentRead(Long operatorId, String orderId);

    /** Must persist successfully before biometric material is returned. */
    void recordFulfillmentAssetRead(Long operatorId, String orderId, String assetId, String assetType);

    /** Required transaction participant: never swallow failures on RBAC mutations. */
    void recordRbacMutation(Long actorId, String targetUid, java.util.List<String> previousRoles,
                           java.util.List<String> newRoles);

    IPage<SecurityAuditLog> query(String eventType, String severity, Integer page, Integer pageSize);
}
