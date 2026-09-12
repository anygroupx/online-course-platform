package com.course.platform.infra.http;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.servicecommerce.LeidianNativeServiceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual PHP form encoding through the production HTTP clients, restricted to a synthetic loopback server. */
class LeidianFormTransportTest {
    record Captured(String method, URI uri, String contentType, String body) {}
    final ObjectMapper json = new ObjectMapper();
    final List<Captured> calls = new CopyOnWriteArrayList<>();
    HttpServer server;
    LeidianNativeServiceGateway gateway;
    ApiProvider provider;
    ServiceProduct product;
    ServiceOrder order;
    ObjectNode row;
    volatile String failingAction;
    volatile int failingStatus;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        var properties = new OutboundSecurityProperties();
        properties.setProviderHttpAllowedHosts(List.of("pinned.invalid"));
        properties.setProviderAllowedPorts(List.of(port));
        var normalizer = new ProviderUrlNormalizer();
        var guard = mock(SsrfGuard.class);
        when(guard.validate(any(), any())).thenAnswer(inv -> {
            URI uri = inv.getArgument(0);
            assertEquals("pinned.invalid", uri.getHost()); assertEquals(port, uri.getPort());
            return new ValidatedDestination(uri, "pinned.invalid", List.of(InetAddress.getByName("127.0.0.1")));
        });
        when(guard.normalizeHost(anyString())).thenAnswer(inv -> inv.getArgument(0));
        gateway = new LeidianNativeServiceGateway(new ApiHttpClient(new SafeHttpClient(guard),
                new ProviderOutboundPolicyFactory(properties, normalizer)), normalizer);
        provider = new ApiProvider(); provider.setId(9L); provider.setStatus(ApiProvider.STATUS_ACTIVE);
        provider.setProviderType("leidian"); provider.setApiUrl("http://pinned.invalid:" + port + "/install");
        provider.setUsername("42"); provider.setApiKey("fixture &+=密钥");
        product = new ServiceProduct(); product.setProviderType("leidian"); product.setProject("1");
        product.setRemoteProductId("1"); product.setUnitPrice(new BigDecimal("0.25"));
        order = new ServiceOrder(); order.setProviderType("leidian"); order.setProject("1"); order.setRemoteProductId("1");
        order.setExternalOrderNo("yid-451"); order.setExternalSubOrderNo("17"); order.setQuantity(10); order.setCompleted(0);
        order.setDistance(new BigDecimal("3.2")); order.setUnitCharge(new BigDecimal("0.50"));
        order.setScheduleJson(json.writeValueAsString(ServiceAccountFingerprint.create("student-001")));
        row = json.createObjectNode().put("id", "17").put("yid", "yid-451").put("user_id", "42")
                .put("app_id", "1").put("uid", "student-001").put("days", "10").put("mile", "3.2")
                .put("zone_id", "7").put("zone_name", "操场 A&B + C=一层").put("start_date", tomorrow())
                .put("run_time", "07:30:00 - 08:30:00").put("status", "1");
        row.set("run_week", json.readTree("[1,3,7]"));
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String action = query == null ? "" : decode(query).getOrDefault("act", "");
            calls.add(new Captured(exchange.getRequestMethod(), exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String response = switch (action) {
                case "get_price" -> "{\"code\":1,\"data\":\"0.12\"}";
                case "get_rule" -> """
                        {"code":1,"school":"示例学院 & 一号","rule":[{"mile":3.2,"start_time":"07:30","end_time":"08:30"}],
                         "run_zones":[{"id":7,"name":"操场 A&B + C=一层"}]}
                        """;
                case "orders" -> {
                    var reply = json.createObjectNode().put("code", 1);
                    reply.set("data", json.createArrayNode().add(row));
                    reply.putObject("pagination").put("page", 1).put("limit", 100).put("last_page", 1).put("total", 1);
                    yield reply.toString();
                }
                case "add_order" -> "{\"code\":1,\"id\":\"yid-451\"}";
                case "get_residue_num" -> "{\"code\":1,\"residue_num\":10}";
                case "cancel_order", "batch_edit_time" -> "{\"code\":1}";
                default -> "{\"code\":-1,\"msg\":\"fixture error only\"}";
            };
            int status = action.equals(failingAction) ? failingStatus : 200;
            if (status == 302 || status == 307) exchange.getResponseHeaders().set("Location", "/must-not-follow");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @AfterEach
    void cleanup() { if (server != null) server.stop(0); }

    String tomorrow() { return ServiceTime.now().toLocalDate().plusDays(1).toString(); }
    Map<String,String> plan() { return Map.of("startDate", tomorrow(), "startTime", "07:30", "endTime", "08:30", "weekdays", "1,3,7"); }
    PreparedOrder prepare() {
        var fields = new LinkedHashMap<>(plan()); fields.put("account", "student-001"); fields.put("zoneId", "7");
        return gateway.prepare(provider, product, new OrderForm(10, new BigDecimal("3.2"), fields, List.of(), true));
    }
    Captured action(String action) {
        return calls.stream().filter(c -> action.equals(decode(c.uri().getRawQuery()).get("act"))).findFirst().orElseThrow();
    }

    @Test
    void createPreservesUtf8PhpBracketFieldsAndDiscoversTheSeparateInternalRecordId() {
        var prepared = prepare();
        var result = gateway.execute(provider, product, order, "CREATE", prepared.fields());
        assertEquals("yid-451", result.externalOrderNo()); assertEquals("17", result.externalSubOrderNo());
        for (Captured call : calls) {
            assertEquals("POST", call.method()); assertEquals("/install/ldrun/api.php", call.uri().getPath());
            assertTrue(call.contentType().startsWith("application/x-www-form-urlencoded"));
            assertFalse(call.uri().toString().contains("fixture"));
            var query = decode(call.uri().getRawQuery()); var body = decode(call.body());
            assertFalse(query.containsKey("login_uid")); assertFalse(query.containsKey("login_key"));
            assertFalse(query.containsKey("uid")); assertFalse(query.containsKey("keywords"));
            assertEquals("42", body.get("login_uid")); assertEquals("fixture &+=密钥", body.get("login_key"));
            if ("get_price".equals(query.get("act"))) {
                assertEquals(Map.of("act", "get_price", "appId", "1"), query); assertFalse(body.containsKey("appId"));
            } else assertEquals(Set.of("act"), query.keySet());
        }
        var create = action("add_order"); var body = decode(create.body());
        assertTrue(create.body().contains("form%5Bweeks%5D%5B2%5D=7")); assertFalse(create.body().contains("示例学院"));
        assertEquals("示例学院 & 一号", body.get("form[schoolName]"));
        assertEquals("操场 A&B + C=一层", body.get("form[zoneName]"));
        assertEquals("3.2", body.get("form[mile]")); assertEquals("10", body.get("form[days]"));
        assertEquals("07:30:00 - 08:30:00", body.get("form[runTime]")); assertFalse(body.containsKey("form"));
        var discovery = decode(action("orders").body());
        assertEquals("2", discovery.get("type")); assertEquals("student-001", discovery.get("keywords"));
        assertEquals(1, calls.stream().filter(c -> "act=add_order".equals(c.uri().getRawQuery())).count());
    }

    @Test
    void priceQueryKeepsTheProjectInQueryAndOnlyCredentialsInTheBody() {
        gateway.fetchCatalog(provider, "4"); assertEquals(1, calls.size());
        assertEquals("act=get_price&appId=4", calls.get(0).uri().getRawQuery());
        assertEquals(Map.of("login_uid", "42", "login_key", "fixture &+=密钥"), decode(calls.get(0).body()));
    }

    @Test
    void batchTimeUsesTopLevelWeekArraysAndTheBusinessIdRatherThanTheInternalRowId() {
        var prepared = gateway.prepareAction(provider, order, "EDIT_PLAN", plan());
        gateway.execute(provider, product, order, "EDIT_PLAN", prepared);
        var body = decode(action("batch_edit_time").body());
        assertEquals("yid-451", body.get("orderId")); assertEquals("7", body.get("weeks[2]"));
        assertEquals("07:30:00", body.get("startTime")); assertEquals("08:30:00", body.get("endTime"));
        assertEquals(tomorrow(), body.get("startDate")); assertFalse(body.containsKey("form"));
        var lookup = decode(action("orders").body());
        assertEquals("1", lookup.get("type")); assertEquals("17", lookup.get("keywords"));
    }

    @ParameterizedTest @CsvSource({"CREATE,302", "CREATE,503", "CANCEL,307", "CANCEL,503"})
    void redirectsAndHttpFailuresNeitherReplayNorFollowABusinessWrite(String action, int responseStatus) {
        Map<String,Object> body = "CREATE".equals(action) ? prepare().fields() : Map.of();
        failingAction = "CREATE".equals(action) ? "add_order" : "cancel_order"; failingStatus = responseStatus;
        var error = assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, action, body));
        assertFalse(error.getMessage().contains("fixture"));
        assertEquals(1, calls.stream().filter(c -> failingAction.equals(decode(c.uri().getRawQuery()).get("act"))).count());
        assertTrue(calls.stream().noneMatch(c -> c.uri().getPath().contains("must-not-follow")));
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
