package com.course.platform.security;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {
    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(true, Math.max(0, remaining), 0);
    }

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, 0, Math.max(1, retryAfterSeconds));
    }
}
