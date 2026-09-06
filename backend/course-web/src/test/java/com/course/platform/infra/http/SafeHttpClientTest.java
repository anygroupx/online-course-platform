package com.course.platform.infra.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafeHttpClientTest {
    private HttpServer server;
    private SafeHttpClient client;
    private OutboundRequestPolicy policy;
    private URI uri;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        uri = URI.create("http://pinned.invalid:" + server.getAddress().getPort() + "/api");
        policy = SsrfGuardTest.policy(Set.of("pinned.invalid"), Set.of("pinned.invalid"), Set.of(server.getAddress().getPort()));
        // Deliberate transport-only loopback fixture. Real SsrfGuard rejects it in separate tests.
        SsrfGuard guard = mock(SsrfGuard.class);
        when(guard.validate(any(), any())).thenAnswer(call -> new ValidatedDestination(call.getArgument(0), "pinned.invalid",
                List.of(InetAddress.getByName("127.0.0.1"))));
        when(guard.normalizeHost(anyString())).thenAnswer(call -> call.getArgument(0));
        client = new SafeHttpClient(guard);
        server.start();
    }
    @AfterEach void cleanup() { server.stop(0); }
    private void respond(int status, byte[] body, boolean chunked, Map<String,String> headers) {
        server.createContext("/api", exchange -> {
            calls.incrementAndGet();
            headers.forEach((k,v) -> exchange.getResponseHeaders().add(k,v));
            exchange.sendResponseHeaders(status, chunked ? 0 : body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); } catch (IOException ignored) { }
        });
    }
    @Test void pinsDnsAndIgnoresSystemProxy() {
        respond(200, "ok".getBytes(StandardCharsets.UTF_8), false, Map.of());
        ProxySelector old = ProxySelector.getDefault();
        AtomicInteger proxyLookups = new AtomicInteger();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                public List<Proxy> select(URI target) { proxyLookups.incrementAndGet(); return List.of(new Proxy(Proxy.Type.SOCKS,new InetSocketAddress("127.0.0.1",1))); }
                public void connectFailed(URI target, SocketAddress sa, IOException e) { }
            });
            assertEquals("ok", client.get(uri, Map.of(), policy).body());
            assertEquals(0, proxyLookups.get());
        } finally { ProxySelector.setDefault(old); }
        assertEquals(1, calls.get());
    }
    @Test void redirectsAreNotFollowed() {
        respond(302, "redirect".getBytes(), false, Map.of("Location", "http://127.0.0.1/private?key=secret"));
        assertReason(SafeHttpException.Reason.REDIRECT_BLOCKED);
        assertEquals(1, calls.get());
    }
    @Test void declaredBodyIsBounded() { respond(200, new byte[2048], false, Map.of()); assertReason(SafeHttpException.Reason.RESPONSE_TOO_LARGE); }
    @Test void chunkedBodyIsBounded() { respond(200, new byte[2048], true, Map.of()); assertReason(SafeHttpException.Reason.RESPONSE_TOO_LARGE); }
    @Test void decompressedBodyIsBounded() throws Exception {
        var buffer = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(buffer)) { gzip.write(new byte[4096]); }
        respond(200, buffer.toByteArray(), false, Map.of("Content-Encoding","gzip"));
        assertReason(SafeHttpException.Reason.RESPONSE_TOO_LARGE);
    }
    @Test void providerCatalogLargerThanOneMebibyteCanBeRead() {
        useProviderPolicy();
        respond(200, new byte[5_264_980], false, Map.of());
        assertEquals(5_264_980, client.get(uri, Map.of(), policy).body().length());
        assertEquals(1, calls.get());
    }
    @Test void providerCatalogStillHasAFiniteResponseLimit() {
        useProviderPolicy();
        respond(200, new byte[8_388_609], false, Map.of());
        assertReason(SafeHttpException.Reason.RESPONSE_TOO_LARGE);
        assertEquals(1, calls.get());
    }
    private void useProviderPolicy() {
        var properties = new OutboundSecurityProperties();
        properties.setProviderHttpAllowedHosts(List.of("pinned.invalid"));
        properties.setProviderAllowedPorts(List.of(uri.getPort()));
        var provider = new com.course.platform.domain.entity.ApiProvider();
        provider.setId(42L);
        provider.setStatus(com.course.platform.domain.entity.ApiProvider.STATUS_ACTIVE);
        provider.setApiUrl("http://pinned.invalid:" + uri.getPort());
        policy = new ProviderOutboundPolicyFactory(properties, new ProviderUrlNormalizer()).forProvider(provider, uri);
    }
    @Test void exactLimitAndErrorStatusAreReturned() {
        respond(503, new byte[1024], false, Map.of());
        var response = client.get(uri, Map.of(), policy);
        assertEquals(1024, response.body().length()); assertFalse(response.isSuccessful()); assertEquals(503,response.statusCode());
    }
    @Test void formEncodingAndUnsafeHeaderFiltering() {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        server.createContext("/api", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("X-Unsafe"));
            exchange.sendResponseHeaders(200, 2);
            try(var out=exchange.getResponseBody()) {out.write("ok".getBytes());}
        });
        assertEquals("ok",client.postForm(uri,Map.of("pass","a+b &中"),Map.of("Host","evil.test","Authorization","Bearer fixture", "X-Unsafe","a\r\nb"),policy).body());
        assertEquals("pass=a%2Bb%20%26%E4%B8%AD",body.get());
        assertTrue(host.get().startsWith("pinned.invalid:")); assertEquals("Bearer fixture",auth.get());
    }
    @Test void slowResponseTimesOutWithoutUnsafeCause() {
        server.createContext("/api", exchange -> {
            try { Thread.sleep(1200); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        assertReason(SafeHttpException.Reason.TIMEOUT);
    }
    private void assertReason(SafeHttpException.Reason reason) {
        var ex = assertThrows(SafeHttpException.class, () -> client.get(uri, Map.of(), policy));
        assertEquals(reason,ex.getReason()); assertNull(ex.getCause());
    }
}
