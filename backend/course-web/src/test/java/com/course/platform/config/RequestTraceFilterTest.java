package com.course.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the correlation-id behaviour: always present, always echoed, never trusted blindly,
 * and never left behind on the pooled request thread.
 */
class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    private MockHttpServletResponse run(HttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response;
    }

    @Test
    void mintsAnIdWhenNoneIsSupplied() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest());

        String traceId = response.getHeader(RequestTraceFilter.HEADER);
        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
        assertEquals(32, traceId.length(), "a generated id is a hyphen-free UUID");
    }

    @Test
    void honoursAPlausibleInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.HEADER, "abc-123_XYZ");

        assertEquals("abc-123_XYZ", run(request).getHeader(RequestTraceFilter.HEADER));
    }

    @Test
    void rejectsAnInboundIdCarryingANewline() throws Exception {
        // A newline would let a caller forge extra lines in the log file.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.HEADER, "good\n2026-01-01 00:00:00.000 [x] ERROR forged");

        String traceId = run(request).getHeader(RequestTraceFilter.HEADER);

        assertNotEquals("good\n2026-01-01 00:00:00.000 [x] ERROR forged", traceId);
        assertFalse(traceId.contains("\n"));
    }

    @Test
    void rejectsAnOverlongInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.HEADER, "a".repeat(200));

        assertEquals(32, run(request).getHeader(RequestTraceFilter.HEADER).length());
    }

    @Test
    void clearsTheContextAfterTheRequest() throws Exception {
        // Request threads are pooled; a leftover value would label an unrelated request.
        assertNull(MDC.get(RequestTraceFilter.MDC_KEY));

        run(new MockHttpServletRequest());

        assertNull(MDC.get(RequestTraceFilter.MDC_KEY),
                "the correlation id must not survive the request");
    }

    @Test
    void clearsTheContextEvenWhenTheChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("boom");
                }));

        assertNull(MDC.get(RequestTraceFilter.MDC_KEY));
    }

    @Test
    void exposesTheIdToCodeRunningInsideTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];

        filter.doFilter(request, response, (req, res) -> seen[0] = MDC.get(RequestTraceFilter.MDC_KEY));

        assertNotNull(seen[0], "logging code inside the request must see the id");
        assertEquals(response.getHeader(RequestTraceFilter.HEADER), seen[0]);
    }
}
