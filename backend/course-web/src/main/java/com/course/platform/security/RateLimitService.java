package com.course.platform.security;

public interface RateLimitService {
    RateLimitDecision check(RateLimitRequest request);
    void reset(String dimension, String keyMaterial);
}
