package com.course.platform.infra.http;

import java.util.List;
import java.util.Map;

/**
 * Binary sibling of {@link SafeHttpResponse} for payloads that are not UTF-8 text, such as
 * RFC 8484 DNS wire messages. The body is copied defensively so callers cannot mutate it.
 */
public record SafeHttpBinaryResponse(int statusCode, byte[] body, Map<String, List<String>> headers) {
    public SafeHttpBinaryResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public int length() {
        return body.length;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
