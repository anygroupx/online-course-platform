package com.course.platform.infra.docking;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对接请求日志脱敏工具。
 *
 * <p>返回新的 Map，不修改真正发送给第三方的请求参数。</p>
 */
public final class DockingLogSanitizer {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "uid", "key", "pass", "password", "token", "cookie",
            "authorization", "apikey", "user", "username", "id", "yid", "oid"
    );

    private DockingLogSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, ?> params) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (params == null) {
            return sanitized;
        }

        params.forEach((key, value) -> sanitized.put(
                key,
                isSensitiveKey(key) && value != null ? MASK : value
        ));
        return sanitized;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return SENSITIVE_KEYS.contains(normalized);
    }
}
