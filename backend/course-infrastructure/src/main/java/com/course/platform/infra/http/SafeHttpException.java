package com.course.platform.infra.http;

/** Generic outbound failure; never contains the target URL, credentials or response body. */
public class SafeHttpException extends RuntimeException {
    public enum Reason {
        BLOCKED_DESTINATION,
        DNS_FAILURE,
        REDIRECT_BLOCKED,
        TIMEOUT,
        RESPONSE_TOO_LARGE,
        NETWORK_FAILURE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    public SafeHttpException(Reason reason) {
        super("External HTTP request failed: " + reason.name());
        this.reason = reason;
    }

    public SafeHttpException(Reason reason, Throwable cause) {
        // Upstream exceptions may embed credentials in URLs or response fragments.
        // Retain only the classified reason, never the unsafe cause.
        super("External HTTP request failed: " + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
