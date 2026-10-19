package com.course.platform.infra.http;

/** Generic outbound failure; never contains the target URL, credentials or response body. */
public class SafeHttpException extends RuntimeException {
    public enum Reason {
        BLOCKED_DESTINATION,
        PRIVATE_ADDRESS,
        PROVIDER_NOT_ACTIVE,
        DNS_FAILURE,
        REDIRECT_BLOCKED,
        TIMEOUT,
        RESPONSE_TOO_LARGE,
        NETWORK_FAILURE,
        TLS_FAILURE,
        HTTP_ERROR,
        INVALID_RESPONSE
    }

    private final Reason reason;

    /** HTTP status of the rejected response, or {@code null} for transport-level failures. */
    private final Integer statusCode;

    public SafeHttpException(Reason reason) {
        this(reason, (Integer) null);
    }

    public SafeHttpException(Reason reason, Throwable cause) {
        // Upstream exceptions may embed credentials in URLs or response fragments.
        // Retain only the classified reason, never the unsafe cause.
        this(reason, (Integer) null);
    }

    /**
     * Failure carrying the HTTP status that caused it.
     *
     * <p>The status is safe to record and to surface: it is neither a credential nor response
     * content, and without it a rejected response is indistinguishable from a dead network.</p>
     */
    public SafeHttpException(Reason reason, Integer statusCode) {
        super(statusCode == null
                ? "External HTTP request failed: " + reason.name()
                : "External HTTP request failed: " + reason.name() + " (HTTP " + statusCode + ")");
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public Reason getReason() {
        return reason;
    }

    /** HTTP status of the rejected response, or {@code null} when none was received. */
    public Integer getStatusCode() {
        return statusCode;
    }
}
