package com.course.platform.infra.servicecommerce;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.servicecommerce.NativeServiceGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.vo.plugin.PluginProduct;
import com.course.platform.domain.vo.plugin.PluginProjectOption;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** P05's plaintext xbd/ydapi perimeter only. No executable PHP, SQL, account-wide mutations or retries. */
@Component
@RequiredArgsConstructor
public class SsbenzDistanceGateway implements NativeServiceGateway, PluginReadOnlyConnector {
    public static final String TYPE = "ssbenz_xbd";
    private static final Set<String> INPUTS =
            Set.of("account", "password", "schoolName", "startTime", "endTime", "weekdays");
    private static final Set<String> ACTIONS = Set.of("school", "add", "order");
    private static final int MAX_ROWS = 1000;
    private static final ObjectMapper JSON =
            new ObjectMapper(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(16).maxNumberLength(32).maxStringLength(4096).build())
                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;

    public static boolean supported(String project, String productId) {
        return "xbd".equals(project) && Set.of("0", "1").contains(productId == null ? "" : productId);
    }

    @Override
    public String getProviderType() { return TYPE; }

    @Override
    public List<PluginProjectOption> projects() {
        return List.of(new PluginProjectOption("xbd", "总公里计划"));
    }

    @Override
    public void testConnection(ApiProvider provider) {
        fetchCatalog(provider, "xbd");
    }

    @Override
    public List<PluginProduct> fetchCatalog(ApiProvider provider, String project) {
        if (project != null && !project.isBlank() && !"xbd".equals(project))
            throw bad("该接口仅支持总公里计划");
        JsonNode data = call(provider, "school", Map.of());
        // Deliberately ignore dj (SQL), gonggao (HTML), and every undocumented field.
        return List.of(
                new PluginProduct("0", "总公里计划 · 方案 0", price(data.path("xbdpr")), "元/公里"),
                new PluginProduct("1", "总公里计划 · 方案 1", price(data.path("xbdprs")), "元/公里"));
    }

