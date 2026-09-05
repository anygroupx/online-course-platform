package com.course.platform.security;

import com.course.platform.domain.entity.SecurityAuditLog;
import com.course.platform.infra.http.OutboundPolicyRegistry;
import com.course.platform.infra.http.SafeHttpClient;
import com.course.platform.infra.http.SafeHttpException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Security alert webhook sent through the same SSRF-safe egress boundary. */
@Slf4j
@Component
public class SecurityAlertNotifier {

    private final ObjectMapper objectMapper;
    private final SafeHttpClient safeHttpClient;
    private final OutboundPolicyRegistry policies;

    public SecurityAlertNotifier(ObjectMapper objectMapper, SafeHttpClient safeHttpClient,
                                 OutboundPolicyRegistry policies) {
        this.objectMapper = objectMapper;
        this.safeHttpClient = safeHttpClient;
        this.policies = policies;
    }

    @Value("${app.security.alert-webhook:}")
    private String webhookUrl;

    public void notify(SecurityAuditLog event) {
        String text = String.format("[SECURITY][%s][%s] user=%s path=%s msg=%s",
                safeText(event.getSeverity()), safeText(event.getEventType()),
                safeText(event.getUsername() == null ? event.getUserId() : event.getUsername()),
                safeText(event.getRequestPath()), safeText(event.getMessage()));
        if ("CRITICAL".equalsIgnoreCase(event.getSeverity())) log.error(text); else log.warn(text);
        if (!StringUtils.hasText(webhookUrl)) return;

        CompletableFuture.runAsync(() -> send(event, text));
    }

    private String safeText(Object value) {
        if (value == null) return "-";
        String clean = String.valueOf(value).replaceAll("[\\p{Cntrl}]", " ").trim();
        return clean.length() <= 512 ? clean : clean.substring(0, 512);
    }

    private void send(SecurityAuditLog event, String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "text");
            body.put("content", Map.of("text", text));
            body.put("eventType", safeText(event.getEventType()));
            body.put("severity", safeText(event.getSeverity()));
            body.put("traceId", safeText(event.getTraceId()));
            var response = safeHttpClient.postJson(URI.create(webhookUrl), Map.of(),
                    objectMapper.writeValueAsString(body), policies.alertWebhook());
            if (!response.isSuccessful()) {
                log.warn("安全告警 Webhook 返回失败: status={}", response.statusCode());
            }
        } catch (SafeHttpException | IllegalArgumentException ex) {
            log.warn("安全告警 Webhook 被阻止或发送失败: reason={}",
                    ex instanceof SafeHttpException safe ? safe.getReason() : ex.getClass().getSimpleName());
        } catch (Exception ex) {
            log.warn("安全告警 Webhook 序列化失败: reason={}", ex.getClass().getSimpleName());
        }
    }
}
