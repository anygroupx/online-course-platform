package com.course.platform.infra.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrustedDohDnsResolverTest {

    private static final String PRIMARY = "cloudflare-dns.com";
    private static final String FALLBACK = "dns.google";

    private byte[] response(String questionName, int type, String... answers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        short16(out, 0);
        short16(out, 0x8180);
        short16(out, 1);
        short16(out, answers.length);
        short16(out, 0);
        short16(out, 0);
        name(out, questionName);
        short16(out, type);
        short16(out, 1);
        for (String answer : answers) {
            name(out, questionName);
            short16(out, type);
            short16(out, 1);
            out.write(0);
            out.write(0);
            out.write(1);
            out.write(44);
            byte[] raw = literal(answer);
            short16(out, raw.length);
            out.writeBytes(raw);
        }
        return out.toByteArray();
    }

    private byte[] literal(String value) {
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void name(ByteArrayOutputStream out, String value) {
        for (String label : value.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.writeBytes(bytes);
        }
        out.write(0);
    }

    private void short16(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private DohEndpointProperties endpoint(String host, String... bootstrap) {
        DohEndpointProperties properties = new DohEndpointProperties();
        properties.setHost(host);
        properties.setBootstrap(List.of(bootstrap));
        return properties;
    }

    @Test
    void queriesBothEndpointsConcurrentlyAndCachesFirstSuccessfulAnswer() throws Exception {
        SafeHttpClient primary = mock(SafeHttpClient.class);
        SafeHttpClient fallback = mock(SafeHttpClient.class);
        when(primary.postBinary(any(), any(), any(), anyString(), any())).thenAnswer(call ->
                new SafeHttpBinaryResponse(200, response("api.example", 1, "8.8.8.8"), Map.of()));
        when(fallback.postBinary(any(), any(), any(), anyString(), any())).thenAnswer(call ->
                new SafeHttpBinaryResponse(200, response("api.example", 1, "8.8.4.4"), Map.of()));

        TrustedDohDnsResolver resolver = new TrustedDohDnsResolver(primary, fallback);
        List<InetAddress> resolved = resolver.resolve("api.example");

        // Both endpoints are trusted peers queried in parallel, so the first to answer wins;
        // which one that is must not be asserted, only that the answer is one of them.
        assertEquals(1, resolved.size());
        String answer = resolved.get(0).getHostAddress();
        assertTrue(Set.of("8.8.8.8", "8.8.4.4").contains(answer), "unexpected answer " + answer);

        // Second call must be served from cache, so neither transport is touched again.
        assertEquals(answer, resolver.resolve("api.example").get(0).getHostAddress());
        verify(primary, timeout(1_000).times(2)).postBinary(any(), any(), any(), anyString(), any());
        verify(fallback, timeout(1_000).times(2)).postBinary(any(), any(), any(), anyString(), any());
    }

    @Test
    void usesWireFormatAndConfiguredPath() throws Exception {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.postBinary(any(), any(), any(), anyString(), any())).thenAnswer(call -> {
            URI uri = call.getArgument(0);
            assertEquals(PRIMARY, uri.getHost());
            assertEquals("/dns-query", uri.getPath());
            assertEquals("application/dns-message", call.getArgument(3));
            byte[] sent = call.getArgument(2);
            // A wire-format query, not a JSON body.
            assertTrue(sent.length > 12);
            assertEquals(1, ((sent[4] & 0xFF) << 8) | (sent[5] & 0xFF));
            return new SafeHttpBinaryResponse(200, response("api.example", 1, "8.8.8.8"), Map.of());
        });

        new TrustedDohDnsResolver(PRIMARY, "dns-query", transport).resolve("api.example");
    }

    @Test
    void fallsBackWhenPrimaryFails() throws Exception {
        SafeHttpClient primary = mock(SafeHttpClient.class);
        SafeHttpClient fallback = mock(SafeHttpClient.class);
        when(primary.postBinary(any(), any(), any(), anyString(), any()))
                .thenThrow(new SafeHttpException(SafeHttpException.Reason.TIMEOUT));
        when(fallback.postBinary(any(), any(), any(), anyString(), any())).thenAnswer(call ->
                new SafeHttpBinaryResponse(200, response("api.example", 1, "8.8.4.4"), Map.of()));

        assertEquals("8.8.4.4", new TrustedDohDnsResolver(primary, fallback)
                .resolve("api.example").get(0).getHostAddress());
        verify(primary, timeout(1_000).times(2)).postBinary(any(), any(), any(), anyString(), any());
        verify(fallback, timeout(1_000).times(2)).postBinary(any(), any(), any(), anyString(), any());
    }

    @Test
    void privateAnswersRemainBlockedByGuard() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.postBinary(any(), any(), any(), anyString(), any())).thenAnswer(call ->
                new SafeHttpBinaryResponse(200, response("api.example", 1, "127.0.0.1"), Map.of()));
        SsrfGuard guard = new SsrfGuard(new TrustedDohDnsResolver(PRIMARY, "dns-query", transport));

        assertEquals(SafeHttpException.Reason.PRIVATE_ADDRESS,
                assertThrows(SafeHttpException.class, () -> guard.validate(
                        URI.create("https://api.example"),
                        SsrfGuardTest.policy(Set.of("api.example"), Set.of(), Set.of()))).getReason());
    }

    @Test
    void resolverFailureDoesNotFallbackToSystemDns() {
        SafeHttpClient transport = mock(SafeHttpClient.class);
        when(transport.postBinary(any(), any(), any(), anyString(), any()))
                .thenThrow(new SafeHttpException(SafeHttpException.Reason.TIMEOUT));

        UnknownHostException error = assertThrows(UnknownHostException.class,
                () -> new TrustedDohDnsResolver(PRIMARY, "dns-query", transport).resolve("api.example"));

        assertNull(error.getCause());
        verify(transport, times(2)).postBinary(any(), any(), any(), anyString(), any());
    }

    @Test
    void rejectsConfigurationWithoutBootstrapAddresses() {
        DohEndpointProperties noBootstrap = new DohEndpointProperties();
        noBootstrap.setHost(PRIMARY);

        assertThrows(IllegalStateException.class,
                () -> new TrustedDohDnsResolver(List.of(noBootstrap)));
        assertThrows(IllegalStateException.class, () -> new TrustedDohDnsResolver(List.of()));
    }

    @Test
    void rejectsNonLiteralBootstrapEntries() {
        // A hostname here would make the resolver depend on the system DNS it exists to bypass.
        DohEndpointProperties hostname = endpoint(PRIMARY, "not-an-ip");
        assertThrows(IllegalStateException.class,
                () -> new TrustedDohDnsResolver(List.of(hostname)));
    }
}
