package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CloudflareDnsResolverTest {
    private String response(int type,String address) {
        return "{\"Status\":0,\"Question\":[{\"name\":\"api.example.\",\"type\":"+type+"}],\"Answer\":[{\"type\":"+type+",\"data\":\""+address+"\"}]}";
    }
    @Test void combinesBothAddressFamiliesThroughFixedPolicy() throws Exception {
        var transport=mock(SafeHttpClient.class);
        when(transport.get(any(),any(),any())).thenAnswer(call->{
            URI uri=call.getArgument(0); OutboundRequestPolicy policy=call.getArgument(2);
            assertEquals("cloudflare-dns.com",uri.getHost());assertEquals(Set.of("cloudflare-dns.com"),policy.allowedHosts());
            int type=uri.getQuery().endsWith("=1")?1:28;
            return new SafeHttpResponse(200,response(type,type==1?"8.8.8.8":"2001:4860:4860::8888"),Map.of());
        });
        assertEquals(2,new CloudflareDnsResolver(transport).resolve("api.example").size());
        verify(transport,times(2)).get(any(),any(),any());
    }
    @Test void rejectsMismatchedQuestionsAndNonLiteralAnswers() {
        assertThrows(UnknownHostException.class,()->CloudflareDnsResolver.parse(response(1,"8.8.8.8"),"other.example",1));
        assertThrows(UnknownHostException.class,()->CloudflareDnsResolver.parse(response(1,"secret.evil.test"),"api.example",1));
        assertThrows(UnknownHostException.class,()->CloudflareDnsResolver.parse(response(28,"127.0.0.1"),"api.example",28));
        assertThrows(UnknownHostException.class,()->CloudflareDnsResolver.parse("{\"Status\":2}","api.example",1));
    }
    @Test void privateAnswersRemainBlockedByGuard() {
        var transport=mock(SafeHttpClient.class);
        when(transport.get(any(),any(),any())).thenReturn(new SafeHttpResponse(200,response(1,"127.0.0.1"),Map.of()),
                new SafeHttpResponse(200,response(28,"fc00::1"),Map.of()));
        var guard=new SsrfGuard(new CloudflareDnsResolver(transport));
        assertEquals(SafeHttpException.Reason.BLOCKED_DESTINATION,assertThrows(SafeHttpException.class,
                ()->guard.validate(URI.create("https://api.example"),SsrfGuardTest.policy(Set.of("api.example"),Set.of(),Set.of()))).getReason());
    }
    @Test void resolverFailureDoesNotFallback() {
        var transport=mock(SafeHttpClient.class);
        when(transport.get(any(),any(),any())).thenThrow(new SafeHttpException(SafeHttpException.Reason.TIMEOUT));
        var ex=assertThrows(UnknownHostException.class,()->new CloudflareDnsResolver(transport).resolve("api.example"));
        assertNull(ex.getCause()); verify(transport,times(1)).get(any(),any(),any());
    }
}
