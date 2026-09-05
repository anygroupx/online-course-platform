package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudflareDnsResolverTest {

    private String response(int type, String address) {
        return "{\"Status\":0,\"Question\":[{\"name\":\"api.example.\",\"type\":" + type
                + "}],\"Answer\":[{\"type\":" + type + ",\"data\":\"" + address + "\"}]}";
    }

    @Test
    void resolvesAddressFamiliesConcurrentlyAndCachesSuccessfulAnswers() throws Exception {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.get(any(), any(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            OutboundRequestPolicy policy = call.getArgument(2);
            assertEquals("cloudflare-dns.com", uri.getHost());
            assertEquals(Set.of("cloudflare-dns.com", "dns.google"), policy.allowedHosts());
            assertEquals(5, policy.connectTimeout().toSeconds());
            assertEquals(8, policy.callTimeout().toSeconds());
            int type = uri.getQuery().endsWith("=1") ? 1 : 28;
            return new SafeHttpResponse(200,
                    response(type, type == 1 ? "8.8.8.8" : "2001:4860:4860::8888"), Map.of());
        });
        CloudflareDnsResolver resolver = new CloudflareDnsResolver(transport);

        assertEquals(2, resolver.resolve("api.example").size());
        assertEquals(2, resolver.resolve("api.example").size());

        verify(transport, times(2)).get(any(), any(), any());
    }

    @Test
    void fallsBackToSecondTrustedProvider() throws Exception {
        SafeHttpClient primary = mock(SafeHttpClient.class);
        SafeHttpClient fallback = mock(SafeHttpClient.class);
        when(primary.get(any(), any(), any()))
                .thenThrow(new SafeHttpException(SafeHttpException.Reason.TIMEOUT));
        when(fallback.get(any(), any(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            assertEquals("dns.google", uri.getHost());
            int type = uri.getQuery().endsWith("=1") ? 1 : 28;
            return new SafeHttpResponse(200,
                    response(type, type == 1 ? "8.8.4.4" : "2001:4860:4860::8844"), Map.of());
        });

        assertEquals(2, new CloudflareDnsResolver(primary, fallback).resolve("api.example").size());
        verify(primary, times(2)).get(any(), any(), any());
        verify(fallback, times(2)).get(any(), any(), any());
    }

    @Test
    void rejectsMismatchedQuestionsAndNonLiteralAnswers() {
        assertThrows(UnknownHostException.class,
                () -> CloudflareDnsResolver.parse(response(1, "8.8.8.8"), "other.example", 1));
        assertThrows(UnknownHostException.class,
                () -> CloudflareDnsResolver.parse(response(1, "secret.evil.test"), "api.example", 1));
        assertThrows(UnknownHostException.class,
                () -> CloudflareDnsResolver.parse(response(28, "127.0.0.1"), "api.example", 28));
        assertThrows(UnknownHostException.class,
                () -> CloudflareDnsResolver.parse("{\"Status\":2}", "api.example", 1));
    }

    @Test
    void privateAnswersRemainBlockedByGuard() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.get(any(), any(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            int type = uri.getQuery().endsWith("=1") ? 1 : 28;
            return new SafeHttpResponse(200,
                    response(type, type == 1 ? "127.0.0.1" : "fc00::1"), Map.of());
        });
        SsrfGuard guard = new SsrfGuard(new CloudflareDnsResolver(transport));

        assertEquals(SafeHttpException.Reason.BLOCKED_DESTINATION,
                assertThrows(SafeHttpException.class, () -> guard.validate(
                        URI.create("https://api.example"),
                        SsrfGuardTest.policy(Set.of("api.example"), Set.of(), Set.of()))).getReason());
    }

    @Test
    void resolverFailureDoesNotFallbackToSystemDns() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.get(any(), any(), any()))
                .thenThrow(new SafeHttpException(SafeHttpException.Reason.TIMEOUT));

        UnknownHostException error = assertThrows(UnknownHostException.class,
                () -> new CloudflareDnsResolver(transport).resolve("api.example"));

        assertNull(error.getCause());
        verify(transport, times(2)).get(any(), any(), any());
    }
}
