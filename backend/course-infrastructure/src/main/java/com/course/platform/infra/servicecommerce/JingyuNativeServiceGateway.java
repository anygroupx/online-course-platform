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
import com.course.platform.domain.vo.plugin.PluginSchool;
import com.course.platform.domain.vo.plugin.PluginSchoolPage;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Fixed Jingyu protocol. The row ID and the business receipt are separate identities. */
@Component
@RequiredArgsConstructor
public class JingyuNativeServiceGateway implements NativeServiceGateway, PluginReadOnlyConnector {
    public static final String TYPE = "jingyu";
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;
    private static final Set<String> PROJECTS = Set.of("keep", "bdlp", "yyd");
    private static final String SCHOOL_SCOPE = "jingyu:yyd:v1";
    private static final String SCHOOL_CONTEXT = "jingyu:yyd:school";
    private static final Set<String> COMMON_ACTIONS = Set.of("get_price", "orders", "get_task_data",
            "get_remain_count", "refund", "change_run_status", "edit_task", "delay_task", "fast_delay_task");
    private static final Set<String> ORDER_ACTIONS = Set.of("PAUSE", "RESUME", "DELAY", "DELAY_TASK", "CHANGE_TIME", "REFUND");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxNumberLength(32).maxStringLength(4096).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public static boolean supported(String project, String product) {
        return project != null && PROJECTS.contains(project) && project.equals(product);
    }

    @Override
    public String getProviderType() { return TYPE; }

    @Override
    public List<PluginProjectOption> projects() {
        // YMTY authorization/repair pricing is not advertised until its full flow is implemented.
        return List.of(new PluginProjectOption("keep", "Keep 自由跑"), new PluginProjectOption("bdlp", "步道乐跑"),
                new PluginProjectOption("yyd", "校园运动"));
    }

    @Override
    public void testConnection(ApiProvider provider) { fetchCatalog(provider, null); }

    @Override
    public List<PluginProduct> fetchCatalog(ApiProvider provider, String project) {
        if (project != null && !project.isBlank() && !supported(project, project)) throw bad("不支持的运动项目");
        List<PluginProduct> products = new ArrayList<>();
        for (PluginProjectOption option : projects()) {
            if (project == null || project.isBlank() || project.equals(option.id()))
                products.add(new PluginProduct(option.id(), option.name(), price(provider, option.id()),
                        "bdlp".equals(option.id()) ? "元/次" : "元/次·公里"));
        }
        return List.copyOf(products);
    }

    /** Unlike LeiDian, the price endpoint exposes an already cent-rounded rate R. */
    public static BigDecimal unitCharge(BigDecimal rate, String project, BigDecimal distance) {
        if (!supported(project, project) || rate == null || rate.signum() < 0
                || rate.compareTo(new BigDecimal("9999")) > 0 || rate.stripTrailingZeros().scale() > 6)
            throw bad("计费参数不正确");
        return rate.multiply(billable(project, distance)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal billable(String project, BigDecimal distance) {
        if (!supported(project, project)) throw bad("不支持的运动项目");
        distance(distance);
        return "bdlp".equals(project) ? BigDecimal.ONE : distance;
    }

    private BigDecimal price(ApiProvider provider, String project) {
        BigDecimal result = decimal(call(provider, project, "get_price", Map.of()).path("data"), 2);
        if (result.compareTo(new BigDecimal("9999")) > 0) throw invalid();
        return result;
    }

    private record Account(String studentId, List<Choice> zones, Map<String, String> facts,
                           String school, String authType, String authTime, boolean authorized,
                           List<SchoolRunRule> schoolRules) {}

    @Override
    public PluginSchoolPage schools(ApiProvider provider, ServiceProduct product, int page, String keyword) {
        if (!"yyd".equals(project(product))) throw bad("该项目无需选择学校");
        if (page < 1 || page > 10) throw bad("学校查询页码超出范围");
        List<PluginSchool> schools = schoolList(provider, keyword);
        int start = Math.min((page - 1) * 20, schools.size()), end = Math.min(start + 20, schools.size());
        return new PluginSchoolPage(schools.subList(start, end), page, 20, end < schools.size());
    }

    private List<PluginSchool> schoolList(ApiProvider provider, String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 80 || controls(keyword))
            throw bad("请输入 1–80 字的学校名称后查询");
        JsonNode rows = array(call(provider, "yyd", "get_school_data", Map.of("school", keyword.trim()))
                .path("data").path("list"), 200);
        List<PluginSchool> schools = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            String id = id(row.path("school_id")), name = text(row.path("name"), 80);
            if (!ids.add(id) || !name.equals(name.trim())) throw invalid();
            schools.add(new PluginSchool(id, name));
        }
        return List.copyOf(schools);
    }

