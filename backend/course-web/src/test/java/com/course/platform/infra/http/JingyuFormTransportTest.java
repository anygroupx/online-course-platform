package com.course.platform.infra.http;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.servicecommerce.JingyuNativeServiceGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real form encoding and response handling; DNS is pinned to a loopback-only fixture. */
class JingyuFormTransportTest {
    private static final String KEY = "fixture &+=密钥";
    private static final String PASSWORD = " 保留空格 &+=密码 ";
    private final ObjectMapper json = new ObjectMapper();
    private final List<Captured> calls = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private JingyuNativeServiceGateway gateway;
    private ApiProvider provider;
    private ServiceProduct product;
    private ServiceOrder order;
    private ObjectNode row, student;
    private String failingAction;
    private int failingStatus;
    private Map<String, String> fields;

    private record Captured(String method, URI uri, String contentType, String body) {}

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
        gateway = new JingyuNativeServiceGateway(new ApiHttpClient(new SafeHttpClient(guard),
                new ProviderOutboundPolicyFactory(properties, normalizer)), normalizer);
        provider = new ApiProvider(); provider.setId(9L); provider.setStatus(ApiProvider.STATUS_ACTIVE);
        provider.setProviderType("jingyu"); provider.setApiUrl("http://pinned.invalid:" + port + "/install");
        provider.setUsername("42"); provider.setApiKey(KEY);
        product = new ServiceProduct(); product.setProviderType("jingyu"); product.setUnitPrice(new BigDecimal("0.50"));
        order = new ServiceOrder(); order.setProviderType("jingyu"); order.setExternalSubOrderNo("17");
        order.setQuantity(2); order.setCompleted(0); order.setDistance(new BigDecimal("1.5"));
        order.setUnitCharge(new BigDecimal("0.75"));
        selectProject("keep");
        server.createContext("/", exchange -> {
            Map<String, String> query = decode(exchange.getRequestURI().getRawQuery());
            String action = query.getOrDefault("act", "");
            calls.add(new Captured(exchange.getRequestMethod(), exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String response = switch (action) {
                case "get_price" -> "{\"code\":1,\"data\":\"0.01\"}";
                case "get_keep_user_info", "get_bdlp_user_info" -> json.createObjectNode().put("code", 1)
                        .set("data", json.createObjectNode().set("student", student)).toString();
                case "get_keep_zone_data", "get_bdlp_zone_data" -> """
                        {"code":1,"data":{"list":[{"zone_id":7,"name":"操场 A&B + C=一层"}]}}
                        """;
                case "orders" -> {
                    var reply = json.createObjectNode().put("code", 1);
                    reply.set("data", json.createArrayNode().add(row));
                    reply.putObject("pagination").put("page", 1).put("limit", 20).put("last_page", 1).put("total", 1);
                    yield reply.toString();
                }
                case "keep_add", "bdlp_add" -> json.createObjectNode().put("code", 1).set("data",
                        json.createObjectNode().put("id", 17).put(query.get("appId") + "_order_id", query.get("appId") + "-451")).toString();
                case "get_task_data" -> "{\"code\":1,\"data\":{\"list\":[{\"run_task_id\":\"task-1\",\"start_time\":\""
                        + time(1) + "\",\"status_display\":\"未开始\"}]}}";
                case "get_remain_count" -> "{\"code\":1,\"data\":{\"refund_cnt\":2}}";
                case "change_run_status", "refund", "edit_task" -> "{\"code\":1}";
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

    private void selectProject(String project) throws Exception {
        boolean bdlp = "bdlp".equals(project);
        String account = bdlp ? "150123" : "13800138000";
        product.setProject(project); product.setRemoteProductId(project);
        order.setProject(project); order.setRemoteProductId(project); order.setExternalOrderNo(project + "-451");
        order.setScheduleJson(json.writeValueAsString(ServiceAccountFingerprint.create(account)));
        fields = bdlp ? Map.of("account", account, "zoneId", "7", "runType", "2")
                : Map.of("account", account, "password", PASSWORD, "zoneId", "7", "minMinute", "4", "maxMinute", "12");
        row = json.createObjectNode().put("id", "17").put(project + "_order_id", project + "-451")
                .put("uid", "42").put("user", account).put("num", "2").put("distance", "1.5")
                .put("zone_id", "7").put("zone_name", "操场 A&B + C=一层").put("status_display", "正常").put("pause", "1")
                .put("min_minute", "4").put("max_minute", "12").put("school_name", "示例学院 & 一号")
                .put("run_type", "2").put("is_auth", "1").put("auth_type", "设备授权 + A&B")
                .put("auth_time", "2026-01-01 08:00:00");
        student = json.createObjectNode();
        if (bdlp) {
            student.put("uid", account); student.putObject("school").put("name", "示例学院 & 一号");
            student.putObject("device").put("is_expired", false).put("login_type_display", "设备授权 + A&B")
                    .put("refresh_at", "2026-01-01 08:00:00");
            student.putObject("run_rule").put("min_dis", "1.5");
        } else {
            student.put("student_id", "81").put("phone", account).put("default_zone_id", "7");
            student.putObject("default_zone").put("zone_id", "7").put("name", "示例学院 & 一号");
        }
    }

    @AfterEach
    void cleanup() { if (server != null) server.stop(0); }

    private String time(int days) { return ServiceTime.now().toLocalDate().plusDays(days) + " 07:30:00"; }
    private PreparedOrder prepare() {
        return gateway.prepare(provider, product, new OrderForm(2, order.getDistance(), fields, List.of(time(1), time(2)), true));
    }
    private Captured action(String action) {
        return calls.stream().filter(c -> action.equals(decode(c.uri().getRawQuery()).get("act"))).findFirst().orElseThrow();
    }

    @ParameterizedTest @ValueSource(strings = {"keep", "bdlp"})
    void realTransportPreservesUtf8FlatPhpFieldsAndKeepsSecretsOutOfTheUrl(String project) throws Exception {
        selectProject(project);
        var result = gateway.execute(provider, product, order, "CREATE", prepare().fields());
        assertEquals(project + "-451", result.externalOrderNo()); assertEquals("17", result.externalSubOrderNo());
        for (Captured call : calls) {
            assertEquals("POST", call.method()); assertEquals("/install/jingyu/api.php", call.uri().getPath());
            assertTrue(call.contentType().startsWith("application/x-www-form-urlencoded"));
            var query = decode(call.uri().getRawQuery()); var body = decode(call.body());
            assertEquals(Set.of("appId", "act"), query.keySet()); assertEquals(project, query.get("appId"));
            assertEquals("42", body.get("login_uid")); assertEquals(KEY, body.get("login_key"));
            assertFalse(call.uri().toString().contains("fixture")); assertFalse(body.containsKey("form"));
        }
        var created = action(project + "_add"); var body = decode(created.body());
        assertTrue(created.body().contains("form%5Btask_list%5D%5B1%5D%5Bstart_time%5D="));
        assertFalse(created.body().contains("操场")); assertEquals("操场 A&B + C=一层", body.get("form[zone_name]"));
        assertEquals(time(2), body.get("form[task_list][1][start_time]")); assertEquals("1.5", body.get("form[dis]"));
        if ("keep".equals(project)) {
            assertEquals(PASSWORD, body.get("form[password]"));
            assertEquals(PASSWORD, decode(action("get_keep_user_info").body()).get("password"));
        } else {
            assertEquals("false", body.get("form[is_jrxy]")); assertEquals("2", body.get("form[run_type]"));
            assertEquals("1", body.get("form[is_auth]")); assertEquals("设备授权 + A&B", body.get("form[auth_type]"));
            assertEquals("示例学院 & 一号", body.get("form[school_name]"));
        }
        assertEquals("17", decode(action("orders").body()).get("keywords"));
        assertEquals(project + "-451", decode(action("get_task_data").body()).get(project + "_order_id"));
        assertEquals(1, calls.stream().filter(c -> (project + "_add").equals(decode(c.uri().getRawQuery()).get("act"))).count());
    }

    @Test
    void taskChangesUseTheBusinessReceiptRatherThanTheCurrentInterfaceRow() {
        var prepared = gateway.prepareAction(provider, order, "CHANGE_TIME", Map.of("taskId", "task-1", "page", "1", "time", time(3)));
        gateway.execute(provider, product, order, "CHANGE_TIME", prepared);
        var body = decode(action("edit_task").body());
        assertEquals("keep-451", body.get("keep_order_id")); assertFalse(body.containsKey("id"));
        assertEquals("task-1", body.get("form[run_task_id]")); assertEquals(time(3), body.get("form[start_time]"));
    }

    @ParameterizedTest @CsvSource({"CREATE,302", "CREATE,307", "CREATE,503", "PAUSE,302", "PAUSE,503", "REFUND,307", "REFUND,503", "CHANGE_TIME,307", "CHANGE_TIME,503"})
    void redirectsAndHttpErrorsNeverReplayAWritingRequest(String action, int status) {
        Map<String, Object> body = switch (action) {
            case "CREATE" -> prepare().fields();
            case "CHANGE_TIME" -> gateway.prepareAction(provider, order, action, Map.of("taskId", "task-1", "page", "1", "time", time(3)));
            default -> Map.of();
        };
        failingAction = switch (action) {
            case "CREATE" -> "keep_add";
            case "PAUSE" -> "change_run_status";
            case "REFUND" -> "refund";
            case "CHANGE_TIME" -> "edit_task";
            default -> throw new AssertionError(action);
        };
        failingStatus = status;
        var error = assertThrows(ProviderRequestException.class, () -> gateway.execute(provider, product, order, action, body));
        assertFalse(error.getMessage().contains(KEY)); assertFalse(error.getMessage().contains(PASSWORD));
        assertEquals(1, calls.stream().filter(c -> failingAction.equals(decode(c.uri().getRawQuery()).get("act"))).count());
        assertTrue(calls.stream().noneMatch(c -> c.uri().getPath().contains("must-not-follow")));
    }

    private Map<String, String> decode(String raw) {
        Map<String, String> form = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return form;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            assertNull(form.put(key, value), "duplicate form field");
        }
        return form;
    }
}
