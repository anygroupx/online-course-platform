package com.course.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a per-request correlation id into the logging context so every line written while
 * handling one request can be pulled out together.
 *
 * <p>Without this, a single failure produces several unrelated lines that can only be
 * matched by guessing at timestamps — which is exactly what made the AQKS study-time
 * defect slow to read.</p>
 *
 * <p>The id is always echoed in the {@code X-Request-Id} response header, and an inbound
 * {@code X-Request-Id} is honoured only when it is a plausible short token. Accepting an
 * arbitrary inbound value would let a caller stuff a long or multi-line string into every
 * log line for that request.</p>
 *
 * <p>The context is cleared in a {@code finally} block: pooled request threads are reused,
 * so a value left behind would attach itself to an unrelated later request.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestTraceFilter extends OncePerRequestFilter {

    /** Logging context key, referenced by the log pattern. */
    public static final String MDC_KEY = "traceId";

    /** Request and response header carrying the correlation id. */
    public static final String HEADER = "X-Request-Id";

    /** Maximum length accepted from an inbound header. */
    private static final int MAX_INBOUND_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Must not leak into the next request handled by this pooled thread.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Uses an inbound id when it is safe to do so, otherwise mints one.
     *
     * <p>Only letters, digits, hyphens and underscores are accepted. This rejects newlines
     * (which would forge additional log lines) and any oversized value.</p>
     */
    private String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER);
        if (inbound != null) {
            String trimmed = inbound.trim();
            if (!trimmed.isEmpty()
                    && trimmed.length() <= MAX_INBOUND_LENGTH
                    && trimmed.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