    private PluginSchool verifiedSchool(ApiProvider provider, Map<String, String> fields) {
        String schoolId = value(fields, "schoolId", 19), schoolName = value(fields, "schoolName", 80);
        if (!schoolId.matches("[1-9][0-9]{0,18}")) throw bad("请从查询结果中选择学校");
        List<PluginSchool> matches = schoolList(provider, schoolName).stream()
                .filter(school -> schoolName.equals(school.name())).toList();
        // YYD order rows omit school_id. Ambiguous school names cannot prove ownership later.
        if (matches.size() != 1 || !schoolId.equals(matches.get(0).id()))
            throw bad("学校信息已变化或存在同名学校，请重新查询核对");
        return matches.get(0);
    }

    @Override
    public Lookup lookup(ApiProvider provider, ServiceProduct product, Map<String, String> fields) {
        String project = project(product);
        validateFields(fields, "yyd".equals(project) ? Set.of("account", "password", "schoolId", "schoolName")
                : "keep".equals(project) ? Set.of("account", "password") : Set.of("account"));
        Account account = accountInfo(provider, project, fields);
        if ("yyd".equals(project)) return new Lookup(account.facts(), account.zones(),
                "请选择学校对应的跑步规则，再核对最低距离与每次执行时间。", null, null, null, null, account.schoolRules());
        return new Lookup(account.facts(), account.zones(), "bdlp".equals(project)
                ? "请核对授权状态并选择跑区；每次费用与距离无关。授权失效时不能提交订单。"
                : "请选择跑区、配速及每次执行时间；自由跑不保证计入有效成绩。");
    }

