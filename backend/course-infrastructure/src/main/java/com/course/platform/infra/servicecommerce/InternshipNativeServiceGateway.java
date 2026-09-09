package com.course.platform.infra.servicecommerce;

import com.course.platform.application.service.platform.docking.ProviderConnectionProbe;
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
import java.net.URI;
import java.time.*;
import java.util.*;

/**
 * sxdk_tw direct upstream protocol, not the Qingka session-only wrapper. No inferred
 * prices/retries.
 */
@Component
@RequiredArgsConstructor
public class InternshipNativeServiceGateway
        implements NativeServiceGateway, ProviderConnectionProbe {
    public static final String TYPE = "sxdk_tw";
    public static final Map<String, String> PROJECTS =
            Map.ofEntries(
                    Map.entry("zxjy", "职校家园"),
                    Map.entry("qzt", "黔职通"),
                    Map.entry("gxy", "工学云"),
                    Map.entry("xyb", "校友帮"),
                    Map.entry("xxy", "习迅云 / 宁夏"),
                    Map.entry("xxt", "学习通"),
                    Map.entry("hzj", "慧职教"),
                    Map.entry("gxzy", "广西职业"),
                    Map.entry("jxzhjy", "江西智慧教育"));
    private static final Set<String> FIELDS =
            Set.of(
                    "account",
                    "password",
                    "name",
                    "gwName",
                    "customizedGwName",
                    "schoolId",
                    "school",
                    "schoolName",
                    "projectName",
                    "address",
                    "addressOld",
                    "officialAddress",
                    "jobAddress",
                    "lat",
                    "lng",
                    "country",
                    "province",
                    "city",
                    "area",
                    "adcode",
                    "phone_name",
                    "reason",
                    "desctext",
                    "up_remark",
                    "down_remark",
                    "runMode");
    private static final Set<String> GETS =
            Set.of("getUserInfo", "getOrder", "getLog", "getSchoolList");
    private static final Set<String> POSTS =
            Set.of(
                    "searchPhoneInfo",
                    "addOrder",
                    "editOrder",
                    "delOrder",
                    "changeStatus",
                    "nowCheck",
                    "buPapers",
                    "getSchoolList");
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(24)
                                                    .maxNumberLength(32)
                                                    .maxStringLength(8192)
                                                    .build())
                                    .build())
                    .findAndRegisterModules()
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;

    @Override
    public String getProviderType() {
        return TYPE;
    }

    @Override
    public void testConnection(ApiProvider provider) {
        call(provider, "getUserInfo", Map.of(), false);
    }

    public static boolean supported(String project, String id) {
        return project != null && PROJECTS.containsKey(project) && project.equals(id);
    }

    public static List<String> capabilities() {
        return List.of(
                "LOOKUP",
                "CREATE",
                "SYNC",
                "PAUSE",
                "RESUME",
                "EDIT_SCHEDULE",
                "RUN_NOW",
                "REPORT",
                "REFUND");
    }

    @Override
    public Lookup lookup(ApiProvider p, ServiceProduct product, Map<String, String> fields) {
        validateFields(fields);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platform", project(product.getProject()));
        body.put("phone", required(fields, "account", 100));
        body.put("password", required(fields, "password", 512));
        if ("xyb".equals(product.getProject())) {
            String mode = fields.getOrDefault("runMode", "1");
            if (!Set.of("1", "2", "3").contains(mode)) throw bad("运行方式不支持");
            body.put("runType", Integer.parseInt(mode));
        }
        addSchool(p, product.getProject(), fields, body);
        JsonNode data = call(p, "searchPhoneInfo", body, true);
        if (!data.isObject()) throw invalid();
        JsonNode person = "xyb".equals(product.getProject()) ? data.path("checkOrder") : data;
        if (!person.isObject() || !person.path("name").isTextual()) throw invalid();
        text(person.path("name"), 100);
        Map<String, String> values = new LinkedHashMap<>();
        String type = product.getProject();
        for (String key : FIELDS) {
            if (Set.of("account", "password", "phone_name", "up_remark", "down_remark", "runMode")
                    .contains(key)) continue;
            if (data.hasNonNull(key)
                    && data.path(key).isValueNode()
                    && !data.path(key).asText().isEmpty())
                values.put(key, text(data.path(key), 512));
        }
        if ("qzt".equals(type)) {
            copy(data, values, "longitude", "lng");
            copy(data, values, "latitude", "lat");
            copy(data, values, "address", "officialAddress");
            copy(data, values, "officialAddress", "address");
        }
        if ("gxy".equals(type)) {
            copy(data, values, "jobName", "gwName");
            copy(data, values, "jobProvince", "province");
            copy(data, values, "jobCity", "city");
            copy(data, values, "jobArea", "area");
        }
        if ("xyb".equals(type)) {
            copy(data.path("checkOrder"), values, "name", "name");
            copy(data.path("checkOrder"), values, "gwName", "gwName");
            copy(data.path("checkOrder"), values, "planName", "projectName");
        } else copy(data, values, "planName", "projectName");
        return new Lookup(Map.copyOf(values), List.of(), "已读取账号资料与上游建议；建议不自动修改计划，采用后仍须重新预览服务日期和费用。", null, null, advice(type,data));
    }

    private InternshipAdvice advice(String type,JsonNode data) {
        String in=null,out=null; LocalDate end=null; List<Integer> days=null;
        Boolean daily=null,weekly=null,monthly=null;
        if("qzt".equals(type)){
            in=adviceTime(data,"checkInTime");out=adviceTime(data,"checkOutTime");
            if(data.hasNonNull("endTime")&&!data.path("endTime").asText().isBlank()){
                String raw=text(data.path("endTime"),10);
                try { end=LocalDate.parse(raw); if(!end.toString().equals(raw))throw invalid(); }
                catch(java.time.format.DateTimeParseException e){throw invalid();}
            }
            if(data.hasNonNull("weekList")&&!data.path("weekList").asText().isBlank()){
                String raw=text(data.path("weekList"),13);if(!raw.matches("[1-7](,[1-7]){0,6}"))throw invalid();
                Set<Integer> found=new TreeSet<>();
                for(String d:raw.split(",")){int value=Integer.parseInt(d);if(!found.add(value==1?7:value-1))throw invalid();}
                days=List.copyOf(found);
            }
        }
        if("xxt".equals(type)){
            String first=adviceTime(data,"up_check_time"),last=adviceTime(data,"down_check_time");
            if(first!=null&&last!=null){in=first;out=last;}
        }
        if("xyb".equals(type)){
            var person=data.path("checkOrder");
            daily=adviceFlag(person,"needDailyBlogs",false);weekly=adviceFlag(person,"needWeeklyBlogs",false);monthly=adviceFlag(person,"needMonthlyBlogs",false);
        }else if(Set.of("xxy","xxt","hzj","gxzy","jxzhjy").contains(type)){
            boolean hasTwo=Set.of("hzj","gxzy","jxzhjy").contains(type);
            daily=adviceFlag(data,"day_paper",hasTwo);weekly=adviceFlag(data,"week_paper",hasTwo);monthly=adviceFlag(data,"month_paper",hasTwo);
        }
        if(in==null&&out==null&&end==null&&days==null&&daily==null&&weekly==null&&monthly==null)return null;
        return new InternshipAdvice(in,out,end,days,daily,weekly,monthly);
    }
    private String adviceTime(JsonNode data,String key){
        if(!data.hasNonNull(key)||data.path(key).asText().isEmpty())return null;
        String value=text(data.path(key),8);
        if(!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?"))throw invalid();
        return value.length()==5?value+":00":value;
    }
    private Boolean adviceFlag(JsonNode data,String key,boolean twoMeansTrue){
        if(!data.hasNonNull(key))return null;
        var n=data.get(key);if(n.isBoolean())return n.booleanValue();
        if(!(n.isTextual()||n.isIntegralNumber()))throw invalid();
        String value=n.asText();if("0".equals(value))return false;if("1".equals(value)||(twoMeansTrue&&"2".equals(value)))return true;throw invalid();
    }

    @Override
    public PreparedOrder prepare(ApiProvider p, ServiceProduct product, OrderForm form) {
        if (form == null || !form.authorizedAccount()) throw bad("请确认本人账号及提交授权");
        String platform = project(product.getProject());
        validateSchedule(platform, form.schedule());
        DailyServicePlan plan = DailyServicePlan.create(form.schedule(), today());
        Map<String, Object> body = businessFields(p, platform, form.fields(), true);
        body.putAll(scheduleFields(form.schedule()));
        body.put("platform", platform);
        return new PreparedOrder(
                body,
                plan.paidDates().size(),
                null,
                multiplier(platform, form.schedule()),
                mask(required(form.fields(), "account", 100)),
                plan);
    }

    private Map<String, Object> businessFields(
            ApiProvider p, String platform, Map<String, String> fields, boolean requirePassword) {
        validateFields(fields);
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : FIELDS)
            if (fields.containsKey(key) && !Set.of("account", "runMode").contains(key))
                body.put(key, fields.get(key));
        body.put("phone", required(fields, "account", 100));
        if (requirePassword) body.put("password", required(fields, "password", 512));
        required(fields, "name", 100);
        required(fields, "address", 512);
        coordinate(required(fields, "lat", 16), 90);
        coordinate(required(fields, "lng", 16), 180);
        addSchool(p, platform, fields, body);
        if ("zxjy".equals(platform))
            body.put(
                    "customizedGwName",
                    fields.getOrDefault("customizedGwName", fields.getOrDefault("gwName", "")));
        return body;
    }

    private void addSchool(
            ApiProvider p, String platform, Map<String, String> fields, Map<String, Object> body) {
        if (!Set.of("xxy", "xxt", "hzj").contains(platform)) return;
        String id = fields.getOrDefault("schoolId", "");
        if (id.isBlank() && "xxt".equals(platform))
            return; // documented mobile-account login does not require school
        if (id.isBlank()) throw bad("请先选择学校");
        List<School> matches =
                remoteSchools(
                                p,
                                platform,
                                fields.getOrDefault(
                                        "school", fields.getOrDefault("schoolName", "")))
                        .stream()
                        .filter(s -> id.equals(s.id()))
                        .toList();
        if (matches.size() != 1) throw bad("学校选项已变化，请重新查询并选择");
        School school = matches.get(0);
        body.put("schoolId", school.id());
        body.put("school", school.name());
        body.put("schoolName", school.name());
        if (school.endpoint() != null) body.put("url", school.endpoint());
    }

    @Override
    public PluginSchoolPage schools(
            ApiProvider p, ServiceProduct product, int page, String keyword) {
        if (page < 1 || page > 100 || keyword == null || keyword.length() > 80)
            throw bad("学校查询参数不合法");
        List<School> all = remoteSchools(p, product.getProject(), keyword);
        var filtered =
                all.stream().filter(s -> keyword.isBlank() || s.name().contains(keyword)).toList();
        int offset = (page - 1) * 20;
        List<PluginSchool> items =
                filtered.stream()
                        .skip(offset)
                        .limit(20)
                        .map(s -> new PluginSchool(s.id(), s.name()))
                        .toList();
        return new PluginSchoolPage(items, page, 20, filtered.size() > offset + items.size());
    }

    private record School(String id, String name, String endpoint) {}

    private List<School> remoteSchools(ApiProvider p, String project, String keyword) {
        if (!Set.of("xxy", "xxt", "hzj").contains(project)) throw bad("该实习平台没有学校目录接口");
        JsonNode root =
                request(
                        p,
                        "getSchoolList",
                        "xxt".equals(project)
                                ? Map.of("platform", project, "filter", keyword)
                                : Map.of("platform", project),
                        "xxt".equals(project));
        List<School> schools = new ArrayList<>();
        if ("xxt".equals(project)) {
            if (!root.path("result").isBoolean() || !root.path("result").booleanValue())
                throw invalid();
            for (JsonNode item : array(root.path("froms"), 1000))
                schools.add(
                        new School(id(item.path("schoolid")), text(item.path("name"), 120), null));
        } else if ("xxy".equals(project)) {
            success(root);
            for (JsonNode group : array(root.path("data"), 200))
                for (JsonNode item : array(group.path("schools"), 1000)) {
                    String endpoint = text(item.path("differ_api"), 2048);
                    URI uri = normalizer.normalize(endpoint);
                    if (!"https".equals(uri.getScheme())) throw invalid();
                    schools.add(
                            new School(
                                    id(item.path("school_id")),
                                    text(item.path("school_name"), 120),
                                    uri.toASCIIString()));
                }
        } else {
            if (!"success".equals(root.path("res").asText())
                    || !root.path("data").isObject()
                    || root.path("data").size() > 200) throw invalid();
            for (JsonNode group : root.path("data"))
                for (JsonNode item : array(group, 1000))
                    schools.add(
                            new School(
                                    id(item.path("schoolId")),
                                    text(item.path("schoolName"), 120),
                                    null));
        }
        if (schools.size() > 2000
                || schools.stream().map(School::id).distinct().count() != schools.size())
            throw invalid();
        return schools;
    }

    @Override
    public Lookup orderOptions(ApiProvider p, ServiceOrder order) {
        JsonNode remote = findOrder(p, order);
        DailyServicePlan local = plan(order);
        requireSameCalendar(remote, local.schedule());
        Map<String, String> fields = new LinkedHashMap<>();
        for (String key : FIELDS)
            if (!Set.of("account", "password", "runMode").contains(key)
                    && remote.hasNonNull(key)
                    && !remote.path(key).asText().isEmpty())
                fields.put(key, text(remote.path(key), 512));
        // Account is immutable and remains masked in the form; the full value is read only at the
        // server when editing.
        return new Lookup(
                fields,
                List.of(),
                "编辑仅对新增且未购买的日期计费；删减日期不自动退款。运行方式不可更换，空密码表示保留现有凭据。",
                local.schedule(),
                local.paidDates());
    }

    @Override
    public PreparedAction prepareScheduledAction(
            ApiProvider p, ServiceProduct product, ServiceOrder order, ActionForm form) {
        DailyServicePlan current = plan(order);
        if ("EDIT_SCHEDULE".equals(form.action())) {
            validateSchedule(order.getProject(), form.schedule());
            JsonNode remote = findOrder(p, order);
            requireSameCalendar(remote, current.schedule());
            validateFields(form.fields());
            if (form.fields().containsKey("account")) throw bad("已有实习订单不能修改账号");
            Map<String, String> fields = new LinkedHashMap<>();
            for (String key : FIELDS)
                if (remote.hasNonNull(key) && remote.path(key).isValueNode())
                    fields.put(key, textOrEmpty(remote.path(key), 2048));
            fields.put("account", text(remote.path("phone"), 100));
            for (var entry : form.fields().entrySet())
                if (!"password".equals(entry.getKey()) || !entry.getValue().isBlank())
                    fields.put(entry.getKey(), entry.getValue());
            Map<String, Object> body = businessFields(p, order.getProject(), fields, true);
            body.putAll(scheduleFields(form.schedule()));
            body.put("platform", order.getProject());
            DailyServicePlan next = current.revise(form.schedule(), today());
            return new PreparedAction(
                    body, next.additionalDays(current), order.getUnitCharge(), next);
        }
        if (form.schedule() != null) throw bad("此操作不接受计划参数");
        if ("REPORT".equals(form.action())) {
            Map<String, String> f = form.fields();
            if (f == null || !f.keySet().equals(Set.of("startDate", "endDate", "reportType")))
                throw bad("补交日期和类型不完整");
            LocalDate first = date(f.get("startDate")), last = date(f.get("endDate"));
            if (first.isBefore(current.startDate())
                    || last.isAfter(today())
                    || last.isAfter(current.schedule().endDate())
                    || first.isAfter(last)
                    || first.plusDays(DailyServicePlan.MAX_CALENDAR_DAYS - 1).isBefore(last))
                throw bad("补交范围须在已购买周期内且不能晚于今天");
            Set<LocalDate> paid = new HashSet<>(current.paidDates());
            if (!first.datesUntil(last.plusDays(1)).allMatch(paid::contains))
                throw bad("补交范围包含未购买的服务日，请按连续已购日期分段提交");
            Set<String> types = new HashSet<>(Set.of("周报", "月报"));
            if (!"qzt".equals(order.getProject())) types.add("日报");
            if ("gxy".equals(order.getProject())) types.addAll(Set.of("上班打卡", "下班打卡", "上下班打卡"));
            if (!types.contains(f.get("reportType"))) throw bad("补交类型不支持");
            return new PreparedAction(
                    Map.of(
                            "startTime",
                            first.toString(),
                            "endTime",
                            last.toString(),
                            "levelName",
                            f.get("reportType")),
                    0,
                    order.getUnitCharge(),
                    null);
        }
        if (form.fields() != null && !form.fields().isEmpty()) throw bad("此操作不接受额外参数");
        if ("RUN_NOW".equals(form.action())) {
            if (current.schedule().endDate().isBefore(today())) throw bad("计划已到期，请先续期");
            return new PreparedAction(
                    Map.of(),
                    1,
                    order.getUnitCharge()
                            .divide(multiplier(order.getProject(), current.schedule())),
                    null);
        }
        if (!Set.of("REFUND", "PAUSE", "RESUME").contains(form.action())) throw bad("实习服务不支持此操作");
        return new PreparedAction(Map.of(), 0, order.getUnitCharge(), null);
    }

    @Override
    public int refundRemaining(ApiProvider p, ServiceOrder order) {
        requireSameCalendar(findOrder(p, order), plan(order).schedule());
        return plan(order).refundableDays(today());
    }

    @Override
    public void checkAddTimes(ApiProvider p, ServiceOrder order, int quantity) {
        throw bad("实习服务请通过编辑周期续期");
    }

    @Override
    public RemoteResult execute(
            ApiProvider p,
            ServiceProduct product,
            ServiceOrder order,
            String action,
            Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>(fields);
        String act;
        if ("CREATE".equals(action)) act = "addOrder";
        else {
            body.put("id", order.getExternalOrderNo());
            act =
                    switch (action) {
                        case "EDIT_SCHEDULE" -> "editOrder";
                        case "REFUND" -> "delOrder";
                        case "PAUSE", "RESUME" -> {
                            body.put("code", "PAUSE".equals(action) ? 2 : 1);
                            yield "changeStatus";
                        }
                        case "RUN_NOW" -> "nowCheck";
                        case "REPORT" -> "buPapers";
                        default -> throw bad("未知实习业务操作");
                    };
        }
        JsonNode data = call(p, act, body, true);
        if ("CREATE".equals(action)) {
            String external =
                    data.hasNonNull("upstreamOrderId")
                            ? id(data.path("upstreamOrderId"))
                            : id(data.path("sourceOrderId"));
            if ("0".equals(external)) throw invalid();
            return new RemoteResult(external, "ACTIVE", 0, null);
        }
        // This is the published local unused-service-day refund policy, not an invented supplier
        // refund_count.
        return new RemoteResult(
                order.getExternalOrderNo(),
                "REFUND".equals(action) ? "REFUNDED" : "PAUSE".equals(action) ? "PAUSED" : "ACTIVE",
                null,
                "REFUND".equals(action) ? plan(order).refundableDays(today()) : null);
    }

    @Override
    public RemoteResult sync(ApiProvider p, ServiceOrder order) {
        JsonNode remote = findOrder(p, order);
        DailyServicePlan plan = plan(order);
        requireSameCalendar(remote, plan.schedule());
        String code = text(remote.path("code"), 8);
        String status =
                plan.schedule().endDate().isBefore(today())
                        ? "COMPLETED"
                        : "1".equals(code) ? "ACTIVE" : "2".equals(code) ? "PAUSED" : "ATTENTION";
        return new RemoteResult(order.getExternalOrderNo(), status, 0, null);
    }

    @Override
    public RunLogPage logs(ApiProvider p, ServiceOrder order, int page) {
        if (page != 1) throw bad("该上游仅提供最近十条记录");
        JsonNode rows =
                array(call(p, "getLog", Map.of("id", order.getExternalOrderNo()), false), 10);
        List<RunLog> logs = new ArrayList<>();
        int index = 0;
        for (JsonNode row : rows) {
            String time = text(row.path("logTime"), 40);
            if (!time.matches("[0-9TtZz:+. -]{10,40}")) throw invalid();
            // Raw logText can contain passwords, session tokens or HTML. Never echo it as a user
            // message.
            String status =
                    switch (row.path("logType").asText()) {
                        case "打卡", "上班打卡", "下班打卡" -> "考勤执行记录";
                        case "日报", "周报", "月报" -> "报告执行记录";
                        default -> "上游执行记录";
                    };
            logs.add(new RunLog(Integer.toString(++index), time, status));
        }
        return new RunLogPage(logs, 1, false);
    }

    private JsonNode findOrder(ApiProvider p, ServiceOrder order) {
        long start = System.nanoTime();
        for (int page = 1; page <= 10; page++) {
            if (page > 1 && System.nanoTime() - start > 5_000_000_000L) break;
            JsonNode rows =
                    array(call(p, "getOrder", Map.of("page", page, "pagesize", 100), false), 100);
            JsonNode match = null;
            for (JsonNode row : rows)
                if (order.getExternalOrderNo().equals(row.path("id").asText())) {
                    if (match != null || !order.getProject().equals(row.path("platform").asText()))
                        throw invalid();
                    match = row;
                }
            if (match != null) return match;
            if (rows.size() < 100) break;
        }
        throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }

    private void requireSameCalendar(JsonNode row, InternshipSchedule s) {
        if (!s.endDate().toString().equals(row.path("end_time").asText())
                || !upstreamWeekdays(s).equals(normalizedWeekdays(row.path("check_week"))))
            throw bad("上游周期与本平台购买记录不一致，请联系管理员核对，不能按不一致周期扣款或退款");
        if (row.hasNonNull("runType") && count(row.path("runType")) != s.runMode())
            throw bad("上游运行方式已变化，请核对价格");
    }

    private String normalizedWeekdays(JsonNode value) {
        String days = text(value, 20);
        if (!days.matches("[0-6](?:,[0-6]){0,6}")) throw invalid();
        String[] values = days.split(",");
        if (new HashSet<>(Arrays.asList(values)).size() != values.length) throw invalid();
        Arrays.sort(values);
        return String.join(",", values);
    }

    private static String upstreamWeekdays(InternshipSchedule s) {
        return s.weekdays().stream()
                .sorted()
                .map(d -> Integer.toString(d - 1))
                .collect(java.util.stream.Collectors.joining(","));
    }

    public static DailyServicePlan plan(ServiceOrder order) {
        try {
            if (order.getScheduleJson() == null || order.getScheduleJson().length() > 150000)
                throw invalid();
            DailyServicePlan value =
                    JSON.readValue(order.getScheduleJson(), DailyServicePlan.class);
            if (value == null
                    || value.schedule() == null
                    || value.paidDates() == null
                    || value.paidDates().size() > 9999) throw invalid();
            return value;
        } catch (Exception ex) {
            throw bad("本地实习计划快照不可用，请联系管理员核对");
        }
    }

    public static BigDecimal multiplier(String platform, InternshipSchedule s) {
        return "xyb".equals(platform) && s.runMode() == 3 ? new BigDecimal("5") : BigDecimal.ONE;
    }

    private void validateSchedule(String platform, InternshipSchedule s) {
        DailyServicePlan.validate(s, today());
        if ("qzt".equals(platform) && s.dailyReport()) throw bad("黔职通不支持日报");
        if (!"xyb".equals(platform) && s.runMode() != 1) throw bad("该实习平台不支持所选运行方式");
        if (Set.of("qzt", "gxy").contains(platform)
                && (s.checkOutTime() == null || s.checkOutTime().isBlank()))
            throw bad("该平台必须设置下班时间");
    }

    private Map<String, Object> scheduleFields(InternshipSchedule s) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("end_time", s.endDate().toString());
        values.put("check_week", upstreamWeekdays(s));
        values.put("check_time", seconds(s.checkInTime()));
        values.put("up_check_time", seconds(s.checkInTime()));
        values.put("down_check_time", seconds(s.checkOutTime()));
        values.put("runType", s.runMode());
        values.put("day_paper", s.dailyReport() ? "true" : "false");
        values.put("week_paper", s.weeklyReport() ? "true" : "false");
        values.put("month_paper", s.monthlyReport() ? "true" : "false");
        values.put("holiday", s.skipHolidays() ? "true" : "false");
        values.put("randomLocation", s.randomLocation() ? "true" : "false");
        values.put("weekPaperSubmitWeek", s.weeklyReportDay());
        values.put("monthPaperSubmitMonth", s.monthlyReportDay());
        values.put("paper_router", 2);
        values.put("payType", 0);
        Map<String, Object> limits = new LinkedHashMap<>();
        var r = s.reportLengths();
        for (var entry :
                Map.of(
                                "day",
                                r == null || r.day() == null
                                        ? new InternshipSchedule.WordRange(0, 0)
                                        : r.day(),
                                "week",
                                r == null || r.week() == null
                                        ? new InternshipSchedule.WordRange(0, 0)
                                        : r.week(),
                                "month",
                                r == null || r.month() == null
                                        ? new InternshipSchedule.WordRange(0, 0)
                                        : r.month(),
                                "summary",
                                r == null || r.summary() == null
                                        ? new InternshipSchedule.WordRange(0, 0)
                                        : r.summary())
                        .entrySet()) limits.put(entry.getKey(), entry.getValue());
        try {
            values.put("paperNumSetting", JSON.writeValueAsString(limits));
        } catch (Exception ex) {
            throw bad("报告设置格式错误");
        }
        return values;
    }

    private JsonNode call(ApiProvider p, String action, Map<String, Object> body, boolean post) {
        JsonNode root = request(p, action, body, post);
        success(root);
        return root.path("data");
    }

    private JsonNode request(
            ApiProvider p, String action, Map<String, Object> supplied, boolean post) {
        if (p == null
                || !TYPE.equals(p.getProviderType())
                || !(post ? POSTS : GETS).contains(action)) throw bad("不支持的实习协议请求");
        if (p.getUsername() == null
                || p.getUsername().isBlank()
                || p.getApiKey() == null
                || p.getApiKey().isBlank()) throw invalid();
        Map<String, Object> body = new LinkedHashMap<>(supplied);
        body.put("act", action);
        body.put("uid", p.getUsername());
        body.put("key", p.getApiKey());
        String endpoint = normalizer.normalize(p.getApiUrl(), TYPE).toASCIIString();
        String response =
                post ? http.postForString(p, endpoint, body) : http.getForString(p, endpoint, body);
        if (response == null || response.length() > 524288) throw invalid();
        try {
            JsonNode root = JSON.readTree(response);
            if (root == null || !root.isObject()) throw invalid();
            return root;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static void success(JsonNode root) {
        JsonNode code = root.path("code");
        if (!(code.isIntegralNumber() || code.isTextual())
                || !code.asText().matches("-?[0-9]{1,4}")) throw invalid();
        if (!"0".equals(code.asText()))
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
    }

    private static JsonNode array(JsonNode v, int max) {
        if (!v.isArray() || v.size() > max) throw invalid();
        return v;
    }

    private static void validateFields(Map<String, String> fields) {
        if (fields == null || fields.size() > 64 || !FIELDS.containsAll(fields.keySet()))
            throw bad("实习表单包含不支持的字段");
        fields.forEach(
                (key, value) -> {
                    if (value == null
                            || value.length() > 2048
                            || value.codePoints().anyMatch(Character::isISOControl))
                        throw bad("实习字段格式错误");
                });
    }

    private static String required(Map<String, String> fields, String key, int max) {
        String v = fields.get(key);
        if (v == null || v.isBlank() || v.length() > max) throw bad("缺少或无效字段：" + key);
        return v;
    }

    private static String text(JsonNode v, int max) {
        String value = textOrEmpty(v, max);
        if (value.isBlank()) throw invalid();
        return value;
    }

    private static String textOrEmpty(JsonNode v, int max) {
        if (!(v.isTextual() || v.isNumber() || v.isBoolean())
                || v.asText().length() > max
                || v.asText().codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return v.asText();
    }

    private static void copy(JsonNode data, Map<String, String> target, String from, String to) {
        if (data.hasNonNull(from) && !data.path(from).asText().isEmpty())
            target.put(to, text(data.path(from), 512));
    }

    private static String id(JsonNode v) {
        String id = text(v, 64);
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return id;
    }

    private static int count(JsonNode v) {
        String s = text(v, 5);
        if (!s.matches("[0-9]{1,4}")) throw invalid();
        return Integer.parseInt(s);
    }

    private static void coordinate(String v, int max) {
        if (!v.matches("-?[0-9]{1,3}(?:\\.[0-9]{1,8})?")
                || new BigDecimal(v).abs().compareTo(BigDecimal.valueOf(max)) > 0)
            throw bad("经纬度不合法");
    }

    private static String project(String p) {
        if (p == null || !PROJECTS.containsKey(p)) throw bad("未知实习平台");
        return p;
    }

    private static LocalDate date(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception ex) {
            throw bad("日期格式错误");
        }
    }

    private static String seconds(String s) {
        return s == null || s.isEmpty() ? "" : s.length() == 5 ? s + ":00" : s;
    }

    private static LocalDate today() {
        return ServiceTime.now().toLocalDate();
    }

    private static String mask(String s) {
        return s.length() < 5 ? "授权账号" : s.substring(0, 2) + "***" + s.substring(s.length() - 2);
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }

    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
