package com.course.platform.infra.http;

/**
 * HTTP failure that additionally carries the upstream's business error code and message.
 *
 * <p>{@link SafeHttpException} deliberately carries no response content, because a raw body can
 * contain credentials or personal data. That rule is right in general, but it has a real cost:
 * the AQKS upstream reports business rejections with a non-2xx status <em>and</em> a JSON body
 * that explains the rejection, and discarding the body made a wrong HTTP method look exactly
 * like an expired session. Both surfaced as a bare {@code 404}.</p>
 *
 * <p>This subtype narrows the disclosure to the two fields that describe the rejection — the
 * business code and its operator-readable message — and never retains the rest of the body.
 * Both fields are parsed defensively: only a short, single-line, non-secret-looking value is
 * kept, so an unexpected payload cannot leak through this path.</p>
 */
public class UpstreamBusinessException extends SafeHttpException {

    /** Longest accepted message length; upstream reasons are short sentences. */
    private static final int MAX_MESSAGE_LENGTH = 200;

    private final String businessCode;
    private final String businessMessage;

    public UpstreamBusinessException(int statusCode, String businessCode, String businessMessage) {
        super(Reason.HTTP_ERROR, statusCode);
        this.businessCode = businessCode;
        this.businessMessage = sanitize(businessMessage);
    }

    /** Upstream business code such as {@code -8001}, or {@code null} when the body had none. */
    public String getBusinessCode() {
        return businessCode;
    }

    /** Upstream operator-readable reason, or {@code null} when absent or rejected as unsafe. */
    public String getBusinessMessage() {
        return businessMessage;
    }

    /**
     * Rejects anything that does not look like a short explanation.
     *
     * <p>This is the guard that keeps the "no response content" property: a message containing
     * line breaks, an obvious credential marker, or an excessive length is dropped rather than
     * logged. A missing message is always preferable to a leaked one.</p>
     */
    private static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_MESSAGE_LENGTH) {
            return null;
        }
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return null;
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        for (String marker : new String[]{"password", "passwd", "token", "cookie", "secret",
                "authorization", "bearer", "bearertoken", "session"}) {
            if (lower.contains(marker)) {
                return null;
            }
        }
        return trimmed;
    }

    @Override
    public String toString() {
        return "UpstreamBusinessException{status=" + getStatusCode()
                + ", code=" + businessCode + ", message=" + businessMessage + '}';
    }
}