    private Account accountInfo(ApiProvider provider, String project, Map<String, String> fields) {
        String account = account(fields, project);
        if ("yyd".equals(project)) return schoolAccount(provider, fields, account);
        Map<String, Object> inputs = "bdlp".equals(project) ? Map.of("uid", account)
                : Map.of("phone", account, "password", password(fields));
        JsonNode data = call(provider, project, "get_" + project + "_user_info", inputs).path("data");
        JsonNode student = data.path("student");
        if (!student.isObject()) throw invalid();
        String studentId = id(student.path("bdlp".equals(project) ? "uid" : "student_id"));
        Map<String, String> facts = new LinkedHashMap<>();
        String school = null, authType = null, authTime = null;
        boolean authorized = true;
        JsonNode zones;
        if ("bdlp".equals(project)) {
            if (!account.equals(studentId)) throw invalid();
            school = text(student.path("school").path("name"), 100);
            JsonNode device = student.path("device");
            authorized = !flag(device.path("is_expired"));
            authType = text(device.path("login_type_display"), 100);
            authTime = timestamp(device.path("refresh_at"));
            BigDecimal minimum = decimal(student.path("run_rule").path("min_dis"), 1);
            distance(minimum);
            facts.put("schoolName", school);
            facts.put("minDistance", minimum.toPlainString());
            facts.put("authorizationState", authorized ? "VALID" : "EXPIRED");
            facts.put("authorizationType", authType);
            facts.put("authorizedAt", authTime);
            zones = call(provider, project, "get_bdlp_zone_data", Map.of("uid", account)).path("data").path("list");
        } else {
            if (student.has("phone") && !account.equals(text(student.path("phone"), 15))) throw invalid();
            JsonNode defaultZone = student.path("default_zone");
            if (!id(student.path("default_zone_id")).equals(id(defaultZone.path("zone_id")))) throw invalid();
            String query = text(defaultZone.path("name"), 100);
            zones = call(provider, project, "get_keep_zone_data", Map.of("school", query)).path("data").path("list");
        }
        List<Choice> choices = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode zone : array(zones, 200)) {
            String zoneId = id(zone.path("zone_id"));
            if (!seen.add(zoneId)) throw invalid();
            choices.add(new Choice("zoneId", zoneId, text(zone.path("name"), 100)));
        }
        if (choices.isEmpty()) throw invalid();
        return new Account(studentId, List.copyOf(choices), Map.copyOf(facts), school, authType, authTime, authorized, List.of());
    }

    private Account schoolAccount(ApiProvider provider, Map<String, String> fields, String account) {
        String password = password(fields);
        PluginSchool school = verifiedSchool(provider, fields);
        JsonNode student = call(provider, "yyd", "get_yyd_user_info",
                Map.of("school_id", school.id(), "number", account, "password", password)).path("data").path("student");
        if (!student.isObject() || student.has("number") && !account.equals(text(student.path("number"), 64))
                || student.has("school_id") && !school.id().equals(id(student.path("school_id")))) throw invalid();
        String studentId = id(student.path("student_id"));
        List<SchoolRunRule> rules = new ArrayList<>();
        List<Choice> choices = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : array(student.path("run_rule_items"), 200)) {
            String ruleId = id(row.path("run_rule_item_id"));
            if (!ids.add(ruleId)) throw invalid();
            BigDecimal minimum = decimal(row.path("min_dis"), 1);
            if (minimum.compareTo(BigDecimal.ONE) < 0 || minimum.compareTo(new BigDecimal("100")) > 0) throw invalid();
            JsonNode zone = row.path("zone");
            String zoneId = id(zone.path("zone_id")), zoneName = text(zone.path("name"), 100);
            rules.add(new SchoolRunRule(ruleId, zoneId, zoneName, minimum.toPlainString()));
            choices.add(new Choice("runRuleId", ruleId, zoneName));
        }
        if (rules.isEmpty()) throw invalid();
        return new Account(studentId, List.copyOf(choices), Map.of("schoolId", school.id(), "schoolName", school.name()),
                school.name(), null, null, true, List.copyOf(rules));
    }

    @Override
    public PreparedOrder prepare(ApiProvider provider, ServiceProduct product, OrderForm form) {
        String project = project(product);
        if (form == null || !form.authorizedAccount() || form.schedule() != null || form.accountSessionId() != null)
            throw bad("请确认账号授权并填写运动计划");
        Map<String, Object> body = createBody(provider, project, form.fields(), form.quantity(), form.distance(), form.taskTimes());
        BigDecimal charge = unitCharge(product.getUnitPrice(), project, form.distance());
        requireCost(provider, project, form.distance(), charge);
        String account = account(form.fields(), project);
        String label = account.length() < 5 ? "已授权账号" : account.substring(0, 2) + "***" + account.substring(account.length() - 2);
        return new PreparedOrder(body, form.quantity(), form.distance(), billable(project, form.distance()), label,
                null, null, "yyd".equals(project) ? schoolBinding(body) : ServiceAccountFingerprint.create(account));
    }

    private Map<String, Object> createBody(ApiProvider provider, String project, Map<String, String> fields,
                                           int quantity, BigDecimal distance, List<String> times) {
        validateFields(fields, "yyd".equals(project) ? Set.of("account", "password", "schoolId", "schoolName", "runRuleId")
                : "bdlp".equals(project) ? Set.of("account", "zoneId", "runType")
                : Set.of("account", "password", "zoneId", "minMinute", "maxMinute"));
        distance(distance);
        validateTimes(times, quantity);
        String account = account(fields, project);
        Account info = accountInfo(provider, project, fields);
        if (!info.authorized()) throw bad("账号授权已失效，请完成授权后重新查询");
        Map<String, Object> body = new LinkedHashMap<>();
        putForm(body, "student_id", info.studentId());
        if ("yyd".equals(project)) {
            String ruleId = value(fields, "runRuleId", 19);
            SchoolRunRule rule = info.schoolRules().stream().filter(item -> ruleId.equals(item.id())).findFirst()
                    .orElseThrow(() -> bad("跑步规则已变化，请重新查询并选择"));
            if (distance.compareTo(new BigDecimal(rule.minDistance())) < 0) throw bad("每次距离不能低于所选规则的最低距离");
            putForm(body, "zone_id", rule.zoneId());
            putForm(body, "zone_name", rule.zoneName());
            putForm(body, "run_rule_item_id", rule.id());
            putForm(body, "school_id", value(fields, "schoolId", 19));
            putForm(body, "school_name", info.school());
            putForm(body, "number", account);
            putForm(body, "password", password(fields));
        } else {
            String zoneId = value(fields, "zoneId", 19);
            Choice zone = info.zones().stream().filter(item -> zoneId.equals(item.value())).findFirst()
                    .orElseThrow(() -> bad("跑区已变化，请重新查询并选择"));
            putForm(body, "zone_id", zone.value());
            putForm(body, "zone_name", zone.label());
        }
        putForm(body, "dis", distance.stripTrailingZeros().toPlainString());
        if ("bdlp".equals(project)) {
            String runType = value(fields, "runType", 1);
            if (!Set.of("1", "2").contains(runType)) throw bad("请选择有效跑或自由跑");
            putForm(body, "uid", account);
            putForm(body, "run_type", runType);
            putForm(body, "is_jrxy", "false");
            putForm(body, "school_name", info.school());
            putForm(body, "is_auth", "1");
            putForm(body, "auth_type", info.authType());
            putForm(body, "auth_time", info.authTime());
        } else if ("keep".equals(project)) {
            putForm(body, "phone", account);
            putForm(body, "password", password(fields));
            putForm(body, "run_type", "1");
            putForm(body, "min_minute", boundedInput(fields, "minMinute", 3, 6));
            putForm(body, "max_minute", boundedInput(fields, "maxMinute", 8, 15));
        }
        for (int i = 0; i < times.size(); i++) body.put("form[task_list][" + i + "][start_time]", times.get(i));
        return Map.copyOf(body);
    }

    @Override
    public int refundRemaining(ApiProvider provider, ServiceOrder order) {
        requireMutable(exactOrder(provider, order));
        int remaining = count(call(provider, order.getProject(), "get_remain_count",
                Map.of("id", order.getExternalSubOrderNo())).path("data").path("refund_cnt"), 365);
        if (remaining > order.getQuantity() - order.getCompleted()) throw invalid();
        return remaining;
    }

    @Override
    public void checkAddTimes(ApiProvider provider, ServiceOrder order, int quantity) {
        throw bad("该项目不支持增加次数，请按需要购买新计划");
    }

    @Override
    public Map<String, Object> prepareAction(ApiProvider provider, ServiceOrder order,
                                              String action, Map<String, String> fields) {
        if (!ORDER_ACTIONS.contains(action)) throw bad("该服务不支持此操作");
        boolean taskAction = Set.of("CHANGE_TIME", "DELAY_TASK").contains(action);
        validateFields(fields, "CHANGE_TIME".equals(action) ? Set.of("taskId", "time", "page")
                : "DELAY_TASK".equals(action) ? Set.of("taskId", "page") : Set.of());
        JsonNode row = exactOrder(provider, order);
        requireAction(row, action);
        if (!taskAction) return Map.of();
        String task = value(fields, "taskId", 64);
        int page = Integer.parseInt(boundedInput(fields, "page", 1, 19));
        RunLogPage logs = page(tasks(provider, order), page);
        if (logs.items().stream().filter(item -> task.equals(item.id()) && item.editable()).count() != 1)
            throw bad("该任务不属于当前记录页或已经结束，请刷新后重试");
        if ("DELAY_TASK".equals(action)) return Map.of("run_task_id", task);
        String time = value(fields, "time", 19);
        futureTime(time);
        return Map.of("run_task_id", task, "start_time", time);
    }

    @Override
    public RemoteResult execute(ApiProvider provider, ServiceProduct product, ServiceOrder order,
                                String action, Map<String, Object> fields) {
        if ("CREATE".equals(action)) {
            String project = project(product);
            Map<String, Object> body = checkedCreate(provider, product, order, fields);
            requireCost(provider, project, order.getDistance(), order.getUnitCharge());
            // Exactly one write; missing/ambiguous receipts are recovered by reading, never by re-submission.
            JsonNode data = call(provider, project, project + "_add", body).path("data");
            String rowId = id(data.path("id")), businessId = receiptId(data.path(project + "_order_id"));
            ServiceOrder candidate = copyWithReceipts(order, businessId, rowId);
            JsonNode row = exactOrder(provider, candidate);
            verifyCreatedFields(row, body, project);
            return result(candidate, row, tasks(provider, candidate));
        }
        if (!ORDER_ACTIONS.contains(action)) throw bad("该服务不支持此操作");
        JsonNode row = exactOrder(provider, order);
        requireAction(row, action);
        List<RunLog> tasks = tasks(provider, order);
        Map<String, Object> body = new LinkedHashMap<>();
        String endpoint;
        if ("CHANGE_TIME".equals(action) || "DELAY_TASK".equals(action)) {
            Set<String> keys = "CHANGE_TIME".equals(action) ? Set.of("run_task_id", "start_time") : Set.of("run_task_id");
            if (fields == null || !fields.keySet().equals(keys)) throw bad("任务操作快照不完整");
            String task = field(fields, "run_task_id");
            if (tasks.stream().filter(item -> task.equals(item.id()) && item.editable()).count() != 1)
                throw bad("任务已经变化，请重新核对执行记录");
            body.put(order.getProject() + "_order_id", order.getExternalOrderNo());
            if ("CHANGE_TIME".equals(action)) {
                String time = field(fields, "start_time");
                futureTime(time);
                body.put("form[run_task_id]", task);
                body.put("form[start_time]", time);
                endpoint = "edit_task";
            } else {
                body.put("run_task_id", task);
                endpoint = "delay_task";
            }
        } else {
            if (fields == null || !fields.isEmpty()) throw bad("此操作不接受额外参数");
            body.put("id", order.getExternalSubOrderNo());
            endpoint = switch (action) {
                case "PAUSE", "RESUME" -> "change_run_status";
                case "DELAY" -> "fast_delay_task";
                case "REFUND" -> "refund";
                default -> throw bad("不支持的操作");
            };
            if (Set.of("PAUSE", "RESUME").contains(action)) body.put("status", "PAUSE".equals(action) ? "0" : "1");
            if ("REFUND".equals(action) && refundRemaining(provider, order) == 0)
                throw bad("没有可退款的剩余次数，请刷新订单状态");
        }
        // Apply only the fields changed by the acknowledged operation. A pause/resume does not
        // repair partial failures or renew an expired authorization; retain their attention state.
        ObjectNode projected = row.deepCopy();
        switch (action) {
            case "PAUSE" -> projected.put("pause", "0");
            case "RESUME" -> projected.put("pause", "1");
            case "REFUND" -> projected.put("status_display", "已退款");
            case "DELAY" -> projected.put("status_display", "正常");
            default -> { }
        }
        RemoteResult result = result(order, projected, tasks);
        call(provider, order.getProject(), endpoint, body);
        // refund has no atomic quantity receipt. Null MUST enter reconciliation, never credit an estimate.
        return result;
    }

    private Map<String, Object> checkedCreate(ApiProvider provider, ServiceProduct product, ServiceOrder order,
                                              Map<String, Object> fields) {
        validateOrderShape(order);
        if (!product.getProject().equals(order.getProject()) || fields == null || fields.size() > 385
                || order.getUnitCharge() == null || order.getUnitCharge().signum() <= 0)
            throw bad("下单快照不一致");
        String project = order.getProject();
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("account", field(fields, "form[" + ("bdlp".equals(project) ? "uid" : "yyd".equals(project) ? "number" : "phone") + "]"));
        if ("yyd".equals(project)) {
            inputs.put("schoolId", field(fields, "form[school_id]"));
            inputs.put("schoolName", field(fields, "form[school_name]"));
            inputs.put("runRuleId", field(fields, "form[run_rule_item_id]"));
            inputs.put("password", field(fields, "form[password]"));
        } else inputs.put("zoneId", field(fields, "form[zone_id]"));
        if ("bdlp".equals(project)) inputs.put("runType", field(fields, "form[run_type]"));
        else if ("keep".equals(project)) {
            inputs.put("password", field(fields, "form[password]"));
            inputs.put("minMinute", field(fields, "form[min_minute]"));
            inputs.put("maxMinute", field(fields, "form[max_minute]"));
        }
        if (!("yyd".equals(project) ? matchesSchoolBody(binding(order), fields)
                : binding(order).matches(account(inputs, project)))) throw bad("账号绑定不一致，请重新核对");
        List<String> times = new ArrayList<>();
        for (int i = 0; i < order.getQuantity(); i++) times.add(field(fields, "form[task_list][" + i + "][start_time]"));
        Map<String, Object> expected = createBody(provider, project, inputs, order.getQuantity(), order.getDistance(), times);
        if (!expected.equals(fields)) throw bad("账号、授权、跑区或计划已变化，请重新核对");
        return expected;
    }

    private static ServiceOrder copyWithReceipts(ServiceOrder order, String businessId, String rowId) {
        ServiceOrder copy = new ServiceOrder();
        copy.setProviderType(order.getProviderType());
        copy.setProject(order.getProject());
        copy.setRemoteProductId(order.getRemoteProductId());
        copy.setQuantity(order.getQuantity());
        copy.setCompleted(order.getCompleted());
        copy.setDistance(order.getDistance());
        copy.setScheduleJson(order.getScheduleJson());
        copy.setExternalOrderNo(businessId);
        copy.setExternalSubOrderNo(rowId);
        return copy;
    }

    private static void verifyCreatedFields(JsonNode row, Map<String, Object> body, String project) {
        if ("yyd".equals(project)) {
            for (String key : List.of("school_name", "run_rule_item_id", "zone_name"))
                if (!field(body, "form[" + key + "]").equals(text(row.path(key), 100))) throw invalid();
            return;
        }
        for (String key : List.of("zone_id", "zone_name"))
            if (!field(body, "form[" + key + "]").equals(text(row.path(key), 100))) throw invalid();
        if ("bdlp".equals(project)) {
            for (String key : List.of("school_name", "run_type", "is_auth", "auth_type", "auth_time"))
                if (!field(body, "form[" + key + "]").equals(text(row.path(key), 100))) throw invalid();
        } else {
            for (String key : List.of("min_minute", "max_minute"))
                if (!field(body, "form[" + key + "]").equals(text(row.path(key), 2))) throw invalid();
        }
    }

    @Override
    public RemoteResult sync(ApiProvider provider, ServiceOrder order) {
        JsonNode row = exactOrder(provider, order);
        return result(order, row, tasks(provider, order));
    }

    private static RemoteResult result(ServiceOrder order, JsonNode row, List<RunLog> tasks) {
        int successes = (int) tasks.stream().filter(task -> "已完成".equals(task.status())).count();
        int completed = Math.max(order.getCompleted(), successes);
        String raw = text(row.path("status_display"), 40), pause = text(row.path("pause"), 1);
        if (!Set.of("0", "1").contains(pause)) throw invalid();
        String state = switch (raw) {
            case "正常" -> "0".equals(pause) ? "PAUSED" : "ACTIVE";
            case "全部完成" -> completed == order.getQuantity() ? "COMPLETED" : "ATTENTION";
            case "已退款" -> "REFUND_REVIEW";
            default -> "ATTENTION";
        };
        if ("bdlp".equals(order.getProject()) && !flag(row.path("is_auth"))
                && Set.of("ACTIVE", "PAUSED").contains(state)) state = "ATTENTION";
        return new RemoteResult(order.getExternalOrderNo(), state, completed, null, order.getExternalSubOrderNo());
    }

    @Override
    public RunLogPage logs(ApiProvider provider, ServiceOrder order, int page) {
        if (page < 1 || page > 19) throw bad("记录页码超出范围");
        exactOrder(provider, order);
        return page(tasks(provider, order), page);
    }

    private static RunLogPage page(List<RunLog> rows, int page) {
        int start = Math.min((page - 1) * 20, rows.size()), end = Math.min(start + 20, rows.size());
        return new RunLogPage(List.copyOf(rows.subList(start, end)), page, end < rows.size());
    }

    private List<RunLog> tasks(ApiProvider provider, ServiceOrder order) {
        JsonNode rows = array(call(provider, order.getProject(), "get_task_data",
                Map.of(order.getProject() + "_order_id", order.getExternalOrderNo())).path("data").path("list"), 365);
        if (rows.size() > order.getQuantity()) throw invalid();
        List<RunLog> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            String id = receiptId(row.path("run_task_id"));
            if (!ids.add(id)) throw invalid();
            String start = timestamp(row.path("start_time"));
            JsonNode end = row.path("end_time");
            String endTime = end.isMissingNode() || end.isNull() || end.isTextual() && end.asText().isEmpty() ? null : timestamp(end);
            String raw = text(row.path("status_display"), 40);
            String state = switch (raw) {
                case "未开始" -> "待执行";
                case "成功" -> "已完成";
                case "退款" -> "已退款";
                default -> "需要关注";
            };
            items.add(new RunLog(id, start, state, "未开始".equals(raw), endTime));
        }
        return List.copyOf(items);
    }

    private JsonNode exactOrder(ApiProvider provider, ServiceOrder order) {
        validateOrderShape(order);
        if (order.getExternalOrderNo() == null || !order.getExternalOrderNo().matches("[A-Za-z0-9_-]{1,64}")
                || order.getExternalSubOrderNo() == null || !order.getExternalSubOrderNo().matches("[1-9][0-9]{0,18}"))
            throw bad("订单缺少两个独立编号，请联系管理员核对");
        JsonNode root = call(provider, order.getProject(), "orders", Map.of("type", "1", "keywords", order.getExternalSubOrderNo(),
                "page", "1", "limit", "20"));
        JsonNode rows = array(root.path("data"), 20), pagination = root.path("pagination");
        if (rows.size() != 1 || count(pagination.path("page"), 10000) != 1
                || count(pagination.path("limit"), 1000) != 20 || count(pagination.path("last_page"), 10000) != 1
                || count(pagination.path("total"), 1000000) != 1) throw invalid();
        JsonNode row = rows.get(0);
        if (!order.getExternalSubOrderNo().equals(id(row.path("id")))
                || !order.getExternalOrderNo().equals(receiptId(row.path(order.getProject() + "_order_id")))
                || !provider.getUsername().equals(id(row.path("uid")))
                || !matchesOrderAccount(provider, order, row)
                || count(row.path("num"), 365) != order.getQuantity()
                || decimal(row.path("distance"), 2).compareTo(order.getDistance()) != 0) throw invalid();
        return row;
    }

    private boolean matchesOrderAccount(ApiProvider provider, ServiceOrder order, JsonNode row) {
        ServiceAccountFingerprint fingerprint = binding(order);
        if (!"yyd".equals(order.getProject())) return fingerprint.matches(text(row.path("user"), 64));
        String schoolName = text(row.path("school_name"), 80);
        if (!fingerprint.matchesScoped(SCHOOL_SCOPE, text(row.path("user"), 64), text(row.path("pass"), 128),
                schoolName, id(row.path("run_rule_item_id")), text(row.path("zone_name"), 100))) return false;
        List<PluginSchool> schools = schoolList(provider, schoolName).stream().filter(item -> schoolName.equals(item.name())).toList();
        return schools.size() == 1 && fingerprint.matchesContext(SCHOOL_CONTEXT, schools.get(0).id());
    }

    private static ServiceAccountFingerprint schoolBinding(Map<String, Object> body) {
        return ServiceAccountFingerprint.createScoped(SCHOOL_SCOPE, field(body, "form[number]"), field(body, "form[password]"),
                field(body, "form[school_name]"), field(body, "form[run_rule_item_id]"), field(body, "form[zone_name]"))
                .withContext(SCHOOL_CONTEXT, field(body, "form[school_id]"));
    }

    private static boolean matchesSchoolBody(ServiceAccountFingerprint fingerprint, Map<String, Object> body) {
        return fingerprint.matchesScoped(SCHOOL_SCOPE, field(body, "form[number]"), field(body, "form[password]"),
                field(body, "form[school_name]"), field(body, "form[run_rule_item_id]"), field(body, "form[zone_name]"))
                && fingerprint.matchesContext(SCHOOL_CONTEXT, field(body, "form[school_id]"));
    }

    public static boolean matchesPreparedAccount(String project, Map<String, String> input, PreparedOrder prepared) {
        if (input == null || prepared == null || prepared.accountFingerprint() == null) return false;
        if (!"yyd".equals(project)) return prepared.accountFingerprint().matches(input.get("account"));
        Map<String, Object> body = prepared.fields();
        return body != null && Objects.equals(input.get("account"), body.get("form[number]"))
                && Objects.equals(input.get("password"), body.get("form[password]"))
                && Objects.equals(input.get("schoolId"), body.get("form[school_id]"))
                && Objects.equals(input.get("schoolName"), body.get("form[school_name]"))
                && Objects.equals(input.get("runRuleId"), body.get("form[run_rule_item_id]"))
                && matchesSchoolBody(prepared.accountFingerprint(), body);
    }

    private static ServiceAccountFingerprint binding(ServiceOrder order) {
        try {
            if (order.getScheduleJson() == null || order.getScheduleJson().length() > 500) throw invalid();
            ServiceAccountFingerprint binding = JSON.readValue(order.getScheduleJson(), ServiceAccountFingerprint.class);
            if (binding == null) throw invalid();
            return binding;
        } catch (Exception ex) { throw invalid(); }
    }

    private static void validateOrderShape(ServiceOrder order) {
        if (order == null || !TYPE.equals(order.getProviderType()) || !supported(order.getProject(), order.getRemoteProductId())
                || order.getQuantity() == null || order.getQuantity() < 1 || order.getQuantity() > 365
                || order.getCompleted() == null || order.getCompleted() < 0 || order.getCompleted() > order.getQuantity())
            throw bad("运动订单快照不完整");
        distance(order.getDistance());
    }

    private static void requireMutable(JsonNode row) {
        if (!Set.of("正常", "部分失败").contains(text(row.path("status_display"), 40)))
            throw bad("订单状态需要核对，暂不能修改或退款");
    }

    private static void requireAction(JsonNode row, String action) {
        requireMutable(row);
        String pause = text(row.path("pause"), 1);
        if (!Set.of("0", "1").contains(pause)) throw invalid();
        if ("PAUSE".equals(action) && !"1".equals(pause) || "RESUME".equals(action) && !"0".equals(pause))
            throw bad("运行状态已变化，请刷新后操作");
        if ("DELAY".equals(action) && !"部分失败".equals(text(row.path("status_display"), 40)))
            throw bad("仅部分失败的订单可以批量延期");
    }

    private void requireCost(ApiProvider provider, String project, BigDecimal distance, BigDecimal charge) {
        if (charge == null || charge.compareTo(unitCharge(price(provider, project), project, distance)) < 0)
            throw bad("计划费用已变化，请联系管理员核对价格");
    }

    private JsonNode call(ApiProvider provider, String project, String action, Map<String, Object> values) {
        if (provider == null || !TYPE.equals(provider.getProviderType()) || !supported(project, project)
                || !(COMMON_ACTIONS.contains(action) || "yyd".equals(project) && "get_school_data".equals(action)
                        || Set.of("get_" + project + "_user_info",
                        "get_" + project + "_zone_data", project + "_add").contains(action)))
            throw bad("不支持的运动服务请求");
        if (provider.getUsername() == null || !provider.getUsername().matches("[1-9][0-9]{0,18}")
                || provider.getApiKey() == null || provider.getApiKey().isBlank()
                || provider.getApiKey().length() > 2048 || controls(provider.getApiKey()))
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        Map<String, Object> body = new LinkedHashMap<>(values);
        body.put("login_uid", provider.getUsername());
        body.put("login_key", provider.getApiKey());
        String url = normalizer.normalize(provider.getApiUrl(), TYPE).toASCIIString()
                + "/jingyu/api.php?appId=" + project + "&act=" + action;
        String raw = http.postForString(provider, url, body);
        if (raw == null || raw.length() > 262144 || raw.getBytes(StandardCharsets.UTF_8).length > 262144) throw invalid();
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isObject() || !(root.path("code").isIntegralNumber() || root.path("code").isTextual()))
                throw invalid();
            String code = root.path("code").asText();
            if (code.matches("0|-[1-9][0-9]{0,3}"))
                throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
            if (!"1".equals(code)) throw invalid();
            return root;
        } catch (ProviderRequestException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private static String project(ServiceProduct product) {
        if (product == null || !TYPE.equals(product.getProviderType()) || !supported(product.getProject(), product.getRemoteProductId()))
            throw bad("不支持的运动商品");
        return product.getProject();
    }

    private static String account(Map<String, String> fields, String project) {
        String account = value(fields, "account", "yyd".equals(project) ? 64 : "bdlp".equals(project) ? 19 : 15);
        if ("yyd".equals(project)) {
            if (!account.matches("[A-Za-z0-9_-]{1,64}")) throw bad("请填写有效学号，支持字母、数字、短横线和下划线");
            return account;
        }
        if (!("bdlp".equals(project) ? account.matches("[1-9][0-9]{0,18}") : account.matches("[0-9]{7,15}")))
            throw bad("请填写有效的账号编号或手机号");
        return account;
    }

    private static String password(Map<String, String> fields) {
        String password = fields.get("password");
        // Do not trim or normalize credentials.
        if (password == null || password.isBlank() || password.length() > 128 || controls(password)) throw bad("请填写有效的账号密码");
        return password;
    }

    private static void validateFields(Map<String, String> fields, Set<String> allowed) {
        if (fields == null || !allowed.containsAll(fields.keySet())) throw bad("表单包含不支持的字段");
        fields.forEach((key, value) -> {
            if (value == null || value.length() > 2048 || controls(value)) throw bad("字段格式不正确");
        });
    }

    private static String value(Map<String, String> fields, String key, int max) {
        if (fields == null) throw bad("表单不完整");
        String value = fields.get(key);
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max || controls(value))
            throw bad("请填写完整有效的账号信息与计划");
        return value;
    }

    private static String boundedInput(Map<String, String> fields, String key, int min, int max) {
        String value = value(fields, key, 3);
        if (!value.matches("[1-9][0-9]{0,2}") || Integer.parseInt(value) < min || Integer.parseInt(value) > max)
            throw bad("计划参数超出允许范围");
        return value;
    }

    private static String field(Map<String, Object> fields, String key) {
        Object value = fields == null ? null : fields.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > 2048 || controls(text))
            throw bad("操作快照不完整");
        return text;
    }

    private static void putForm(Map<String, Object> body, String key, String value) { body.put("form[" + key + "]", value); }

    private static void distance(BigDecimal distance) {
        if (distance == null || distance.compareTo(BigDecimal.ONE) < 0 || distance.compareTo(new BigDecimal("100")) > 0
                || distance.stripTrailingZeros().scale() > 1) throw bad("每次距离须为 1–100 公里，最多一位小数");
    }

    private static void validateTimes(List<String> times, int quantity) {
        if (quantity < 1 || quantity > 365 || times == null || times.size() != quantity
                || new HashSet<>(times).size() != times.size()) throw bad("请为每次任务指定不同的执行时间，最多 365 次");
        for (String time : times) futureTime(time);
    }

    private static void futureTime(String value) {
        try {
            if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) throw bad("任务时间格式不正确");
            LocalDateTime time = LocalDateTime.parse(value, STAMP), now = ServiceTime.now();
            if (!time.isAfter(now) || time.isAfter(now.plusYears(1))) throw bad("请选择未来一年内的任务时间（北京时间）");
        } catch (DateTimeException ex) { throw bad("任务时间格式不正确"); }
    }

    private static String timestamp(JsonNode node) {
        String value = text(node, 19);
        try {
            if (!LocalDateTime.parse(value, STAMP).format(STAMP).equals(value)) throw invalid();
        } catch (DateTimeException ex) { throw invalid(); }
        return value;
    }

    private static String text(JsonNode node, int max) {
        if (!(node.isTextual() || node.isIntegralNumber())) throw invalid();
        String value = node.asText();
        if (value.isBlank() || value.length() > max || controls(value)) throw invalid();
        return value;
    }

    private static String id(JsonNode node) {
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

    private static boolean flag(JsonNode node) {
        if (node.isBoolean()) return node.booleanValue();
        String value = text(node, 1);
        if (!Set.of("0", "1").contains(value)) throw invalid();
        return "1".equals(value);
    }

    private static JsonNode array(JsonNode node, int max) {
        if (!node.isArray() || node.size() > max) throw invalid();
        return node;
    }

    private static boolean controls(String value) { return value.codePoints().anyMatch(Character::isISOControl); }
    private static BusinessException bad(String message) { return new BusinessException(message); }
    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
