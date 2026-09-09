package com.course.platform.infra.servicecommerce;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.servicecommerce.NativeServiceGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.vo.plugin.PluginProduct;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** The Wuxin API deliberately uses per-action success codes, not one code for all operations. */
@Component
@RequiredArgsConstructor
public class WuxinNativeServiceGateway implements NativeServiceGateway, PluginReadOnlyConnector {
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(20)
                                                    .maxNumberLength(32)
                                                    .maxStringLength(4096)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Map<String, Integer> ACTIONS =
            Map.ofEntries(
                    Map.entry("getWuxinSdxySchoolInfo", 1),
                    Map.entry("getWuxinSdxyPrice", 1),
                    Map.entry("addWuxinSdxyOrder", 0),
                    Map.entry("getWuxinSdxyOrdersList", 0),
                    Map.entry("getWuxinSdxyOrderConfig", 1),
                    Map.entry("getWuxinSdxyOrderRecords", 1),
                    Map.entry("editWuxinSdxyOrder", 1),
                    Map.entry("deleteWuxinSdxyOrder", 1),
                    Map.entry("increaseWuxinSdxyOrder", 1),
                    Map.entry("reassignWuxinSdxyOrder", 1));
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> INPUTS =
            Set.of(
                    "authCode",
                    "runPlanCode",
                    "fenceCode",
                    "runType",
                    "startDate",
                    "runTime",
                    "endTime",
                    "weekdays",
                    "pace",
                    "message");

    @Override
    public String getProviderType() {
        return "wuxin";
    }

    @Override
    public void testConnection(ApiProvider provider) {
        JsonNode data = call(provider, "getWuxinSdxyOrdersList", Map.of("page", 1, "pageSize", 1));
        rows(data, 1);
    }

    @Override
    public List<PluginProduct> fetchCatalog(ApiProvider provider, String project) {
        if (project != null && !project.isBlank() && !"sdxy".equals(project))
            throw bad("无心仅支持闪动项目");
        JsonNode data = call(provider, "getWuxinSdxyPrice", Map.of());
        return List.of(new PluginProduct("sdxy", "无心 · 闪动校园", decimal(data.path("price")), "元/次"));
    }

    @Override
    public Lookup lookup(ApiProvider provider, ServiceProduct product, Map<String, String> fields) {
        fields(fields);
        return choices(
                call(
                        provider,
                        "getWuxinSdxySchoolInfo",
                        Map.of("auth_code", required(fields, "authCode", 512))));
    }

    private Lookup choices(JsonNode data) {
        List<Choice> choices = new ArrayList<>();
        for (String kind : List.of("run_plans", "areas")) {
            JsonNode values = data.path(kind);
            if (!values.isArray() || values.size() > 200) throw invalid();
            for (JsonNode v : values)
                choices.add(
                        new Choice(
                                "run_plans".equals(kind) ? "runPlanCode" : "fenceCode",
                                id(v.path("code")),
                                text(v.path("name"), 120)));
        }
        choices.add(new Choice("runType", "1", "日常跑"));
        choices.add(new Choice("runType", "2", "自由跑"));
        Map<String, String> suggested = new LinkedHashMap<>();
        for (Choice choice : choices) suggested.putIfAbsent(choice.field(), choice.value());
        return new Lookup(suggested, choices, "使用本人授权码读取计划与区域；不采集密码或生物识别数据");
    }

    @Override
    public PreparedOrder prepare(ApiProvider provider, ServiceProduct product, OrderForm form) {
        fields(form.fields());
        if (!form.authorizedAccount()) throw bad("请确认授权使用该账号");
        if (form.quantity() < 1 || form.quantity() > 365) throw bad("次数须为 1–365");
        String auth = required(form.fields(), "authCode", 512);
        Lookup allowed =
                choices(call(provider, "getWuxinSdxySchoolInfo", Map.of("auth_code", auth)));
        Map<String, Object> body = planFields(allowed, form.fields(), form.distance());
        String date = required(form.fields(), "startDate", 10);
        try {
            LocalDate start = LocalDate.parse(date);
            if (start.isBefore(LocalDate.now(SERVICE_ZONE))
                    || start.isAfter(LocalDate.now(SERVICE_ZONE).plusYears(1)))
                throw bad("开始日期须在未来一年内");
        } catch (java.time.format.DateTimeParseException ex) {
            throw bad("开始日期格式错误");
        }
        body.put("auth_code", auth);
        body.put("start_date", date);
        body.put("order_num", form.quantity());
        return new PreparedOrder(body, form.quantity(), form.distance(), BigDecimal.ONE, "授权账号");
    }

