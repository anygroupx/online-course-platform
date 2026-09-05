package com.course.platform.infra.http;

import java.util.List;
import java.util.Map;

public record SafeHttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
