package com.course.platform.infra.docking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class DockingLogSanitizerTest {

    @Test
    @DisplayName("对接日志应隐藏凭据和学生身份参数且不修改原请求")
    void sanitize_shouldMaskSecretsWithoutChangingOriginalMap() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("uid", "real-uid");
        original.put("api_key", "real-key");
        original.put("pass", "student-password");
        original.put("username", "student-account");
        original.put("yid", "remote-order-id");
        original.put("school", "测试大学");

        Map<String, Object> sanitized = DockingLogSanitizer.sanitize(original);

        assertNotSame(original, sanitized);
        assertEquals("***", sanitized.get("uid"));
        assertEquals("***", sanitized.get("api_key"));
        assertEquals("***", sanitized.get("pass"));
        assertEquals("***", sanitized.get("username"));
        assertEquals("***", sanitized.get("yid"));
        assertEquals("测试大学", sanitized.get("school"));
        assertEquals("real-key", original.get("api_key"));
        assertEquals("student-password", original.get("pass"));
    }
}