    @Override
    public PreparedOrder prepare(ApiProvider provider, ServiceProduct product, OrderForm form) {
        binding(provider, product);
        if (form == null || !form.authorizedAccount() || form.quantity() != 1
                || form.schedule() != null || form.accountSessionId() != null
                || (form.taskTimes() != null && !form.taskTimes().isEmpty()))
            throw bad("总公里计划每次提交一单，不接受次数、任务列表或其他授权会话");
        Map<String, String> values = form.fields();
        if (values == null || values.size() > INPUTS.size()
                || values.keySet().stream().anyMatch(key -> key == null || !INPUTS.contains(key)))
            throw bad("总公里表单含不支持字段");
        values.forEach((key, value) -> {
            if (value == null || value.length() > 2048
                    || value.codePoints().anyMatch(Character::isISOControl)) throw bad("计划字段格式错误");
        });
        String account = required(values, "account", 100, true);
        String password = required(values, "password", 200, false);
        String school = values.getOrDefault("schoolName", "").trim();
        if (school.isEmpty()) school = "自动识别";
        String weeks = required(values, "weekdays", 13, false);
        if (!weeks.matches("[1-7](?:,[1-7]){0,6}")) throw bad("请选择每周执行星期");
        BigDecimal distance = TotalDistancePlan.distance(form.distance());
        TotalDistancePlan plan = new TotalDistancePlan(product.getRemoteProductId(), distance.toPlainString(),
                school, required(values, "startTime", 5, false), required(values, "endTime", 5, false),
                Arrays.stream(weeks.split(",")).map(Integer::valueOf).toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", Integer.parseInt(plan.typeCode()));
        body.put("school", plan.schoolName());
        body.put("user", account);
        body.put("pass", password);
        body.put("zkm", plan.totalDistance());
        body.put("ks_h", Integer.parseInt(plan.startTime().substring(0, 2)));
        body.put("ks_m", Integer.parseInt(plan.startTime().substring(3)));
        body.put("js_h", Integer.parseInt(plan.endTime().substring(0, 2)));
        body.put("js_m", Integer.parseInt(plan.endTime().substring(3)));
        body.put("weeks", String.join("", plan.weekdays().stream().map(String::valueOf).toList()));
        String masked = account.length() < 5 ? "***" : account.substring(0, 2) + "***" + account.substring(account.length() - 2);
        return new PreparedOrder(Map.copyOf(body), 1, distance, distance, masked, null, plan);
    }

    @Override
    public RemoteResult execute(ApiProvider provider, ServiceProduct product, ServiceOrder order,
                                String action, Map<String, Object> fields) {
        binding(provider, product);
        if (!"CREATE".equals(action)) throw bad("总公里计划暂不支持此操作");
        if (order == null || !TYPE.equals(order.getProviderType()) || !"xbd".equals(order.getProject())
                || !product.getRemoteProductId().equals(order.getRemoteProductId())) throw bad("订单商品不一致");
        Set<String> expected = Set.of("type", "school", "user", "pass", "zkm", "ks_h", "ks_m", "js_h", "js_m", "weeks");
        if (fields == null || !fields.keySet().equals(expected)
                || !product.getRemoteProductId().equals(String.valueOf(fields.get("type"))))
            throw bad("下单快照不完整");
        // Revalidate the durable payload before dispatch; the encrypted snapshot cannot smuggle
        // extra actions/credentials or change the quantity that was priced and debited.
        try {
            String weeks = wireText(fields, "weeks");
            if (!weeks.matches("[1-7]{1,7}")) throw bad("星期快照无效");
            Map<String, String> original = Map.of(
                    "account", wireText(fields, "user"), "password", wireText(fields, "pass"),
                    "schoolName", wireText(fields, "school"),
                    "startTime", wireClock(fields, "ks_h", "ks_m"),
                    "endTime", wireClock(fields, "js_h", "js_m"),
                    "weekdays", String.join(",", weeks.split("")));
            PreparedOrder checked = prepare(provider, product,
                    new OrderForm(1, new BigDecimal(wireText(fields, "zkm")), original, List.of(), true));
            if (!fields.equals(checked.fields()) || !Integer.valueOf(1).equals(order.getQuantity())
                    || !Objects.equals(order.getAccountLabel(), checked.accountLabel())
                    || order.getScheduleJson() == null
                    || !checked.distancePlan().equals(JSON.readValue(order.getScheduleJson(), TotalDistancePlan.class))
                    || order.getDistance() == null || order.getDistance().compareTo(checked.distance()) != 0)
                throw bad("下单快照与已确认的总公里数不一致");
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw bad("下单快照格式无效"); }
        JsonNode data = call(provider, "add", fields);
        // The wrapper can return code=1 even if its inner submission failed. This is only a receipt.
        return new RemoteResult(id(data.path("id")), "SUBMITTED", null, null);
    }

    @Override
    public RemoteResult sync(ApiProvider provider, ServiceOrder order) {
        if (order == null || !TYPE.equals(order.getProviderType())
                || !supported(order.getProject(), order.getRemoteProductId())
                || !validId(order.getExternalOrderNo())) throw bad("缺少可核对的提交编号");
        // The archive's optional-id branch dereferences a query result as a row. Do not use it,
        // guess pagination, fall back to student/account aggregates, or pick the first result.
        JsonNode root = call(provider, "order", Map.of());
        JsonNode rows = root.path("data");
        if (!rows.isArray() || rows.size() > MAX_ROWS) throw invalid();
        if (root.has("id") && !order.getExternalOrderNo().equals(id(root.path("id")))) throw invalid();
        Set<String> seen = new HashSet<>();
        String status = null;
        for (JsonNode row : rows) {
            if (!row.isObject()) throw invalid();
            String rowId = id(row.path("id"));
            if (!seen.add(rowId)) throw invalid();
            if (!order.getExternalOrderNo().equals(rowId)) continue;
            String code = scalar(row.path("status"));
            if (!Set.of("0", "1", "2").contains(code)) throw invalid();
            status = "1".equals(code) ? "SUBMITTED" : "SUBMISSION_REVIEW";
        }
        // Absence in a bounded, unpaginated response proves neither rejection nor a refund.
        if (status == null) throw invalid();
        return new RemoteResult(order.getExternalOrderNo(), status, null, null);
    }

    @Override
    public Lookup lookup(ApiProvider p, ServiceProduct product, Map<String, String> fields) {
        throw bad("总公里计划暂不提供账号预检，请核对填写的账号信息");
    }
    @Override
    public int refundRemaining(ApiProvider p, ServiceOrder order) { throw bad("总公里计划暂不支持自动退款"); }
    @Override
    public void checkAddTimes(ApiProvider p, ServiceOrder order, int quantity) { throw bad("总公里计划不按次数追加"); }
    @Override
    public RunLogPage logs(ApiProvider p, ServiceOrder order, int page) { throw bad("总公里计划暂无单笔执行记录"); }

    private JsonNode call(ApiProvider provider, String action, Map<String, Object> fields) {
        if (provider == null || !TYPE.equals(provider.getProviderType()) || !ACTIONS.contains(action))
            throw bad("不支持的总公里请求");
        String key = provider.getApiKey();
        if (key == null || key.isBlank() || "0".equals(key) || key.length() > 2048
                || key.codePoints().anyMatch(Character::isISOControl))
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        String url = normalizer.normalize(provider.getApiUrl(), TYPE).toASCIIString() + "/" + action;
        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put("token", key); // Only the saved encrypted API key; never a form-provided token or URL query.
        String response = http.postForString(provider, url, body);
        if (response == null || response.length() > 262144) throw invalid();
        try {
            JsonNode root = JSON.readTree(response);
            if (root == null || !root.isObject()) throw invalid();
            String code = scalar(root.path("code"));
            if (!code.matches("-?[0-9]{1,4}")) throw invalid();
            if (!"1".equals(code))
                throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
            return root;
        } catch (ProviderRequestException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private static void binding(ApiProvider p, ServiceProduct product) {
        if (p == null || !TYPE.equals(p.getProviderType()) || product == null
                || !TYPE.equals(product.getProviderType())
                || !supported(product.getProject(), product.getRemoteProductId()))
            throw bad("不支持的总公里商品");
    }

    private static String wireText(Map<String, Object> fields, String key) {
        if (!(fields.get(key) instanceof String value)) throw bad("下单快照字段无效");
        return value;
    }

    private static String wireClock(Map<String, Object> fields, String hour, String minute) {
        if (!(fields.get(hour) instanceof Integer h) || !(fields.get(minute) instanceof Integer m))
            throw bad("时段快照字段无效");
        return String.format(Locale.ROOT, "%02d:%02d", h, m);
    }

    private static String required(Map<String, String> fields, String key, int max, boolean trim) {
        String value = fields.getOrDefault(key, "");
        if (trim) value = value.trim();
        if (value.isBlank() || value.length() > max) throw bad("计划字段缺失或过长：" + key);
        return value;
    }

    private static BigDecimal price(JsonNode node) {
        // USE_BIG_DECIMAL_FOR_FLOATS prevents binary floating-point rounding at JSON parsing time.
        if (!(node.isTextual() || node.isNumber())) throw invalid();
        String value = node.asText();
        if (!value.matches("[0-9]{1,4}(?:\\.[0-9]{1,6})?")) throw invalid();
        BigDecimal result = new BigDecimal(value);
        if (result.compareTo(new BigDecimal("9999")) > 0) throw invalid();
        return result;
    }

    private static String scalar(JsonNode node) {
        if (!(node.isTextual() || node.isIntegralNumber())) throw invalid();
        return node.asText();
    }

    private static String id(JsonNode node) {
        String value = scalar(node);
        if (!validId(value)) throw invalid();
        return value;
    }

    public static boolean validId(String value) {
        return value != null && value.matches("[1-9][0-9]{0,18}");
    }

    private static BusinessException bad(String message) { return new BusinessException(message); }
    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
