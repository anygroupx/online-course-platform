package com.course.platform.infra.servicecommerce;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.application.service.servicecommerce.NativeServiceGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.vo.plugin.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Fixed AppUI form protocol. Day balances are quotas, not attendance evidence or calendar dates. */
@Component
@RequiredArgsConstructor
public class AppuiNativeServiceGateway implements NativeServiceGateway, PluginReadOnlyConnector {
    public static final String TYPE = "appui";
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;
    private static final Set<String> ACTIONS = Set.of("getCourse", "getSchoolList", "query", "add",
            "orders", "detail", "edit", "renew", "refund");
    private static final Set<String> SCHOOL_PROJECTS = Set.of("3", "6", "8");
    private static final Set<String> INPUTS = Set.of("account", "password", "schoolName", "address",
            "startTime", "endTime", "weekdays", "reports");
    private static final Set<String> EDIT_INPUTS = Set.of("password", "address", "startTime",
            "endTime", "weekdays", "reports");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20)
                    .maxNumberLength(32).maxStringLength(8192).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    public static boolean supported(String project, String product) {
        return project != null && project.matches("[1-9]") && project.equals(product);
    }

    @Override
    public String getProviderType() { return TYPE; }

    @Override
    public List<PluginProjectOption> projects() {
        return List.of(new PluginProjectOption("1", "校友邦"), new PluginProjectOption("2", "职校家园"),
                new PluginProjectOption("3", "慧职教"), new PluginProjectOption("4", "黔职通"),
                new PluginProjectOption("5", "学习通"), new PluginProjectOption("6", "习行学生版"),
                new PluginProjectOption("7", "工学云"), new PluginProjectOption("8", "习讯云"),
                new PluginProjectOption("9", "广西职业院校公众号"));
    }

    @Override
    public void testConnection(ApiProvider provider) { fetchCatalog(provider, null); }

    @Override
    public List<PluginProduct> fetchCatalog(ApiProvider provider, String project) {
        if (project != null && !project.isBlank() && !supported(project, project))
            throw bad("不支持的实习项目");
        JsonNode rows = array(call(provider, "getCourse", Map.of()).path("data"), 9);
        if (rows.isEmpty()) throw invalid();
        List<PluginProduct> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String pid = text(row.path("pid"), 1);
            if (!supported(pid, pid) || !seen.add(pid)
                    || count(row.path("yes_school")) != (SCHOOL_PROJECTS.contains(pid) ? 1 : 0))
                throw invalid();
            PluginProduct product = new PluginProduct(pid, text(row.path("name"), 100),
                    decimal(row.path("price")), "元/天");
            if (project == null || project.isBlank() || project.equals(pid)) result.add(product);
        }
        return List.copyOf(result);
    }

    @Override
    public PluginSchoolPage schools(ApiProvider provider, ServiceProduct product, int page, String keyword) {
        String pid = project(product);
        if (page < 1 || page > 150 || keyword != null && (keyword.length() > 80 || controls(keyword)))
            throw bad("学校查询参数不正确");
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<String> schools = schoolNames(provider, pid).stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).contains(search)).toList();
        int start = Math.min((page - 1) * 20, schools.size()), end = Math.min(start + 20, schools.size());
        return new PluginSchoolPage(schools.subList(start, end).stream()
                .map(s -> new PluginSchool(s, s)).toList(), page, 20, end < schools.size());
    }

    private List<String> schoolNames(ApiProvider provider, String pid) {
        if (!SCHOOL_PROJECTS.contains(pid)) throw bad("该项目无需选择学校");
        JsonNode rows = array(call(provider, "getSchoolList", Map.of("pid", pid)).path("data"), 3000);
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode row : rows) {
            if (!row.isTextual()) throw invalid();
            result.add(text(row, 100));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> accountFields(ApiProvider provider, ServiceProduct product,
                                              Map<String, String> fields) {
        validateFields(fields, INPUTS);
        String pid = project(product), school = value(fields, "schoolName", 100, false, true);
        if (SCHOOL_PROJECTS.contains(pid)) {
            if (school.isBlank() || !schoolNames(provider, pid).contains(school))
                throw bad("请选择有效学校后重新查询");
        } else if (!school.isEmpty()) throw bad("该项目无需提交学校");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("pid", pid);
        form.put("school", school);
        form.put("user", value(fields, "account", 100, true, true));
        form.put("pass", value(fields, "password", 128, true, false));
        return form;
    }

    @Override
    public Lookup lookup(ApiProvider provider, ServiceProduct product, Map<String, String> fields) {
        JsonNode root = call(provider, "query", nested(accountFields(provider, product, fields)));
        return new Lookup(Map.of("studentName", text(root.path("userName"), 100),
                "address", optionalText(root.path("address"), 500)), List.of(),
                "账号已查询，请核对姓名和地址，再选择服务天数与执行安排。");
    }

    @Override
    public PreparedOrder prepare(ApiProvider provider, ServiceProduct product, OrderForm order) {
        if (order == null || !order.authorizedAccount()) throw bad("请确认授权使用该账号");
        if (order.quantity() < 1 || order.quantity() > 365 || order.distance() != null
                || order.schedule() != null || order.accountSessionId() != null
                || order.taskTimes() != null && !order.taskTimes().isEmpty())
            throw bad("请选择 1–365 天，不接受距离或日历计划");
        Map<String, Object> form = accountFields(provider, product, order.fields());
        Map<String, Object> plan = planFields(order.fields());
        // The displayed name is never trusted as authority for the purchase.
        JsonNode checked = call(provider, "query", nested(form));
        form.put("userName", text(checked.path("userName"), 100));
        form.putAll(plan);
        form.put("days1", order.quantity());
        String account = (String) form.get("user");
        String label = account.length() < 5 ? "已授权账号" : account.substring(0, 2) + "***"
                + account.substring(account.length() - 2);
        return new PreparedOrder(nested(form), order.quantity(), null, BigDecimal.ONE, label,
                null, null, ServiceAccountFingerprint.create(account));
    }

    private Map<String, Object> planFields(Map<String, String> fields) {
        String start = clock(value(fields, "startTime", 5, true, true));
        String end = clock(value(fields, "endTime", 5, true, true));
        if (start.compareTo(end) >= 0) throw bad("下班时间须晚于上班时间");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("address", value(fields, "address", 500, true, true));
        form.put("shangban_time", start);
        form.put("xiaban_time", end);
        form.put("week", selection(value(fields, "weekdays", 13, true, true), 7, false));
        form.put("report", selection(value(fields, "reports", 5, true, true), 3, false));
        return form;
    }

    @Override
    public Lookup orderOptions(ApiProvider provider, ServiceOrder order) {
        JsonNode row = exactOrder(provider, order);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("address", text(row.path("address"), 500));
        values.put("startTime", clock(text(row.path("shangban_time"), 5)));
        values.put("endTime", clock(text(row.path("xiaban_time"), 5)));
        values.put("weekdays", remoteSelection(row.path("week"), 7, false));
        values.put("reports", remoteSelection(row.path("report"), 3, true));
        return new Lookup(values, List.of(), "已读取当前安排；密码留空将保持不变，保存不会增加服务天数。");
    }

    @Override
    public Map<String, Object> prepareAction(ApiProvider provider, ServiceOrder order,
                                              String action, Map<String, String> fields) {
        if (!"EDIT_PLAN".equals(action)) {
            if (fields != null && !fields.isEmpty()) throw bad("此操作不接受额外参数");
            return Map.of();
        }
        validateFields(fields, EDIT_INPUTS);
        Map<String, Object> form = planFields(fields);
        JsonNode remote = exactOrder(provider, order);
        requireMutable(remote);
        String password = value(fields, "password", 128, false, false);
        // Missing input means retain, never overwrite the remote password with an empty value.
        form.put("pass", password.isEmpty() ? text(remote.path("pass"), 128) : password);
        form.put("pid", order.getProject());
        return nested(form);
    }

    @Override
    public int refundRemaining(ApiProvider provider, ServiceOrder order) {
        JsonNode row = exactOrder(provider, order);
        requireMutable(row);
        return count(row.path("residue_day"));
    }

    @Override
    public void checkAddTimes(ApiProvider provider, ServiceOrder order, int quantity) {
        if (quantity < 1 || quantity > 365 || order.getQuantity() + quantity > 9999)
            throw bad("新增天数须为 1–365 天，累计不超过 9999 天");
        requireMutable(exactOrder(provider, order));
        BigDecimal cost = fetchCatalog(provider, order.getProject()).stream()
                .filter(p -> order.getRemoteProductId().equals(p.id())).map(PluginProduct::unitPrice)
                .findFirst().orElseThrow(AppuiNativeServiceGateway::invalid);
        if (cost.compareTo(order.getUnitCharge()) > 0) throw bad("服务价格已变化，请联系管理员后再续期");
    }

    @Override
    public RemoteResult execute(ApiProvider provider, ServiceProduct product, ServiceOrder order,
                                String action, Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        String act;
        Integer remainingAtDispatch = null;
        if ("CREATE".equals(action)) {
            project(product);
            body.putAll(fields);
            act = "add";
        } else {
            if (!Set.of("EDIT_PLAN", "ADD_TIMES", "REFUND").contains(action))
                throw bad("该服务不支持此操作");
            // A fresh identity read before every business write; still never replay a write.
            JsonNode current = exactOrder(provider, order);
            requireMutable(current);
            if ("REFUND".equals(action)) remainingAtDispatch = count(current.path("residue_day"));
            body.put("id", order.getExternalOrderNo());
            act = switch (action) {
                case "EDIT_PLAN" -> { body.putAll(fields); yield "edit"; }
                case "ADD_TIMES" -> { body.put("days1", fields.get("delta")); yield "renew"; }
                default -> "refund";
            };
        }
        JsonNode root = call(provider, act, body);
        if ("CREATE".equals(action)) return new RemoteResult(id(root.path("oid")), "ACTIVE", 0, null);
        Integer refund = "REFUND".equals(action) ? count(root.path("days2")) : null;
        if (refund != null && (remainingAtDispatch == null || refund > remainingAtDispatch
                || refund > order.getQuantity() - order.getCompleted())) throw invalid();
        return new RemoteResult(order.getExternalOrderNo(), refund == null ? "ACTIVE" : "REFUNDED", null, refund);
    }

    @Override
    public RemoteResult sync(ApiProvider provider, ServiceOrder order) {
        JsonNode row = exactOrder(provider, order);
        String status = switch (text(row.path("status"), 20)) {
            case "待处理", "进行中" -> "ACTIVE";
            case "已完成" -> "COMPLETED";
            case "已退款" -> "REFUND_REVIEW";
            default -> "ATTENTION";
        };
        int used = "REFUND_REVIEW".equals(status) ? order.getCompleted()
                : count(row.path("total_day")) - count(row.path("residue_day"));
        return new RemoteResult(order.getExternalOrderNo(), status, used, null);
    }

    @Override
    public RunLogPage logs(ApiProvider provider, ServiceOrder order, int page) {
        if (page < 1 || page > 100) throw bad("日志分页超出范围");
        exactOrder(provider, order);
        JsonNode rows = array(call(provider, "detail", Map.of("id", order.getExternalOrderNo())).path("data"), 2000);
        Set<String> ids = new HashSet<>();
        List<RunLog> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = id(row.path("id"));
            if (!ids.add(id)) throw invalid();
            String time = text(row.path("addtime"), 19);
            try { LocalDateTime.parse(time, TIME); } catch (RuntimeException ex) { throw invalid(); }
            // Free-text qd_msg/qt_msg and credentials are deliberately never projected.
            String status = "签到：" + logStatus(row.path("qd_status")) + " · 签退：" + logStatus(row.path("qt_status"));
            result.add(new RunLog(id, time, status));
        }
        int start = Math.min((page - 1) * 20, result.size()), end = Math.min(start + 20, result.size());
        return new RunLogPage(List.copyOf(result.subList(start, end)), page, end < result.size());
    }

    private static String logStatus(JsonNode value) {
        return switch (value.asText("")) {
            case "已签" -> "已签";
            case "不签到" -> "无需执行";
            case "待签" -> "待执行";
            case "异常" -> "异常";
            default -> "待核对";
        };
    }

    private JsonNode exactOrder(ApiProvider provider, ServiceOrder order) {
        if (order == null || !TYPE.equals(order.getProviderType())
                || !supported(order.getProject(), order.getRemoteProductId())
                || order.getExternalOrderNo() == null || !order.getExternalOrderNo().matches("[1-9][0-9]{0,18}"))
            throw bad("订单信息不完整，请联系管理员核对");
        JsonNode root = call(provider, "orders", Map.of("page", 1, "limit", 20, "type", 1,
                "keywords", order.getExternalOrderNo()));
        JsonNode rows = array(root.path("data"), 20), match = null;
        for (JsonNode row : rows) {
            if (!order.getExternalOrderNo().equals(id(row.path("id")))) continue;
            if (match != null) throw invalid();
            match = row;
        }
        if (match == null || !order.getProject().equals(text(match.path("pid"), 1))) throw invalid();
        try {
            if (order.getScheduleJson() == null || order.getScheduleJson().length() > 500
                    || !JSON.readValue(order.getScheduleJson(), ServiceAccountFingerprint.class)
                    .matches(text(match.path("user"), 100))) throw invalid();
        } catch (Exception ex) { throw invalid(); }
        int total = count(match.path("total_day")), remaining = count(match.path("residue_day"));
        if (total != order.getQuantity() || remaining > total
                || (!"已退款".equals(text(match.path("status"), 20))
                    && total - remaining < order.getCompleted())) throw invalid();
        return match;
    }

    private static void requireMutable(JsonNode row) {
        if (!Set.of("待处理", "进行中", "已完成").contains(text(row.path("status"), 20)))
            throw bad("订单状态需要核对，暂不能修改或退款");
    }

    private JsonNode call(ApiProvider provider, String action, Map<String, Object> values) {
        if (provider == null || !TYPE.equals(provider.getProviderType()) || !ACTIONS.contains(action))
            throw bad("不支持的实习服务请求");
        if (provider.getUsername() == null || provider.getUsername().isBlank()
                || provider.getApiKey() == null || provider.getApiKey().isBlank())
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        Map<String, Object> body = new LinkedHashMap<>(values);
        body.put("login_uid", provider.getUsername());
        body.put("login_key", provider.getApiKey());
        String url = normalizer.normalize(provider.getApiUrl(), TYPE).toASCIIString()
                + "/appui/api.php?act=" + action;
        if ("getSchoolList".equals(action)) {
            String pid = String.valueOf(values.get("pid"));
            if (!SCHOOL_PROJECTS.contains(pid)) throw bad("无需查询学校");
            url += "&pid=" + pid;
        }
        String raw = http.postForString(provider, url, body);
        if (raw == null || raw.length() > 262144 || raw.getBytes(StandardCharsets.UTF_8).length > 262144)
            throw invalid();
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isObject() || !(root.path("code").isIntegralNumber()
                    || root.path("code").isTextual()) || !root.path("code").asText().matches("-?[0-9]{1,4}"))
                throw invalid();
            if (!"1".equals(root.path("code").asText()))
                throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
            return root;
        } catch (ProviderRequestException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private static Map<String, Object> nested(Map<String, Object> form) {
        Map<String, Object> body = new LinkedHashMap<>();
        form.forEach((key, value) -> {
            if (value instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) body.put("form[" + key + "][" + i + "]", list.get(i));
            } else body.put("form[" + key + "]", value);
        });
        return body;
    }

    private static List<Integer> selection(String raw, int max, boolean empty) {
        if (raw.isEmpty() && empty) return List.of();
        if (!raw.matches("[1-" + max + "](?:,[1-" + max + "]){0," + (max - 1) + "}"))
            throw bad("请选择有效的星期和报告类型");
        List<Integer> values = Arrays.stream(raw.split(",")).map(Integer::parseInt).toList();
        if (new HashSet<>(values).size() != values.size()) throw bad("星期和报告类型不能重复");
        return values;
    }

    private static String remoteSelection(JsonNode node, int max, boolean empty) {
        if (!node.isArray() || node.size() > max || (!empty && node.isEmpty())) throw invalid();
        List<String> values = new ArrayList<>();
        for (JsonNode v : node) values.add(text(v, 1));
        String result = String.join(",", values);
        try { selection(result, max, empty); } catch (BusinessException ex) { throw invalid(); }
        return result;
    }

    private static String project(ServiceProduct product) {
        if (product == null || !TYPE.equals(product.getProviderType())
                || !supported(product.getProject(), product.getRemoteProductId())) throw bad("不支持的实习商品");
        return product.getProject();
    }

    private static void validateFields(Map<String, String> fields, Set<String> allowed) {
        if (fields == null || !allowed.containsAll(fields.keySet())) throw bad("表单包含不支持的字段");
        fields.forEach((k, v) -> { if (v == null || v.length() > 2048 || controls(v)) throw bad("字段格式不正确"); });
    }

    private static String value(Map<String, String> fields, String key, int max, boolean required, boolean trim) {
        String value = fields.getOrDefault(key, "");
        if (trim) value = value.trim();
        if (value.length() > max || required && value.isBlank()) throw bad("请填写完整的账号信息与执行安排");
        return value;
    }

    private static String clock(String value) {
        if (!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw bad("时间格式须为 HH:mm");
        return value;
    }

    private static JsonNode array(JsonNode value, int max) {
        if (!value.isArray() || value.size() > max) throw invalid();
        return value;
    }

    private static boolean controls(String s) { return s.codePoints().anyMatch(Character::isISOControl); }

    private static String text(JsonNode value, int max) {
        String raw = optionalText(value, max);
        if (raw.isBlank()) throw invalid();
        return raw;
    }

    private static String optionalText(JsonNode value, int max) {
        if (!(value.isTextual() || value.isIntegralNumber()) || value.asText().length() > max
                || controls(value.asText())) throw invalid();
        return value.asText();
    }

    private static String id(JsonNode value) {
        String id = text(value, 19);
        if (!id.matches("[1-9][0-9]{0,18}")) throw invalid();
        return id;
    }

    private static int count(JsonNode value) {
        String raw = text(value, 4);
        if (!raw.matches("0|[1-9][0-9]{0,3}")) throw invalid();
        return Integer.parseInt(raw);
    }

    private static BigDecimal decimal(JsonNode value) {
        if (!(value.isNumber() || value.isTextual())) throw invalid();
        String raw = value.asText();
        if (!raw.matches("[0-9]{1,8}(?:\\.[0-9]{1,6})?")) throw invalid();
        return new BigDecimal(raw);
    }

    private static BusinessException bad(String message) { return new BusinessException(message); }
    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
