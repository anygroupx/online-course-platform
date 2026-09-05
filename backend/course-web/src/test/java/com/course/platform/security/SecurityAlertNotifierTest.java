package com.course.platform.security;

import com.course.platform.domain.entity.SecurityAuditLog;
import com.course.platform.infra.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class SecurityAlertNotifierTest {
    @Test void logsNonSuccessWithoutWebhookTokenOrBody(CapturedOutput output) {
        SafeHttpClient client = mock(SafeHttpClient.class);
        var properties = new OutboundSecurityProperties();
        properties.setAlertWebhookAllowedHosts(List.of("alert.example"));
        var notifier = new SecurityAlertNotifier(new ObjectMapper(),client,new OutboundPolicyRegistry(properties));
        ReflectionTestUtils.setField(notifier,"webhookUrl","https://alert.example/token-secret?key=secret-key");
        when(client.postJson(any(),any(),any(),any())).thenReturn(new SafeHttpResponse(503,"sensitive-body",Map.of()));
        ReflectionTestUtils.invokeMethod(notifier,"send",new SecurityAuditLog(),"test-alert");
        assertTrue(output.getAll().contains("status=503"));
        for (String secret : List.of("token-secret","secret-key","sensitive-body")) assertFalse(output.getAll().contains(secret));
    }
    @Test void stripsControlCharactersAndBoundsSecurityLogFields(CapturedOutput output) {
        var properties = new OutboundSecurityProperties();
        var notifier = new SecurityAlertNotifier(new ObjectMapper(), mock(SafeHttpClient.class),
                new OutboundPolicyRegistry(properties));
        SecurityAuditLog event = new SecurityAuditLog();
        event.setSeverity("WARN");
        event.setEventType("LOGIN\nFORGED");
        event.setUsername("student\radmin");
        event.setRequestPath("/login\tpath");
        event.setMessage("x".repeat(700));
        notifier.notify(event);
        assertFalse(output.getAll().contains("LOGIN\nFORGED"));
        assertFalse(output.getAll().contains("student\radmin"));
        assertTrue(output.getAll().contains("LOGIN FORGED"));
        assertFalse(output.getAll().contains("x".repeat(513)));
    }
}
