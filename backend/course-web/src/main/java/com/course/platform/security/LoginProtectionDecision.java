package com.course.platform.security;

public record LoginProtectionDecision(
        boolean allowed,
        boolean challengeRequired,
        int failureCount,
        boolean recentSuccess,
        long retryAfterSeconds
) {}
