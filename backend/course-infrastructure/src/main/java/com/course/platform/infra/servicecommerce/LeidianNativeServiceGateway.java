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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Fixed LeiDian form protocol. Internal row IDs and business receipt IDs are NOT interchangeable. */
@Component
@RequiredArgsConstructor
public class LeidianNativeServiceGateway implements NativeServiceGateway, PluginReadOnlyConnector {
    public static final String TYPE = "leidian";
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;
    private static final Set<String> ACTIONS = Set.of("get_price", "get_rule", "orders", "add_order",
            "get_log", "get_residue_num", "edit_run_time", "batch_edit_time", "cancel_order", "get_auth_link");
    private static final Set<String> INPUTS = Set.of("account", "zoneId", "startDate", "startTime", "endTime", "weekdays");
    private static final Set<String> PLAN_INPUTS = Set.of("startDate", "startTime", "endTime", "weekdays");
    private static final int PAGE_SIZE = 100, MAX_PAGES = 5;
    private static final BigDecimal HALF_CENT = new BigDecimal("0.005");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20)
                    .maxNumberLength(32).maxStringLength(8192).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    public static boolean supported(String project, String product) {
        return project != null && project.matches("[1-4]") && project.equals(product);
    }

    @Override
    public String getProviderType() { return TYPE; }

    @Override
    public List<PluginProjectOption> projects() {
        return List.of(new PluginProjectOption("1", "步道乐跑"), new PluginProjectOption("2", "步道人脸跑"),
                new PluginProjectOption("3", "步道自由跑"), new PluginProjectOption("4", "乐健体育"));
    }

    @Override
    public void testConnection(ApiProvider provider) { fetchCatalog(provider, null); }

    @Override
    public List<PluginProduct> fetchCatalog(ApiProvider provider, String project) {
        if (project != null && !project.isBlank() && !supported(project, project)) throw bad("不支持的运动项目");
        List<PluginProduct> products = new ArrayList<>();
        for (PluginProjectOption option : projects()) {
            if (project == null || project.isBlank() || project.equals(option.id())) {
                BigDecimal price = price(provider, option.id());
                products.add(new PluginProduct(option.id(), option.name(), conservativeUnitCost(price, option.id()),
                        "元/次·公里（成本上限）"));
            }
        }
        return List.copyOf(products);
    }

    /** get_price has already rounded t to cents. Never treat it as the exact unrounded t. */
    public static BigDecimal costUpperBound(BigDecimal quotedPrice, BigDecimal billableDistance) {
        if (billableDistance.compareTo(BigDecimal.ONE) == 0) return quotedPrice;
        return quotedPrice.add(HALF_CENT).multiply(billableDistance).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal conservativeUnitCost(BigDecimal quotedPrice, String project) {
        BigDecimal rate = quotedPrice;
        int max = "4".equals(project) ? 100 : 20;
        for (int tenths = 10; tenths <= max; tenths++) {
            BigDecimal mile = BigDecimal.valueOf(tenths, 1);
            rate = rate.max(costUpperBound(quotedPrice, mile).divide(mile, 6, RoundingMode.CEILING));
        }
        return rate;
    }

    private BigDecimal price(ApiProvider provider, String project) {
        BigDecimal price = decimal(call(provider, "get_price", Map.of("appId", project)).path("data"), 2);
        if (price.compareTo(new BigDecimal("9999")) > 0) throw invalid();
        return price;
    }

    @Override
    public Lookup lookup(ApiProvider provider, ServiceProduct product, Map<String, String> fields) {
        String project = project(product);
        if ("4".equals(project)) throw bad("此项目无需查询规则，请填写手机号和执行安排");
        validateFields(fields, Set.of("account"));
        Rules rules = rules(provider, account(fields, project));
        return new Lookup(Map.of("schoolName", rules.school()), rules.zones(),
                "请选择跑区与时间段，也可以自定义同日时间段。规则查询不代表成绩或授权已经验证。",
                null, null, null, rules.times());
    }

    private record Rules(String school, List<Choice> zones, List<RunRule> times) {}

    private Rules rules(ApiProvider provider, String account) {
        JsonNode root = call(provider, "get_rule", Map.of("uid", account));
        String school = text(root.path("school"), 100);
        List<Choice> zones = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode zone : array(root.path("run_zones"), 200)) {
            String id = positiveId(zone.path("id"));
            if (!seen.add(id)) throw invalid();
            zones.add(new Choice("zoneId", id, text(zone.path("name"), 100)));
        }
        if (zones.isEmpty()) throw invalid();
        List<RunRule> times = new ArrayList<>();
        for (JsonNode rule : array(root.path("rule"), 64)) {
            BigDecimal mile = decimal(rule.path("mile"), 1);
            if (mile.signum() <= 0 || mile.compareTo(BigDecimal.TEN) > 0) throw invalid();
            String start = remoteClock(rule.path("start_time")), end = remoteClock(rule.path("end_time"));
            if (start.compareTo(end) >= 0) throw invalid();
            RunRule value = new RunRule(mile.toPlainString(), start, end);
            if (times.contains(value)) throw invalid();
            times.add(value);
        }
        if (times.isEmpty()) throw invalid();
        return new Rules(school, List.copyOf(zones), List.copyOf(times));
    }

    @Override
    public PreparedOrder prepare(ApiProvider provider, ServiceProduct product, OrderForm order) {
        String project = project(product);
        if (order == null || !order.authorizedAccount()) throw bad("请确认授权使用该账号");
        if (order.quantity() < 1 || order.quantity() > 100 || order.schedule() != null
                || order.accountSessionId() != null || order.taskTimes() != null && !order.taskTimes().isEmpty())
            throw bad("请选择 1–100 次，不接受其它账号会话或任务列表");
        BigDecimal mile = distance(order.distance());
        validateFields(order.fields(), INPUTS);
        String account = account(order.fields(), project);
        Map<String, Object> form = plan(order.fields(), true);
        form.put("appId", project);
        form.put("uid", account);
        form.put("days", order.quantity());
        form.put("mile", mile.toPlainString());
        applyZone(provider, form, order.fields(), project, account);
        BigDecimal billable = billable(project, mile);
        requireCost(provider, project, billable, product.getUnitPrice().multiply(billable));
        String label = account.length() < 5 ? "已授权账号" : account.substring(0, 2) + "***"
                + account.substring(account.length() - 2);
        return new PreparedOrder(nested(form), order.quantity(), mile, billable, label,
                null, null, ServiceAccountFingerprint.create(account));
    }

    private void applyZone(ApiProvider provider, Map<String, Object> form, Map<String, String> fields,
                           String project, String account) {
        if ("4".equals(project)) {
            if (fields.containsKey("zoneId") && !fields.get("zoneId").isEmpty()) throw bad("此项目不接受跑区参数");
            form.put("schoolName", "");
            form.put("zoneId", "1");
            form.put("zoneName", "-");
        } else {
            Rules rules = rules(provider, account);
            String zoneId = value(fields, "zoneId", 19);
            Choice zone = rules.zones().stream().filter(z -> z.value().equals(zoneId)).findFirst()
                    .orElseThrow(() -> bad("跑区已变化，请重新查询并选择"));
            form.put("schoolName", rules.school());
            form.put("zoneId", zone.value());
            form.put("zoneName", zone.label());
        }
    }

    @Override
    public Lookup orderOptions(ApiProvider provider, ServiceOrder order) {
        JsonNode row = exactOrder(provider, order);
        String[] window = remoteWindow(row.path("run_time"));
        String date = text(row.path("start_date"), 10);
        try { LocalDate.parse(date, DATE); } catch (DateTimeException ex) { throw invalid(); }
        return new Lookup(Map.of("startDate", date, "startTime", window[0], "endTime", window[1],
                "weekdays", remoteWeeks(row.path("run_week"))), List.of(),
                "已读取下单时的安排。修改后的实际时间请在执行记录中核对；修改安排不会增加次数或扣费。");
    }

    @Override
    public Map<String, Object> prepareAction(ApiProvider provider, ServiceOrder order,
                                              String action, Map<String, String> fields) {
        if ("EDIT_PLAN".equals(action)) {
            validateFields(fields, PLAN_INPUTS);
            Map<String, Object> plan = plan(fields, false);
            requireMutable(exactOrder(provider, order));
            return flatten(plan, "");
        }
        if ("CHANGE_TIME".equals(action)) {
            validateFields(fields, Set.of("taskId", "time", "page"));
            String id = value(fields, "taskId", 64), time = value(fields, "time", 19);
            futureTime(time);
            String page = value(fields, "page", 3);
            if (!page.matches("[1-9][0-9]{0,2}") || Integer.parseInt(page) > 100) throw bad("日志分页参数错误");
            requireMutable(exactOrder(provider, order));
            RunLogPage logs = logsWithoutIdentity(provider, order, Integer.parseInt(page));
            if (logs.items().stream().filter(log -> id.equals(log.id()) && log.editable()).count() != 1)
                throw bad("该任务不属于当前记录页，或已经结束，不能修改时间");
            return Map.of("task_id", id, "start_time", time);
        }
        if ("CANCEL".equals(action)) {
            validateFields(fields, Set.of());
            requireMutable(exactOrder(provider, order));
            remaining(provider, order);
            return Map.of();
        }
        throw bad("该服务不支持此操作");
    }

    @Override
    public int refundRemaining(ApiProvider provider, ServiceOrder order) {
        throw bad("请先取消订单，再核对退款；不能凭取消提示直接退款");
    }

    @Override
    public void checkAddTimes(ApiProvider provider, ServiceOrder order, int quantity) {
        throw bad("此项目不支持增加次数，请按需要购买新计划");
    }

    @Override
    public RemoteResult execute(ApiProvider provider, ServiceProduct product, ServiceOrder order,
                                String action, Map<String, Object> fields) {
        if ("CREATE".equals(action)) {
            String project = project(product);
            Map<String, Object> body = checkedCreate(provider, product, order, fields);
            requireCost(provider, project, billable(project, order.getDistance()), order.getUnitCharge());
            // Only one business write. Discovery failures must never cause a second add_order.
            String yid = receiptId(call(provider, "add_order", body).path("id"));
            JsonNode row = discoverCreated(provider, order, body, yid);
            String status = "1".equals(text(row.path("status"), 10)) ? "ACTIVE" : "ATTENTION";
            return new RemoteResult(yid, status, 0, null, positiveId(row.path("id")));
        }
        if (!Set.of("CANCEL", "EDIT_PLAN", "CHANGE_TIME").contains(action)) throw bad("该服务不支持此操作");
        requireMutable(exactOrder(provider, order));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", order.getExternalOrderNo());
        int used = order.getCompleted();
        String endpoint;
        if ("CANCEL".equals(action)) {
            if (fields == null || !fields.isEmpty()) throw bad("取消订单不接受额外参数");
            used = order.getQuantity() - remaining(provider, order);
            endpoint = "cancel_order";
        } else if ("CHANGE_TIME".equals(action)) {
            if (fields == null || !fields.keySet().equals(Set.of("task_id", "start_time"))) throw bad("任务时间快照不完整");
            String task = field(fields, "task_id"), time = field(fields, "start_time");
            futureTime(time);
            if (tasks(provider, order).stream().filter(log -> task.equals(log.id()) && log.editable()).count() != 1)
                throw bad("任务已变化，请核对执行记录");
            body.put("id", task);
            body.put("runTime", time);
            endpoint = "edit_run_time";
        } else {
            body.putAll(checkedBatchPlan(fields));
            endpoint = "batch_edit_time";
        }
        call(provider, endpoint, body);
        return new RemoteResult(order.getExternalOrderNo(), "CANCEL".equals(action) ? "REFUND_REVIEW" : "ACTIVE",
                "CANCEL".equals(action) ? used : null, null, order.getExternalSubOrderNo());
    }

    private Map<String, Object> checkedCreate(ApiProvider provider, ServiceProduct product, ServiceOrder order,
                                              Map<String, Object> fields) {
        if (order == null || !TYPE.equals(order.getProviderType()) || !product.getProject().equals(order.getProject())
                || !supported(order.getProject(), order.getRemoteProductId()) || fields == null
                || order.getQuantity() == null || order.getQuantity() < 1 || order.getQuantity() > 100
                || order.getDistance() == null || order.getUnitCharge() == null || order.getUnitCharge().signum() <= 0)
            throw bad("下单快照不一致");
        Map<String, Object> body = new LinkedHashMap<>(fields);
        String project = order.getProject(), account = field(body, "form[uid]");
        BigDecimal suppliedDistance;
        try { suppliedDistance = distance(new BigDecimal(field(body, "form[mile]"))); }
        catch (NumberFormatException ex) { throw bad("下单距离快照不正确"); }
        if (!project.equals(field(body, "form[appId]")) || !binding(order).matches(account)
                || !String.valueOf(order.getQuantity()).equals(field(body, "form[days]"))
                || suppliedDistance.compareTo(order.getDistance()) != 0)
            throw bad("下单快照不一致");
        String[] window = window(field(body, "form[runTime]"));
        Map<String, String> inputs = new LinkedHashMap<>(Map.of("account", account,
                "startDate", field(body, "form[startDate]"), "startTime", window[0], "endTime", window[1],
                "weekdays", flattenedWeeks(body, "form[weeks]")));
        if (!"4".equals(project)) inputs.put("zoneId", field(body, "form[zoneId]"));
        account(inputs, project);
        Map<String, Object> fresh = plan(inputs, true);
        applyZone(provider, fresh, inputs, project, account);
        fresh.put("appId", project);
        fresh.put("uid", account);
        fresh.put("days", order.getQuantity());
        fresh.put("mile", order.getDistance().toPlainString());
        Map<String, Object> expected = nested(fresh);
        if (!body.keySet().equals(expected.keySet())) throw bad("下单快照包含额外字段");
        for (String key : expected.keySet()) {
            if (!"form[mile]".equals(key) && !String.valueOf(expected.get(key)).equals(field(body, key)))
                throw bad("学校、跑区或安排已变化，请核对后处理");
        }
        return expected;
    }

    private Map<String, Object> checkedBatchPlan(Map<String, Object> fields) {
        Map<String, String> inputs = Map.of("startDate", field(fields, "startDate"),
                "startTime", field(fields, "startTime"), "endTime", field(fields, "endTime"),
                "weekdays", flattenedWeeks(fields, "weeks"));
        Map<String, Object> expected = flatten(plan(inputs, false), "");
        if (!fields.keySet().equals(expected.keySet())) throw bad("执行安排快照不一致");
        return expected;
    }

    @Override
    public RemoteResult sync(ApiProvider provider, ServiceOrder order) {
        JsonNode row = exactOrder(provider, order);
        int used = order.getQuantity() - remaining(provider, order);
        String status = "1".equals(text(row.path("status"), 10)) ? "ACTIVE" : "ATTENTION";
        // Consumption is not a successful run. Even zero remaining never infers COMPLETED.
        return new RemoteResult(order.getExternalOrderNo(), status, used, null, order.getExternalSubOrderNo());
    }

    private int remaining(ApiProvider provider, ServiceOrder order) {
        int remaining = count(call(provider, "get_residue_num", Map.of("orderId", order.getExternalOrderNo()))
                .path("residue_num"), 100);
        if (remaining > order.getQuantity() || order.getQuantity() - remaining < order.getCompleted()) throw invalid();
        return remaining;
    }

    @Override
    public RunLogPage logs(ApiProvider provider, ServiceOrder order, int page) {
        if (page < 1 || page > 100) throw bad("日志分页超出范围");
        exactOrder(provider, order);
        return logsWithoutIdentity(provider, order, page);
    }

    private RunLogPage logsWithoutIdentity(ApiProvider provider, ServiceOrder order, int page) {
        List<RunLog> items = tasks(provider, order);
        int start = Math.min((page - 1) * 20, items.size()), end = Math.min(start + 20, items.size());
        return new RunLogPage(List.copyOf(items.subList(start, end)), page, end < items.size());
    }

    private List<RunLog> tasks(ApiProvider provider, ServiceOrder order) {
        JsonNode rows = array(call(provider, "get_log", Map.of("orderId", order.getExternalOrderNo()))
                .path("data").path("runTask"), 2000);
        List<RunLog> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            String id = receiptId(row.path("id"));
            if (!ids.add(id)) throw invalid();
            String time = remoteTimestamp(row.path("runTime"));
            String end = row.path("endTime").isNull() || row.path("endTime").isMissingNode()
                    || row.path("endTime").asText().isEmpty() ? null : remoteTimestamp(row.path("endTime"));
            String code = text(row.path("statusCode"), 10);
            String status = switch (code) { case "0" -> "待执行"; case "1" -> "跑步结束";
                case "-1" -> "需要关注"; default -> "待核对"; };
            items.add(new RunLog(id, time, status, Set.of("0", "-1").contains(code), end));
        }
        return List.copyOf(items);
    }

    @Override
    public OrderText scoreInfo(ApiProvider provider, ServiceOrder order) {
        exactOrder(provider, order);
        JsonNode node = call(provider, "get_auth_link", Map.of("orderId", order.getExternalOrderNo())).path("msg");
        if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 2000) throw invalid();
        String value = node.asText().replace("\r\n", "\n");
        if (value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')
                || value.contains(provider.getApiKey())) throw invalid();
        return new OrderText(value);
    }

    private JsonNode exactOrder(ApiProvider provider, ServiceOrder order) {
        validateOrder(order);
        JsonNode root = orders(provider, 1, 1, order.getExternalSubOrderNo());
        JsonNode rows = root.path("data");
        if (rows.size() != 1 || count(root.path("pagination").path("total"), 1000000) != 1) throw invalid();
        JsonNode row = rows.get(0);
        if (!order.getExternalSubOrderNo().equals(positiveId(row.path("id")))
                || !order.getExternalOrderNo().equals(receiptId(row.path("yid")))) throw invalid();
        verifyIdentity(provider, order, row);
        return row;
    }

    private JsonNode discoverCreated(ApiProvider provider, ServiceOrder order, Map<String, Object> form, String yid) {
        String account = field(form, "form[uid]");
        JsonNode match = null;
        Set<String> ids = new HashSet<>();
        int expectedTotal = -1, pages = 1;
        for (int page = 1; page <= pages; page++) {
            JsonNode root = orders(provider, page, 2, account);
            int total = count(root.path("pagination").path("total"), 1000000);
            pages = count(root.path("pagination").path("last_page"), 10000);
            if (pages > MAX_PAGES || expectedTotal != -1 && expectedTotal != total) throw invalid();
            expectedTotal = total;
            for (JsonNode row : root.path("data")) {
                String id = positiveId(row.path("id"));
                if (!ids.add(id)) throw invalid();
                if (!yid.equals(receiptId(row.path("yid")))) continue;
                if (match != null) throw invalid();
                verifyIdentity(provider, order, row);
                if (!account.equals(text(row.path("uid"), 64))
                        || !field(form, "form[zoneId]").equals(positiveId(row.path("zone_id")))
                        || !field(form, "form[zoneName]").equals(text(row.path("zone_name"), 100))
                        || !field(form, "form[startDate]").equals(text(row.path("start_date"), 10))
                        || !Arrays.equals(window(field(form, "form[runTime]")), remoteWindow(row.path("run_time")))
                        || !flattenedWeeks(form, "form[weeks]").equals(remoteWeeks(row.path("run_week")))) throw invalid();
                match = row;
            }
        }
        if (match == null) throw invalid();
        return match;
    }

    private JsonNode orders(ApiProvider provider, int page, int type, String keyword) {
        JsonNode root = call(provider, "orders", Map.of("page", page, "limit", PAGE_SIZE, "type", type, "keywords", keyword));
        JsonNode pagination = root.path("pagination"), rows = array(root.path("data"), PAGE_SIZE);
        int total = count(pagination.path("total"), 1000000), last = count(pagination.path("last_page"), 10000);
        if (count(pagination.path("page"), 10000) != page || count(pagination.path("limit"), 1000) != PAGE_SIZE
                || last != (total + PAGE_SIZE - 1) / PAGE_SIZE
                || rows.size() != Math.min(PAGE_SIZE, Math.max(0, total - (page - 1) * PAGE_SIZE))) throw invalid();
        return root;
    }

    private void verifyIdentity(ApiProvider provider, ServiceOrder order, JsonNode row) {
        if (!order.getProject().equals(text(row.path("app_id"), 1))
                || !provider.getUsername().equals(positiveId(row.path("user_id")))
                || !binding(order).matches(text(row.path("uid"), 64))
                || count(row.path("days"), 100) != order.getQuantity()
                || decimal(row.path("mile"), 1).compareTo(order.getDistance()) != 0) throw invalid();
    }

    private static ServiceAccountFingerprint binding(ServiceOrder order) {
        try {
            if (order.getScheduleJson() == null || order.getScheduleJson().length() > 500) throw invalid();
            ServiceAccountFingerprint fingerprint = JSON.readValue(order.getScheduleJson(), ServiceAccountFingerprint.class);
            if (fingerprint == null) throw invalid();
            return fingerprint;
        } catch (Exception ex) { throw invalid(); }
    }

    private static void validateOrder(ServiceOrder order) {
        if (order == null || !TYPE.equals(order.getProviderType()) || !supported(order.getProject(), order.getRemoteProductId())
                || order.getExternalOrderNo() == null || !order.getExternalOrderNo().matches("[A-Za-z0-9_-]{1,64}")
                || order.getExternalSubOrderNo() == null || !order.getExternalSubOrderNo().matches("[1-9][0-9]{0,18}")
                || order.getQuantity() == null || order.getQuantity() < 1 || order.getQuantity() > 100 || order.getCompleted() == null
                || order.getCompleted() < 0 || order.getCompleted() > order.getQuantity() || order.getDistance() == null)
            throw bad("订单编号或账号绑定不完整，请联系管理员核对");
        distance(order.getDistance());
    }

    private static void requireMutable(JsonNode row) {
        if (!Set.of("1", "-1").contains(text(row.path("status"), 10))) throw bad("订单状态需核对，暂不能修改或取消");
    }

    private void requireCost(ApiProvider provider, String project, BigDecimal billable, BigDecimal charge) {
        if (charge == null || charge.compareTo(costUpperBound(price(provider, project), billable)) < 0)
            throw bad("当前计划费用已变化，请联系管理员核对价格");
    }

    private static BigDecimal billable(String project, BigDecimal mile) {
        return "4".equals(project) ? mile : mile.min(new BigDecimal("2"));
    }

    private static BigDecimal distance(BigDecimal mile) {
        if (mile == null || mile.compareTo(BigDecimal.ONE) < 0 || mile.compareTo(BigDecimal.TEN) > 0
                || mile.stripTrailingZeros().scale() > 1) throw bad("每次距离须为 1–10 公里，最多一位小数");
        return mile;
    }

    private static Map<String, Object> plan(Map<String, String> fields, boolean create) {
        String date = value(fields, "startDate", 10), start = clock(value(fields, "startTime", 8)),
                end = clock(value(fields, "endTime", 8));
        try {
            LocalDate parsed = LocalDate.parse(date, DATE), today = ServiceTime.now().toLocalDate();
            if (!parsed.format(DATE).equals(date) || parsed.isBefore(today) || parsed.isAfter(today.plusYears(1)))
                throw bad("开始日期须在今天至未来一年内（北京时间）");
        } catch (DateTimeException ex) { throw bad("开始日期格式不正确"); }
        if (start.compareTo(end) >= 0) throw bad("结束时间须晚于开始时间，不支持跨天时间段");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("startDate", date);
        if (create) values.put("runTime", start + " - " + end);
        else { values.put("startTime", start); values.put("endTime", end); }
        values.put("weeks", weeks(value(fields, "weekdays", 13)));
        return values;
    }

    private static String account(Map<String, String> fields, String project) {
        String account = value(fields, "account", 64);
        if (!account.matches("[A-Za-z0-9_-]{1,64}") || "4".equals(project) && !account.matches("[0-9]{7,15}"))
            throw bad("请填写有效的账号 UID；乐健体育请填写数字手机号");
        return account;
    }

    private static List<Integer> weeks(String raw) {
        if (!raw.matches("[1-7](?:,[1-7]){0,6}")) throw bad("请选择有效的执行星期");
        List<Integer> values = Arrays.stream(raw.split(",")).map(Integer::parseInt).sorted().toList();
        if (new HashSet<>(values).size() != values.size()) throw bad("执行星期不能重复");
        return values;
    }

    private static String remoteWeeks(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > 7) throw invalid();
        List<String> values = new ArrayList<>();
        for (JsonNode day : node) values.add(text(day, 1));
        try { return joinWeeks(weeks(String.join(",", values))); } catch (BusinessException ex) { throw invalid(); }
    }

    private static String flattenedWeeks(Map<String, Object> fields, String prefix) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 7 && fields.containsKey(prefix + "[" + i + "]"); i++)
            values.add(field(fields, prefix + "[" + i + "]"));
        return joinWeeks(weeks(String.join(",", values)));
    }

    private static String joinWeeks(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    private static String clock(String raw) {
        String value = raw.matches("[0-9]{2}:[0-9]{2}") ? raw + ":00" : raw;
        try {
            if (!value.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}")) throw bad("时间格式须为 HH:mm:ss");
            LocalTime.parse(value, TIME);
            return value;
        } catch (DateTimeException ex) { throw bad("时间格式不正确"); }
    }

    private static String remoteClock(JsonNode node) {
        try { return clock(text(node, 8)); } catch (BusinessException ex) { throw invalid(); }
    }

    private static String[] window(String value) {
        String[] parts = value.split(" - ", -1);
        if (parts.length != 2) throw bad("时间段格式不正确");
        String start = clock(parts[0]), end = clock(parts[1]);
        if (start.compareTo(end) >= 0) throw bad("结束时间须晚于开始时间");
        return new String[] { start, end };
    }

    private static String[] remoteWindow(JsonNode node) {
        try { return window(text(node, 19)); } catch (BusinessException ex) { throw invalid(); }
    }

    private static void futureTime(String value) {
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) throw bad("任务时间格式不正确");
            LocalDateTime time = LocalDateTime.parse(value, STAMP), now = ServiceTime.now();
            if (!time.isAfter(now) || time.isAfter(now.plusYears(1))) throw bad("请选择未来一年内的任务时间（北京时间）");
        } catch (DateTimeException ex) { throw bad("任务时间格式不正确"); }
    }

    private static String remoteTimestamp(JsonNode node) {
        String value = text(node, 19);
        try {
            if (!LocalDateTime.parse(value, STAMP).format(STAMP).equals(value)) throw invalid();
        } catch (DateTimeException ex) { throw invalid(); }
        return value;
    }

    private JsonNode call(ApiProvider provider, String action, Map<String, Object> values) {
        if (provider == null || !TYPE.equals(provider.getProviderType()) || !ACTIONS.contains(action))
            throw bad("不支持的运动服务请求");
        if (provider.getUsername() == null || !provider.getUsername().matches("[1-9][0-9]{0,18}")
                || provider.getApiKey() == null || provider.getApiKey().isBlank()
                || provider.getApiKey().length() > 2048 || controls(provider.getApiKey()))
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        Map<String, Object> body = new LinkedHashMap<>(values);
        body.put("login_uid", provider.getUsername());
        body.put("login_key", provider.getApiKey());
        String url = normalizer.normalize(provider.getApiUrl(), TYPE).toASCIIString()
                + "/ldrun/api.php?act=" + action;
        if ("get_price".equals(action)) {
            String project = field(values, "appId");
            if (!supported(project, project)) throw bad("不支持的运动项目");
            url += "&appId=" + project;
            body.remove("appId");
        }
        String raw = http.postForString(provider, url, body);
        if (raw == null || raw.length() > 262144 || raw.getBytes(StandardCharsets.UTF_8).length > 262144)
            throw invalid();
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isObject() || !(root.path("code").isIntegralNumber()
                    || root.path("code").isTextual())) throw invalid();
            String code = root.path("code").asText();
            if (code.matches("-[1-9][0-9]{0,3}"))
                throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
            if (!"1".equals(code)) throw invalid();
            return root;
        } catch (ProviderRequestException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private static Map<String, Object> nested(Map<String, Object> form) {
        return flatten(form, "form");
    }

    private static Map<String, Object> flatten(Map<String, Object> values, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String name = prefix.isEmpty() ? key : prefix + "[" + key + "]";
            if (value instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) result.put(name + "[" + i + "]", list.get(i));
            } else result.put(name, value);
        });
        return result;
    }

    private static String project(ServiceProduct product) {
        if (product == null || !TYPE.equals(product.getProviderType())
                || !supported(product.getProject(), product.getRemoteProductId())) throw bad("不支持的运动商品");
        return product.getProject();
    }

    private static void validateFields(Map<String, String> fields, Set<String> allowed) {
        if (fields == null || !allowed.containsAll(fields.keySet())) throw bad("表单包含不支持的字段");
        fields.forEach((key, value) -> {
            if (value == null || value.length() > 2048 || controls(value)) throw bad("字段格式不正确");
        });
    }

    private static String value(Map<String, String> fields, String key, int max) {
        String value = fields.getOrDefault(key, "").trim();
        if (value.isEmpty() || value.length() > max || controls(value)) throw bad("请填写完整的账号信息与执行安排");
        return value;
    }

    private static String field(Map<String, Object> fields, String key) {
        Object value = fields == null ? null : fields.get(key);
        if (!(value instanceof String || value instanceof Number)) throw bad("操作快照不完整");
        String text = String.valueOf(value);
        if (text.length() > 2048 || controls(text)) throw bad("操作快照格式不正确");
        return text;
    }

    private static boolean controls(String value) { return value.codePoints().anyMatch(Character::isISOControl); }

    private static String text(JsonNode node, int max) {
        if (!(node.isTextual() || node.isIntegralNumber())) throw invalid();
        String value = node.asText();
        if (value.isBlank() || value.length() > max || controls(value)) throw invalid();
        return value;
    }

    private static String positiveId(JsonNode node) {
        String value = text(node, 19);
        if (!value.matches("[1-9][0-9]{0,18}")) throw invalid();
        return value;
    }

    private static String receiptId(JsonNode node) {
        String value = text(node, 64);
        if (!value.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return value;
    }

    private static int count(JsonNode node, int max) {
        String value = text(node, 10);
        if (!value.matches("0|[1-9][0-9]{0,9}")) throw invalid();
        long count = Long.parseLong(value);
        if (count > max) throw invalid();
        return (int) count;
    }

    private static BigDecimal decimal(JsonNode node, int scale) {
        if (!(node.isNumber() || node.isTextual())) throw invalid();
        String value = node.asText();
        if (!value.matches("[0-9]{1,8}(?:\\.[0-9]{1," + scale + "})?")) throw invalid();
        return new BigDecimal(value);
    }

    private static JsonNode array(JsonNode node, int max) {
        if (!node.isArray() || node.size() > max) throw invalid();
        return node;
    }

    private static BusinessException bad(String message) { return new BusinessException(message); }
    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
