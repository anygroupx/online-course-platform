package com.course.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

class ProjectClientTicketBodyFilterTest {
    @Test void preservesBoundedJsonWithoutEchoingIt() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/project-client-tickets");req.setContextPath("/api");
        byte[] body = "{\"description\":\"fixture-private\"}".getBytes(StandardCharsets.UTF_8);req.setContent(body);
        var response = new MockHttpServletResponse();var called = new AtomicBoolean();
        new ProjectClientTicketBodyFilter().doFilter(req, response, (r,s) -> {
            called.set(true);assertArrayEquals(body, r.getInputStream().readAllBytes());
            assertEquals(body.length, r.getContentLength());
        });
        assertTrue(called.get());assertEquals("", response.getContentAsString());
    }
    @Test void rejectsUnknownLengthChunkedBodyBeforeController() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/external/projects/v1/tickets/id/replies");req.setContextPath("/api");
        req.setContent(new byte[ProjectClientTicketBodyFilter.MAX_BODY+1]);
        var wrapped = new HttpServletRequestWrapper(req) { @Override public long getContentLengthLong() {return -1;} };
        var response = new MockHttpServletResponse();
        new ProjectClientTicketBodyFilter().doFilter(wrapped,response,(r,s)->fail("oversized body reached controller"));
        assertEquals(413,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test void declaredOversizeIsRejectedWithoutReadingInput() throws Exception {
        var req = new MockHttpServletRequest("POST","/api/project-client-tickets");req.setContextPath("/api");
        var wrapped = new HttpServletRequestWrapper(req) {
            @Override public long getContentLengthLong() {return ProjectClientTicketBodyFilter.MAX_BODY+1;}
            @Override public jakarta.servlet.ServletInputStream getInputStream() {fail("must not read declared huge body");return null;}
        };
        var res = new MockHttpServletResponse();new ProjectClientTicketBodyFilter().doFilter(wrapped,res,(r,s)->fail());
        assertEquals(413,res.getStatus());
    }
    @Test void decodedServletPathIsAlsoBounded() throws Exception {
        var req = new MockHttpServletRequest("POST","/api/%70roject-client-tickets");req.setContextPath("/api");req.setServletPath("/project-client-tickets");
        req.setContent(new byte[ProjectClientTicketBodyFilter.MAX_BODY+1]);
        var res=new MockHttpServletResponse();new ProjectClientTicketBodyFilter().doFilter(req,res,(r,s)->fail());assertEquals(413,res.getStatus());
    }
    @Test void unrelatedRequestsRemainUntouched() throws Exception {
        var req=new MockHttpServletRequest("POST","/api/orders/create");req.setContextPath("/api");
        var called=new AtomicBoolean();new ProjectClientTicketBodyFilter().doFilter(req,new MockHttpServletResponse(),(r,s)->{assertSame(req,r);called.set(true);});assertTrue(called.get());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"/api/project-accounts/id/tickets","/api/project-tickets/id/reply-quotes"})
    void supplierTicketBodiesAlsoBoundedBeforeJsonParsing(String path) throws Exception {
        var req=new MockHttpServletRequest("POST",path);req.setContextPath("/api");
        req.setContent(new byte[ProjectClientTicketBodyFilter.MAX_BODY+1]);var res=new MockHttpServletResponse();
        new ProjectClientTicketBodyFilter().doFilter(req,res,(r,s)->fail("supplier ticket image bypassed limit"));
        assertEquals(413,res.getStatus());assertEquals("no-store",res.getHeader("Cache-Control"));
    }

}
