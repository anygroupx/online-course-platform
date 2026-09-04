package com.course.platform.security;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;

/** Expected session rejection whose preceding revocation changes must still commit. */
public class SessionAuthenticationException extends BusinessException {
    public SessionAuthenticationException(ResultCode code) {
        super(code);
    }
}
