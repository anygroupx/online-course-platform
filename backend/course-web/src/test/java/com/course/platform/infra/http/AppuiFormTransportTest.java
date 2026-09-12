package com.course.platform.infra.http;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.ServiceProduct;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderForm;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.servicecommerce.AppuiNativeServiceGateway;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real ApiHttpClient/SafeHttpClient form encoding against an isolated loopback fixture, never a supplier. */
class AppuiFormTransportTest {
    record Captured(String method, URI uri, String contentType, String body) {}
    HttpServer server;
    AppuiNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    final List<Captured> calls = new CopyOnWriteArrayList<>();
    volatile int status = 200;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        var properties = new OutboundSecurityProperties();
        properties.setProviderHttpAllowedHosts(List.of("pinned.invalid"));
        properties.setProviderAllowedPorts(List.of(port));
        var normalizer = new ProviderUrlNormalizer();
        // This transport-only fixture pins a fake hostname to loopback. Real SSRF checks have separate tests.
        var guard = mock(SsrfGuard.class);
        when(guard.validate(any(), any())).thenAnswer(inv -> {
            URI uri = inv.getArgument(0);
            assertEquals("pinned.invalid", uri.getHost());
            assertEquals(port, uri.getPort());
            return new ValidatedDestination(uri, "pinned.invalid", List.of(InetAddress.getByName("127.0.0.1")));
        });
        when(guard.normalizeHost(anyString())).thenAnswer(inv -> inv.getArgument(0));
        gateway = new AppuiNativeServiceGateway(new ApiHttpClient(new SafeHttpClient(guard),
                new ProviderOutboundPolicyFactory(properties, normalizer)), normalizer);
        provider = new ApiProvider(); provider.setId(9L); provider.setStatus(ApiProvider.STATUS_ACTIVE);
        provider.setProviderType("appui"); provider.setApiUrl("http://pinned.invalid:" + port + "/install");
        provider.setUsername("saved-id"); provider.setApiKey("saved &+=密钥");
        product = new ServiceProduct(); product.setProviderType("appui"); product.setProject("1"); product.setRemoteProductId("1");
        server.createContext("/", exchange -> {
            calls.add(new Captured(exchange.getRequestMethod(), exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String response = switch (exchange.getRequestURI().getRawQuery()) {
                case "act=query" -> "{\"code\":1,\"userName\":\"已验证姓名\",\"address\":\"确认地址\"}";
                case "act=add" -> "{\"code\":1,\"oid\":451}";
                case "act=getSchoolList&pid=3" -> "{\"code\":1,\"data\":[\"示例学校\"]}";
                default -> "{\"code\":-1,\"msg\":\"private-error\"}";
            };
            if (status == 302) exchange.getResponseHeaders().set("Location", "/must-not-follow");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @AfterEach
    void cleanup() { if (server != null) server.stop(0); }

    @Test
    void actualPhpBracketFormsPreserveUtf8SymbolsArraysAndPasswordsWithoutQueryCredentials() {
        Map<String,String> fields = Map.of("account","student+001", "password"," p&+=密码 ",
                "address","教学楼 A&B + C=一层", "startTime","07:31", "endTime","18:11",
                "weekdays","1,3,7", "reports","2,3");
        var prepared = gateway.prepare(provider, product, new OrderForm(12, null, fields, List.of(), true));
        var result = gateway.execute(provider, product, null, "CREATE", prepared.fields());
        assertEquals("451", result.externalOrderNo()); assertEquals(2, calls.size());
        for (Captured call : calls) {
            assertEquals("POST", call.method());
            assertEquals("/install/appui/api.php", call.uri().getPath());
            assertTrue(call.contentType().startsWith("application/x-www-form-urlencoded"));
            assertFalse(call.uri().toString().contains("saved"));
            assertTrue(call.body().contains("form%5Bpass%5D="));
            assertFalse(call.body().contains("密码"));
            var body = decode(call.body());
            assertEquals(" p&+=密码 ", body.get("form[pass]"));
            assertEquals("student+001", body.get("form[user]"));
            assertEquals("saved &+=密钥", body.get("login_key"));
        }
        assertEquals("act=query", calls.get(0).uri().getRawQuery());
        assertEquals("act=add", calls.get(1).uri().getRawQuery());
        var add = decode(calls.get(1).body());
        assertEquals("教学楼 A&B + C=一层", add.get("form[address]"));
        assertEquals("已验证姓名", add.get("form[userName]"));
        assertEquals("12", add.get("form[days1]"));
        assertEquals("7", add.get("form[week][2]"));
        assertEquals("2", add.get("form[report][0]"));
        assertEquals("3", add.get("form[report][1]"));
        assertFalse(add.containsKey("form"));
    }

    @Test
    void schoolSelectorRepeatsOnlyTheProjectInTheFixedQueryAndFormBody() {
        product.setProject("3"); product.setRemoteProductId("3");
        assertEquals("示例学校", gateway.schools(provider, product, 1, "示例").items().get(0).name());
        assertEquals(1, calls.size());
        assertEquals("act=getSchoolList&pid=3", calls.get(0).uri().getRawQuery());
        assertEquals("3", decode(calls.get(0).body()).get("pid"));
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 503})
    void redirectsAndHttpFailuresNeverReplayABusinessWrite(int responseStatus) {
        status = responseStatus;
        var failure = assertThrows(ProviderRequestException.class,
                () -> gateway.execute(provider, product, null, "CREATE", Map.of("form[pid]", "1")));
        assertFalse(failure.getMessage().contains("saved"));
        assertEquals(1, calls.size());
    }

    private Map<String,String> decode(String raw) {
        Map<String,String> form = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            assertNull(form.put(key, value), "duplicate form field");
        }
        return form;
    }
}
