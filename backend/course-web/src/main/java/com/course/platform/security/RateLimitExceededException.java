package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;

public class RateLimitExceededException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ResultCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
