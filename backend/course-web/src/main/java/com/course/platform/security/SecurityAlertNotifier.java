package com.course.platform.security;

import com.course.platform.domain.entity.SecurityAuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 简易告警通道：Webhook（IM/邮件网关）+ 结构化日志。
 */
@Slf4j
@Component
public class SecurityAlertNotifier {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${app.security.alert-webhook:}")
    private String webhookUrl;

    public void notify(SecurityAuditLog event) {
        String text = String.format("[SECURITY][%s][%s] user=%s path=%s msg=%s",
                event.getSeverity(),
                event.getEventType(),
                event.getUsername() == null ? event.getUserId() : event.getUsername(),
                event.getRequestPath(),
                event.getMessage());
        if ("CRITICAL".equalsIgnoreCase(event.getSeverity())) {
            log.error(text);
        } else {
            log.warn(text);
        }
        if (!StringUtils.hasText(webhookUrl)) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "text");
            Map<String, String> content = new HashMap<>();
            content.put("text", text);
            body.put("content", content);
            body.put("eventType", event.getEventType());
            body.put("severity", event.getSeverity());
            body.put("traceId", event.getTraceId());
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        log.warn("安全告警 Webhook 发送失败: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("安全告警 Webhook 构造失败: {}", e.getMessage());
        }
    }
}