    private Map<String, Object> planFields(
            Lookup options, Map<String, String> fields, BigDecimal distance) {
        if (distance == null
                || distance.compareTo(new BigDecimal("0.1")) < 0
                || distance.compareTo(new BigDecimal("50")) > 0
                || distance.stripTrailingZeros().scale() > 2) throw bad("距离不合法");
        String plan = selected(options, fields, "runPlanCode"),
                fence = selected(options, fields, "fenceCode"),
                type = selected(options, fields, "runType");
        String start = clock(required(fields, "runTime", 5)),
                end = clock(required(fields, "endTime", 5));
        if (start.compareTo(end) >= 0) throw bad("结束时间须晚于开始时间");
        String weekdays = required(fields, "weekdays", 20);
        if (!weekdays.matches("[1-7](?:,[1-7]){0,6}")
                || new HashSet<>(Arrays.asList(weekdays.split(","))).size()
                        != weekdays.split(",").length) throw bad("请选择不重复的周一至周日");
        String pace = required(fields, "pace", 5);
        if (!pace.matches("[0-9]{1,2}(?:\\.[0-9])?")) throw bad("配速不合法");
        BigDecimal speed = new BigDecimal(pace);
        if (speed.compareTo(new BigDecimal("3")) < 0 || speed.compareTo(new BigDecimal("15")) > 0)
            throw bad("配速须为每公里 3–15 分钟");
        String name =
                options.choices().stream()
                        .filter(c -> "fenceCode".equals(c.field()) && fence.equals(c.value()))
                        .findFirst()
                        .orElseThrow()
                        .label();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run_plan_code", plan);
        body.put("fence_code", fence);
        body.put("zone_name", name);
        body.put("run_type", type);
        body.put("run_time", start + "-" + end);
        body.put("run_meter", distance.toPlainString());
        body.put("run_week", "[" + weekdays + "]");
        body.put("run_speed", speed.toPlainString());
        body.put("mark", optional(fields, "message", 500));
        return body;
    }

