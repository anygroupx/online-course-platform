package com.course.platform.security;

import java.time.Duration;

public record RateLimitRequest(
        String dimension,
        String keyMaterial,
        int limit,
        Duration window,
        String action
) {
    public RateLimitRequest {
        if (dimension == null || !dimension.matches("[a-z0-9:_-]{1,80}")) {
            throw new IllegalArgumentException("invalid rate-limit dimension");
        }
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalArgumentException("rate-limit key material is required");
        }
        if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("invalid rate-limit boundary");
        }
    }
}
