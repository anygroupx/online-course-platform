package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the narrow disclosure window that {@link UpstreamBusinessException} is allowed to open.
 *
 * <p>The parent {@link SafeHttpException} deliberately withholds all response content. This
 * subtype exists only because the AQKS upstream reports a wrong HTTP method and an expired
 * session as the same bare status code; keeping the business code and message is what makes
 * the two distinguishable. Everything beyond those two fields, and any message that could
 * carry a credential, must still be dropped.</p>
 */
class UpstreamBusinessExceptionTest {

    @Test
    void keepsTheBusinessCodeAndShortMessage() {
        var e = new UpstreamBusinessException(404, "-8002", "您没有执行本操作的权限！");

        assertEquals("-8002", e.getBusinessCode());
        assertEquals("您没有执行本操作的权限！", e.getBusinessMessage());
        assertEquals(404, e.getStatusCode());
        assertEquals(SafeHttpException.Reason.HTTP_ERROR, e.getReason());
    }

    @Test
    void dropsAMessageThatMentionsCredentials() {
        var e = new UpstreamBusinessException(401, "-1", "invalid password for user 123");

        assertNull(e.getBusinessMessage(), "a message naming a credential must not be recorded");
        assertEquals("-1", e.getBusinessCode(), "the code alone is still safe to keep");
    }

    @Test
    void dropsAMultilineMessage() {
        var e = new UpstreamBusinessException(500, "-9", "line one\nline two");

        assertNull(e.getBusinessMessage());
    }

    @Test
    void dropsAnOverlongMessage() {
        var e = new UpstreamBusinessException(500, "-9", "x".repeat(500));

        assertNull(e.getBusinessMessage());
    }

    @Test
    void dropsABlankMessageButKeepsTheCode() {
        var e = new UpstreamBusinessException(400, "-5", "   ");

        assertNull(e.getBusinessMessage());
        assertEquals("-5", e.getBusinessCode());
    }

    @Test
    void toleratesAnAbsentMessage() {
        var e = new UpstreamBusinessException(400, "-5", null);

        assertNull(e.getBusinessMessage());
        assertEquals("-5", e.getBusinessCode());
    }

    @Test
    void remainsASafeHttpExceptionSoExistingHandlersKeepWorking() {
        SafeHttpException e = new UpstreamBusinessException(404, "-8002", "no permission");

        assertEquals(SafeHttpException.Reason.HTTP_ERROR, e.getReason());
        assertEquals(404, e.getStatusCode());
    }
}