    @Override
    public Lookup orderOptions(ApiProvider provider, ServiceOrder order) {
        JsonNode data = configuration(provider, order);
        Lookup options = choices(data.path("school_info"));
        JsonNode remote = data.path("order"), schedule = data.path("schedule_config");
        Map<String, String> current = new LinkedHashMap<>();
        putChoice(current, options, "runPlanCode", schedule.path("run_plan_code"));
        putChoice(current, options, "fenceCode", schedule.path("fence_code"));
        putChoice(current, options, "runType", remote.path("run_type"));
        String runTime = remote.path("run_time").asText("");
        if (runTime.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]-(?:[01][0-9]|2[0-3]):[0-5][0-9]")) {
            current.put("runTime", runTime.substring(0, 5));
            current.put("endTime", runTime.substring(6));
        }
        for (var entry : Map.of("start_time", "runTime", "end_time", "endTime").entrySet()) {
            if (schedule.hasNonNull(entry.getKey()))
                current.put(entry.getValue(), clock(text(schedule.path(entry.getKey()), 5)));
        }
        if (schedule.hasNonNull("distance"))
            current.put(
                    "distance",
                    decimal(schedule.path("distance"))
                            .movePointLeft(3)
                            .stripTrailingZeros()
                            .toPlainString());
        else if (remote.hasNonNull("run_meter"))
            current.put("distance", decimal(remote.path("run_meter")).toPlainString());
        if (schedule.hasNonNull("weekday")) {
            Map<String, String> days =
                    Map.of(
                            "mon", "1", "tue", "2", "wed", "3", "thu", "4", "fri", "5", "sat", "6",
                            "sun", "7");
            List<String> selectedDays = new ArrayList<>();
            for (String day : text(schedule.path("weekday"), 27).split(",")) {
                if (!days.containsKey(day) || selectedDays.contains(days.get(day))) throw invalid();
                selectedDays.add(days.get(day));
            }
            current.put("weekdays", String.join(",", selectedDays));
        } else if (remote.hasNonNull("run_week")) {
            JsonNode days = embedded(remote.path("run_week"));
            if (!days.isArray() || days.isEmpty() || days.size() > 7) throw invalid();
            List<String> selectedDays = new ArrayList<>();
            for (JsonNode day : days) {
                String value = day.asText();
                if (!value.matches("[1-7]") || selectedDays.contains(value)) throw invalid();
                selectedDays.add(value);
            }
            current.put("weekdays", String.join(",", selectedDays));
        }
        if (schedule.hasNonNull("pace")) {
            try {
                BigDecimal minutes =
                        decimal(schedule.path("pace"))
                                .divide(
                                        BigDecimal.valueOf(60),
                                        1,
                                        java.math.RoundingMode.UNNECESSARY);
                current.put("pace", minutes.stripTrailingZeros().toPlainString());
            } catch (ArithmeticException ex) {
                throw invalid();
            }
        } else if (remote.hasNonNull("run_speed"))
            current.put("pace", decimal(embedded(remote.path("run_speed"))).toPlainString());
        if (remote.hasNonNull("mark") && !remote.path("mark").asText().isEmpty())
            current.put("message", text(remote.path("mark"), 500));
        return new Lookup(current, options.choices(), "已读取当前计划；未返回的项目请重新选择，不会自动覆盖为默认值。");
    }

    @Override
    public Map<String, Object> prepareAction(
            ApiProvider provider, ServiceOrder order, String action, Map<String, String> fields) {
        if ("EDIT_PLAN".equals(action)) {
            Map<String, String> copy = new LinkedHashMap<>(fields);
            String distance = copy.remove("distance");
            fields(copy);
            if (copy.containsKey("authCode") || copy.containsKey("startDate"))
                throw bad("编辑计划不能修改授权码或开始日期");
            if (distance == null || !distance.matches("[0-9]{1,2}(?:\\.[0-9]{1,2})?"))
                throw bad("距离不合法");
            return planFields(orderOptions(provider, order), copy, new BigDecimal(distance));
        }
        if (fields != null && !fields.isEmpty()) throw bad("此操作不接受额外参数");
        return Map.of();
    }

    @Override
    public int refundRemaining(ApiProvider provider, ServiceOrder order) {
        JsonNode remote = configuration(provider, order).path("order");
        return count(remote.path("residue_num"));
    }

    @Override
    public void checkAddTimes(ApiProvider provider, ServiceOrder order, int quantity) {
        BigDecimal cost = decimal(call(provider, "getWuxinSdxyPrice", Map.of()).path("price"));
        if (cost.compareTo(order.getUnitCharge()) > 0) throw bad("上游价格变化，请联系管理员更新商品");
        if (quantity < 1 || quantity > 365) throw bad("增次数量不合法");
    }

    @Override
    public RemoteResult execute(
            ApiProvider provider,
            ServiceProduct p,
            ServiceOrder order,
            String action,
            Map<String, Object> fields) {
        Map<String, Object> params = new LinkedHashMap<>();
        String act;
        if ("CREATE".equals(action)) {
            params.putAll(fields);
            act = "addWuxinSdxyOrder";
        } else {
            params.put("order_number", order.getExternalOrderNo());
            act =
                    switch (action) {
                        case "REFUND" -> "deleteWuxinSdxyOrder";
                        case "ADD_TIMES" -> {
                            params.put("quantity", fields.get("delta"));
                            yield "increaseWuxinSdxyOrder";
                        }
                        case "REASSIGN" -> "reassignWuxinSdxyOrder";
                        case "EDIT_PLAN" -> {
                            params.putAll(fields);
                            yield "editWuxinSdxyOrder";
                        }
                        default -> throw bad("无心不支持此操作");
                    };
        }
        JsonNode data = call(provider, act, params);
        if ("CREATE".equals(action))
            return new RemoteResult(id(data.path("order_number")), "ACTIVE", 0, null);
        return new RemoteResult(
                order.getExternalOrderNo(),
                "REFUND".equals(action) ? "REFUNDED" : "ACTIVE",
                null,
                "REFUND".equals(action) ? count(data.path("refund_count")) : null);
    }

    @Override
    public RemoteResult sync(ApiProvider provider, ServiceOrder order) {
        // Individual order configuration is documented and carries an exact order_number.
        JsonNode row = configuration(provider, order).path("order");
        String status =
                switch (count(row.path("order_status"))) {
                    case 0, 1, 2 -> "ACTIVE";
                    case 3 -> "COMPLETED";
                    case 4 -> "REFUND_REVIEW";
                    default -> "ATTENTION";
                };
        return new RemoteResult(
                order.getExternalOrderNo(), status, count(row.path("completed_quantity")), null);
    }

    @Override
    public RunLogPage logs(ApiProvider provider, ServiceOrder order, int page) {
        if (page < 1 || page > 1000) throw bad("日志分页超出范围");
        JsonNode list =
                rows(
                        call(
                                provider,
                                "getWuxinSdxyOrderRecords",
                                Map.of(
                                        "order_number",
                                        order.getExternalOrderNo(),
                                        "page",
                                        page,
                                        "limit",
                                        20)),
                        20);
        List<RunLog> result = new ArrayList<>();
        for (JsonNode row : list) {
            String time = text(row.path("scheduled_time"), 40);
            if (!time.matches("[0-9TtZz:+. -]{10,40}")) throw invalid();
            String status =
                    switch (count(row.path("status"))) {
                        case 0 -> "等待执行";
                        case 1 -> "执行中";
                        case 2 -> "已完成";
                        case 3 -> "执行失败";
                        default -> "需要处理";
                    };
            result.add(new RunLog(id(row.path("id")), time, status));
        }
        return new RunLogPage(result, page, result.size() == 20 && page < 1000);
    }

    private JsonNode configuration(ApiProvider provider, ServiceOrder order) {
        JsonNode data =
                call(
                        provider,
                        "getWuxinSdxyOrderConfig",
                        Map.of("order_number", order.getExternalOrderNo()));
        if (!order.getExternalOrderNo().equals(id(data.path("order").path("order_number"))))
            throw invalid();
        return data;
    }

    private static void putChoice(
            Map<String, String> values, Lookup options, String field, JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return;
        String selected = id(value);
        if (options.choices().stream()
                .anyMatch(c -> field.equals(c.field()) && selected.equals(c.value())))
            values.put(field, selected);
    }

    private static JsonNode embedded(JsonNode value) {
        if (!value.isTextual()) return value;
        try {
            JsonNode result = JSON.readTree(text(value, 500));
            if (result == null) throw invalid();
            return result;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private JsonNode call(ApiProvider provider, String action, Map<String, Object> body) {
        if (provider == null
                || !getProviderType().equals(provider.getProviderType())
                || !ACTIONS.containsKey(action)) throw bad("不支持的无心协议请求");
        if (provider.getUsername() == null
                || provider.getUsername().isBlank()
                || provider.getApiKey() == null
                || provider.getApiKey().isBlank())
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        // This supplier requires query credentials; they originate only from encrypted saved
        // configuration.
        // ApiHttpClient never logs query strings, request bodies, raw responses or exceptions.
        String url =
                normalizer.normalize(provider.getApiUrl(), getProviderType()).toASCIIString()
                        + "/wuxin/api.php?act="
                        + action
                        + "&u_uid="
                        + URLEncoder.encode(provider.getUsername(), StandardCharsets.UTF_8)
                        + "&key="
                        + URLEncoder.encode(provider.getApiKey(), StandardCharsets.UTF_8);
        String bodyText = http.postForString(provider, url, body);
        if (bodyText == null || bodyText.length() > 262144) throw invalid();
        try {
            JsonNode root = JSON.readTree(bodyText);
            if (root == null
                    || !root.isObject()
                    || !root.path("code").asText().matches("-?[0-9]{1,4}")) throw invalid();
            if (root.path("code").asInt() != ACTIONS.get(action))
                throw new ProviderRequestException(
                        ProviderRequestException.Reason.UPSTREAM_REJECTED);
            return root.path("data");
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static JsonNode rows(JsonNode data, int max) {
        JsonNode rows = data.path("list");
        if (!rows.isArray() || rows.size() > max) throw invalid();
        return rows;
    }

    private static void fields(Map<String, String> values) {
        if (values == null || values.size() > 20 || !INPUTS.containsAll(values.keySet()))
            throw bad("无心表单含不支持字段");
        values.forEach(
                (k, v) -> {
                    if (v == null
                            || v.length() > 2048
                            || v.codePoints().anyMatch(Character::isISOControl))
                        throw bad("表单字段格式错误");
                });
    }

    private static String required(Map<String, String> values, String key, int max) {
        String value = optional(values, key, max);
        if (value.isBlank()) throw bad("缺少字段：" + key);
        return value;
    }

    private static String optional(Map<String, String> values, String key, int max) {
        String value = values.getOrDefault(key, "").trim();
        if (value.length() > max) throw bad("字段过长：" + key);
        return value;
    }

    private static String selected(Lookup options, Map<String, String> values, String field) {
        String value = required(values, field, 100);
        if (options.choices().stream()
                .noneMatch(c -> field.equals(c.field()) && value.equals(c.value())))
            throw bad("计划或区域已变化，请重新查询");
        return value;
    }

    private static String clock(String value) {
        if (!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw bad("时间格式须为 HH:mm");
        return value;
    }

    private static String text(JsonNode v, int max) {
        if (!(v.isTextual() || v.isIntegralNumber())
                || v.asText().isBlank()
                || v.asText().length() > max
                || v.asText().codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return v.asText();
    }

    private static String id(JsonNode v) {
        String value = text(v, 64);
        if (!value.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return value;
    }

    private static int count(JsonNode v) {
        String value = text(v, 5);
        if (!value.matches("[0-9]{1,4}")) throw invalid();
        return Integer.parseInt(value);
    }

    private static BigDecimal decimal(JsonNode v) {
        String value = v.asText();
        if (!value.matches("[0-9]{1,8}(?:\\.[0-9]{1,6})?")) throw invalid();
        return new BigDecimal(value);
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }

    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
