package com.course.platform.security;

import com.course.platform.common.result.ResultCode;

/** A rotated/revoked refresh token was replayed; its whole family has been revoked. */
public final class RefreshTokenReuseException extends SessionAuthenticationException {
    public RefreshTokenReuseException() {
        super(ResultCode.REFRESH_TOKEN_REUSE);
    }
}
