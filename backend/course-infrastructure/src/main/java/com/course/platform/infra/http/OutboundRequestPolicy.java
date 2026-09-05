package com.course.platform.infra.http;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Immutable egress policy for one external integration. */
public record OutboundRequestPolicy(
        String name,
        Set<String> allowedHosts,
        Set<String> httpAllowedHosts,
        Set<Integer> allowedPorts,
        int maxResponseBytes,
        Duration connectTimeout,
        Duration readTimeout,
        Duration callTimeout
) {
    public OutboundRequestPolicy {
        name = name == null || name.isBlank() ? "external" : name.trim();
        allowedHosts = normalizeHosts(allowedHosts);
        httpAllowedHosts = normalizeHosts(httpAllowedHosts);
        allowedPorts = allowedPorts == null ? Set.of() : Set.copyOf(allowedPorts);
        if (allowedHosts.isEmpty()) throw new IllegalArgumentException("Outbound host allowlist must not be empty");
        if (!allowedHosts.containsAll(httpAllowedHosts)) {
            throw new IllegalArgumentException("HTTP hosts must also be present in the main allowlist");
        }
        if (maxResponseBytes < 1) throw new IllegalArgumentException("maxResponseBytes must be positive");
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()
                || callTimeout == null || callTimeout.isZero() || callTimeout.isNegative()) {
            throw new IllegalArgumentException("Outbound timeouts must be positive");
        }
    }

    private static Set<String> normalizeHosts(Set<String> values) {
        if (values == null) return Set.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }
}
