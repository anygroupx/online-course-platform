package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.domain.entity.SecurityAuditLog;
import com.course.platform.infra.persistence.mapper.SecurityAuditLogMapper;
import com.course.platform.security.SecurityAlertNotifier;
import com.course.platform.shared.util.ServletUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SecurityAuditLogMapper securityAuditLogMapper;
    private final SecurityAlertNotifier securityAlertNotifier;

    @Override
    public void record(String eventType, String severity, Long userId, String username,
                       String path, String method, String message, String detail) {
        try {
            SecurityAuditLog entity = new SecurityAuditLog();
            entity.setEventType(eventType);
            entity.setSeverity(StringUtils.hasText(severity) ? severity : "INFO");
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setRequestPath(path);
            entity.setHttpMethod(method);
            entity.setMessage(message == null ? "" : truncate(message, 500));
            entity.setDetail(detail == null ? null : truncate(detail, 4000));
            entity.setTraceId(UUID.randomUUID().toString().replace("-", ""));
            entity.setCreateTime(LocalDateTime.now());
            try {
                entity.setIpAddress(ServletUtil.getClientIp());
            } catch (Exception e) {
                entity.setIpAddress("unknown");
            }
            securityAuditLogMapper.insert(entity);

            if ("WARN".equalsIgnoreCase(entity.getSeverity()) || "CRITICAL".equalsIgnoreCase(entity.getSeverity())) {
                securityAlertNotifier.notify(entity);
            }
        } catch (Exception e) {
            log.error("写入安全审计失败: type={}, msg={}, err={}", eventType, message, e.getMessage());
        }
    }

    @Override
    public IPage<SecurityAuditLog> query(String eventType, String severity, Integer page, Integer pageSize) {
        Page<SecurityAuditLog> p = new Page<>(page == null ? 1 : page, pageSize == null ? 20 : pageSize);
        LambdaQueryWrapper<SecurityAuditLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(eventType)) {
            qw.eq(SecurityAuditLog::getEventType, eventType);
        }
        if (StringUtils.hasText(severity)) {
            qw.eq(SecurityAuditLog::getSeverity, severity);
        }
        qw.orderByDesc(SecurityAuditLog::getCreateTime);
        return securityAuditLogMapper.selectPage(p, qw);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
