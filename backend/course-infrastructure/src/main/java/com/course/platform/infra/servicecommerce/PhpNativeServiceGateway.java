package com.course.platform.infra.servicecommerce;

import com.course.platform.application.service.servicecommerce.NativeServiceGateway;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** Native, fixed-action adapters. No upstream PHP is executed and no raw response is exposed. */
@Component
@RequiredArgsConstructor
public class PhpNativeServiceGateway
        implements NativeServiceGateway,
                com.course.platform.application.service.servicecommerce.ServiceAccountGateway {
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;
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
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final DateTimeFormatter TASK_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> INPUTS =
            Set.of(
                    "account",
                    "password",
                    "schoolName",
                    "studentName",
                    "studentAccount",
                    "studentId",
                    "zoneId",
                    "fenceId",
                    "runType",
                    "runRuleId",
                    "planOptionId",
                    "fenceOptionId",
                    "runTime",
                    "message",
                    "repair");

    public static boolean supported(String type, String project, String productId) {
        return switch (type == null ? "" : type) {
            case "sxdk_tw" -> InternshipNativeServiceGateway.supported(project, productId);
            case SsbenzDistanceGateway.TYPE -> SsbenzDistanceGateway.supported(project, productId);
            case "wuxin" -> "sdxy".equals(project) && "sdxy".equals(productId);
            case "jiguang" -> "default".equals(project) && Set.of("1", "2").contains(productId);
            case "heisha" ->
                    "default".equals(project) && Set.of("1", "2", "3", "4").contains(productId);
            case "flash" ->
                    Set.of("sdxy", "ydsjxy", "xbd").contains(project) && project.equals(productId);
            default -> false;
        };
    }

    public static List<String> capabilities(String type) {
        return switch (type) {
            case "sxdk_tw" -> InternshipNativeServiceGateway.capabilities();
            case SsbenzDistanceGateway.TYPE -> List.of("CREATE", "SYNC");
            case "wuxin" ->
                    List.of(
                            "LOOKUP",
                            "CREATE",
                            "SYNC",
                            "REFUND",
                            "ADD_TIMES",
                            "EDIT_PLAN",
                            "REASSIGN");
            case "jiguang" -> List.of("CREATE", "SYNC", "REFUND", "ADD_TIMES");
            case "flash" ->
                    List.of(
                            "LOOKUP",
                            "CREATE",
                            "SYNC",
                            "PAUSE",
                            "RESUME",
                            "DELAY",
                            "DELAY_TASK",
                            "REFUND",
                            "CHANGE_TIME");
            case "heisha" -> List.of("LOOKUP", "CREATE", "SYNC");
            default -> List.of();
        };
    }

    @Override
    public Lookup lookup(ApiProvider provider, ServiceProduct p, Map<String, String> fields) {
        validateFields(fields);
        if ("jiguang".equals(p.getProviderType()))
            return new Lookup(Map.of(), List.of(), "填写学校、姓名和学号即可预览订单");
        Lookup result = lookupView(p, lookupData(provider, p, fields));
        return publicLookup(result);
    }

    private JsonNode lookupData(
            ApiProvider provider, ServiceProduct p, Map<String, String> fields) {
        String account = required(fields, "account", 100);
        String password = required(fields, "password", 200);
        if ("heisha".equals(p.getProviderType())) {
            return call(
                    provider,
                    p.getProject(),
                    "preflight",
                    Map.of("phone", account, "password", password));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("form[" + ("ydsjxy".equals(p.getProject()) ? "uk" : "phone") + "]", account);
        body.put("form[password]", password);
        if ("sdxy".equals(p.getProject()))
            body.put("form[school_name]", required(fields, "schoolName", 120));
        return call(provider, p.getProject(), p.getProject() + "_get_user_info_by_password", body);
    }

    private Lookup lookupView(ServiceProduct p, JsonNode data) {
        Map<String, String> suggested = new LinkedHashMap<>();
        List<Choice> choices = new ArrayList<>();
        if ("heisha".equals(p.getProviderType())) {
            suggested.put("schoolName", scalar(data.path("account").path("schoolName"), 120));
            choices(choices, "planOptionId", data.path("plans"), "planOptionId", "runName");
            choices(choices, "fenceOptionId", data.path("fences"), "fenceOptionId", "fenceName");
        } else {
            JsonNode student = data.path("student");
            suggested.put(
                    "studentId",
                    scalar(
                            student.path("ydsjxy".equals(p.getProject()) ? "uid" : "student_id"),
                            100));
            if ("sdxy".equals(p.getProject())) {
                choices(choices, "zoneId", data.path("zone_list"), "zone_id", "name");
                choices(choices, "runRuleId", student.path("run_rule_lst"), "run_rule_id", "label");
                choices.add(new Choice("runType", "SUN", "日常跑"));
                choices.add(new Choice("runType", "FREE", "自由跑"));
            } else if ("ydsjxy".equals(p.getProject())) {
                JsonNode rules = student.path("rule_args");
                addTypeChoices(choices, rules);
                for (String key : List.of("default_zone", "free_zone")) {
                    JsonNode zone = student.path(key);
                    JsonNode id = student.path(key + "_id");
                    if (!id.isMissingNode() && !id.isNull())
                        choices.add(
                                new Choice(
                                        "zoneId", scalar(id, 64), scalar(zone.path("name"), 120)));
                }
            } else {
                addTypeChoices(choices, student.path("rule"));
                choices(choices, "fenceId", student.path("fences"), "fence_id", "name");
            }
        }
        choices = choices.stream().distinct().toList();
        if (choices.isEmpty()) throw invalid();
        for (Choice choice : choices) suggested.putIfAbsent(choice.field(), choice.value());
        return new Lookup(suggested, choices, "账号查询仅用于当前服务；密码与上游令牌不回传、不写入浏览器存储");
    }

    private static Lookup publicLookup(Lookup lookup) {
        Map<String, String> fields = new LinkedHashMap<>(lookup.suggested());
        fields.remove("studentId");
        return new Lookup(Map.copyOf(fields), lookup.choices(), lookup.notice());
    }

    @Override
    public void sendCode(ApiProvider provider, ServiceProduct product, String account) {
        if (!"flash".equals(product.getProviderType()) || !"sdxy".equals(product.getProject()))
            throw bad("该服务没有短信授权接口");
        if (account == null || !account.matches("1[0-9]{10}")) throw bad("请输入有效手机号");
        call(provider, "sdxy", "sdxy_send_code", Map.of("form[phone]", account));
    }

    @Override
    public AccountSnapshot authenticate(
            ApiProvider provider,
            ServiceProduct product,
            String mode,
            String account,
            String secret,
            String schoolName) {
        if (isHeishaFace(product)) {
            if (!"PASSWORD".equals(mode)
                    || !"heisha".equals(provider.getProviderType())
                    || account == null
                    || !account.matches("1[0-9]{10}")
                    || secret == null
                    || secret.isBlank()
                    || secret.length() > 200
                    || secret.codePoints().anyMatch(Character::isISOControl))
                throw bad("人脸商品需要本人手机号与密码预检");
            JsonNode data =
                    lookupData(provider, product, Map.of("account", account, "password", secret));
            if (data.path("account").has("phone")
                    && !account.equals(scalar(data.path("account").get("phone"), 100)))
                throw invalid();
            // Freeze only the fields needed by the documented add contract, not the raw account
            // response.
            var frozen = JSON.createObjectNode();
            frozen.put("runPreflightToken", scalar(data.path("runPreflightToken"), 2048));
            frozen.put("checkedAt", scalar(data.path("checkedAt"), 100));
            frozen.set("plans", freezeHeishaPlans(data.path("plans")));
            frozen.set("fences", freezeHeishaFences(data.path("fences")));
            Lookup lookup = lookupView(product, data);
            var suggested = new LinkedHashMap<>(lookup.suggested());
            String school = suggested.remove("schoolName");
            return new AccountSnapshot(
                    account,
                    secret,
                    school,
                    null,
                    new Lookup(
                            Map.copyOf(suggested),
                            lookup.choices(),
                            "账号与计划已绑定；人脸凭据仅在服务器保管，照片由官方采集站点处理。"),
                    new HeishaAccount(frozen.toString(), null, null, null, null, null));
        }
        if (!"flash".equals(product.getProviderType())) throw bad("该服务不支持临时账号授权");
        JsonNode data;
        if ("SMS".equals(mode)) {
            if (!"sdxy".equals(product.getProject())
                    || !account.matches("1[0-9]{10}")
                    || secret == null
                    || !secret.matches("[0-9]{4,8}")) throw bad("短信授权参数不合法");
            data =
                    call(
                            provider,
                            "sdxy",
                            "sdxy_get_user_info_by_code",
                            Map.of("form[phone]", account, "form[code]", secret));
        } else if ("PASSWORD".equals(mode)) {
            data =
                    lookupData(
                            provider,
                            product,
                            Map.of(
                                    "account",
                                    account,
                                    "password",
                                    secret,
                                    "schoolName",
                                    schoolName));
        } else throw bad("授权方式不支持");
        Lookup lookup = lookupView(product, data);
        String password = "PASSWORD".equals(mode) ? secret : "";
        if ("SMS".equals(mode) && data.path("student").hasNonNull("password")) {
            JsonNode supplied = data.path("student").path("password");
            if (!supplied.isTextual()
                    || supplied.asText().length() > 200
                    || supplied.asText().codePoints().anyMatch(Character::isISOControl))
                throw invalid();
            password = supplied.asText();
        }
        return new AccountSnapshot(
                account,
                password,
                schoolName,
                lookup.suggested().get("studentId"),
                publicLookup(lookup));
    }

    @Override
    public AccountSnapshot refreshRules(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account) {
        if (!"flash".equals(product.getProviderType())
                || !Set.of("sdxy", "xbd").contains(product.getProject()))
            throw bad("该项目没有独立规则刷新接口");
        if (account.studentId() == null || account.studentId().isBlank()) throw bad("请先完成账号授权");
        call(
                provider,
                product.getProject(),
                product.getProject() + "_update_run_rule",
                Map.of("student_id", account.studentId()));
        // The acknowledgement contains no new rules. Never advertise the old choices as refreshed.
        if (account.password() == null
                || account.password().isBlank()
                || ("sdxy".equals(product.getProject()) && account.schoolName().isBlank()))
            return null;
        return authenticate(
                provider,
                product,
                "PASSWORD",
                account.account(),
                account.password(),
                account.schoolName());
    }

    public static boolean isHeishaFace(ServiceProduct product) {
        return product != null
                && "heisha".equals(product.getProviderType())
                && "default".equals(product.getProject())
                && Set.of("3", "4").contains(product.getRemoteProductId());
    }

    @Override
    public AccountSnapshot collectFace(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account) {
        requireHeishaAccount(provider, product, account);
        if (account.heisha().faceToken() != null) throw bad("采集链接已获取，不能重复创建");
        JsonNode data =
                call(
                        provider,
                        product.getProject(),
                        "collect_link",
                        Map.of("phone", account.account()));
        checkHeishaIdentity(data, account);
        return account.withHeisha(
                account.heisha()
                        .collected(
                                scalar(data.path("faceToken"), 2048),
                                scalar(data.path("collectUrl"), 4096),
                                faceStatus(data)));
    }

    @Override
    public AccountSnapshot checkFace(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account) {
        requireHeishaAccount(provider, product, account);
        if (account.heisha().faceToken() == null) throw bad("请先获取官方采集链接");
        JsonNode data =
                call(
                        provider,
                        product.getProject(),
                        "face_check",
                        Map.of(
                                "phone",
                                account.account(),
                                "face_token",
                                account.heisha().faceToken()));
        checkHeishaIdentity(data, account);
        if (data.has("faceToken")
                && !account.heisha().faceToken().equals(scalar(data.get("faceToken"), 2048)))
            throw invalid();
        // A check cannot replace the original collection URL or the server-bound token.
        return account.withHeisha(account.heisha().checked(faceStatus(data)));
    }

    private static void requireHeishaAccount(
            ApiProvider provider, ServiceProduct product, AccountSnapshot account) {
        if (!isHeishaFace(product)
                || !"heisha".equals(provider.getProviderType())
                || account == null
                || account.heisha() == null
                || account.lookup() == null) throw bad("该会话不支持官方人脸采集");
    }

    private static void checkHeishaIdentity(JsonNode data, AccountSnapshot account) {
        if (data.has("phone") && !account.account().equals(scalar(data.get("phone"), 100)))
            throw invalid();
    }

    private static FaceStatus faceStatus(JsonNode data) {
        JsonNode status = data.path("batchStatus");
        if (!status.path("completed").isBoolean()) throw invalid();
        int files = count(status.path("fileCount")),
                minimum = count(status.path("minFileCount")),
                maximum = count(status.path("maxFileCount"));
        boolean completed = status.get("completed").booleanValue();
        if (minimum < 1
                || maximum < minimum
                || maximum > 1000
                || files > maximum
                || (completed && files < minimum)) throw invalid();
        return new FaceStatus(completed, files, minimum, maximum);
    }

    private static JsonNode freezeHeishaPlans(JsonNode rows) {
        if (!rows.isArray() || rows.isEmpty() || rows.size() > 200) throw invalid();
        var plans = JSON.createArrayNode();
        var ids = new HashSet<String>();
        for (JsonNode row : rows) {
            String id = scalar(row.path("planOptionId"), 100);
            if (!ids.add(id)) throw invalid();
            var plan = plans.addObject();
            plan.put("planOptionId", id);
            plan.put("runName", scalar(row.path("runName"), 120));
            var min = decimal(row.path("singleMinDistanceKm"));
            var max = decimal(row.path("singleMaxDistanceKm"));
            if (min.signum() <= 0 || max.compareTo(min) < 0) throw invalid();
            plan.put("singleMinDistanceKm", min);
            plan.put("singleMaxDistanceKm", max);
            JsonNode fragments = row.path("timeFragments");
            if (!fragments.isArray()
                    || fragments.size() > 40
                    || fragments.toString().length() > 4000) throw invalid();
            plan.set("timeFragments", fragments);
        }
        if (plans.toString().length() > 100000) throw invalid();
        return plans;
    }

    private static JsonNode freezeHeishaFences(JsonNode rows) {
        if (!rows.isArray() || rows.isEmpty() || rows.size() > 200) throw invalid();
        var fences = JSON.createArrayNode();
        var ids = new HashSet<String>();
        for (JsonNode row : rows) {
            String id = scalar(row.path("fenceOptionId"), 100);
            if (!ids.add(id)) throw invalid();
            var fence = fences.addObject();
            fence.put("fenceOptionId", id);
            fence.put("fenceName", scalar(row.path("fenceName"), 120));
        }
        return fences;
    }

    @Override
    public PreparedOrder prepare(ApiProvider provider, ServiceProduct p, OrderForm form) {
        return prepareInternal(provider, p, form, null);
    }

    @Override
    public PreparedOrder prepareAuthenticated(
            ApiProvider provider, ServiceProduct p, OrderForm form, AccountSnapshot account) {
        if ((!"flash".equals(p.getProviderType()) && !isHeishaFace(p))
                || account == null
                || account.lookup() == null) throw bad("当前服务不支持此授权会话");
        if (form.fields().keySet().stream()
                .anyMatch(Set.of("account", "password", "schoolName", "studentId")::contains))
            throw bad("授权会话的账号信息不能由下单参数覆盖");
        return prepareInternal(provider, p, form, account);
    }

    private PreparedOrder prepareInternal(
            ApiProvider provider,
            ServiceProduct p,
            OrderForm form,
            AccountSnapshot accountSession) {
        validateFields(form.fields());
        if (isHeishaFace(p)
                && (accountSession == null
                        || accountSession.heisha() == null
                        || accountSession.heisha().faceToken() == null
                        || accountSession.heisha().status() == null
                        || !accountSession.heisha().status().completed()))
            throw bad("人脸商品必须使用已完成官方采集检查的账号授权会话");
        if (!form.authorizedAccount()) throw bad("请确认有权使用所填写的账号及个人信息");
        if (form.quantity() < 1
                || form.quantity() > 365
                || form.distance() == null
                || form.distance().compareTo(new BigDecimal("0.1")) < 0
                || form.distance().compareTo(new BigDecimal("50")) > 0
                || form.distance().stripTrailingZeros().scale() > 2) throw bad("次数或距离不合法");
        if (!supported(p.getProviderType(), p.getProject(), p.getRemoteProductId()))
            throw bad("该商品尚未支持原生下单");
        Map<String, String> f = form.fields();
        Map<String, Object> body = new LinkedHashMap<>();
        String account;
        BigDecimal units = form.distance();
        if ("jiguang".equals(p.getProviderType())) {
            if (!Set.of("1", "1.2", "1.5", "1.6", "2", "3", "5", "10")
                    .contains(form.distance().stripTrailingZeros().toPlainString())) {
                throw bad("该商品仅支持 1 / 1.2 / 1.5 / 1.6 / 2 / 3 / 5 / 10 公里");
            }
            account = required(f, "studentAccount", 100);
            body.put("school_name", required(f, "schoolName", 120));
            body.put("student_name", required(f, "studentName", 80));
            body.put("student_account", account);
            body.put("customer_message", optional(f, "message", 500));
        } else {
            account =
                    accountSession == null ? required(f, "account", 100) : accountSession.account();
            JsonNode preflight = accountSession == null ? lookupData(provider, p, f) : null;
            if (isHeishaFace(p)) {
                try {
                    preflight = JSON.readTree(accountSession.heisha().preflightJson());
                } catch (Exception ex) {
                    throw invalid();
                }
                if (preflight == null || !preflight.isObject()) throw invalid();
            }
            Lookup options =
                    accountSession == null ? lookupView(p, preflight) : accountSession.lookup();
            if ("heisha".equals(p.getProviderType())) {
                String planId = selected(options, f, "planOptionId");
                String fenceId = selected(options, f, "fenceOptionId");
                JsonNode plan = find(preflight.path("plans"), "planOptionId", planId);
                JsonNode fence = find(preflight.path("fences"), "fenceOptionId", fenceId);
                BigDecimal minimum = decimal(plan.path("singleMinDistanceKm"));
                BigDecimal maximum = decimal(plan.path("singleMaxDistanceKm"));
                if (units.compareTo(minimum) < 0 || units.compareTo(maximum) > 0)
                    throw bad("距离超出所选计划限制");
                String time = required(f, "runTime", 5);
                if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw bad("时间须为 HH:mm");
                body.put("phone", account);
                body.put(
                        "password",
                        accountSession == null
                                ? required(f, "password", 200)
                                : accountSession.password());
                if (isHeishaFace(p)) body.put("face_token", accountSession.heisha().faceToken());
                body.put("plan_option_id", planId);
                body.put("fence_option_id", fenceId);
                body.put("run_time", time);
                body.put(
                        "school_name",
                        accountSession == null
                                ? options.suggested().get("schoolName")
                                : accountSession.schoolName());
                body.put("plan_name", scalar(plan.path("runName"), 120));
                body.put("fence_name", scalar(fence.path("fenceName"), 120));
                body.put("run_preflight_token", scalar(preflight.path("runPreflightToken"), 2048));
                body.put("checked_at", scalar(preflight.path("checkedAt"), 100));
                body.put("single_min_distance_km", minimum.toPlainString());
                body.put("single_max_distance_km", maximum.toPlainString());
                JsonNode fragments = plan.path("timeFragments");
                if (!fragments.isArray()
                        || fragments.size() > 40
                        || fragments.toString().length() > 4000) throw invalid();
                body.put("time_fragments", fragments.toString());
            } else {
                boolean ydsj = "ydsjxy".equals(p.getProject());
                body.put("form[" + (ydsj ? "uk" : "phone") + "]", account);
                body.put(
                        "form[password]",
                        accountSession == null
                                ? required(f, "password", 200)
                                : accountSession.password());
                body.put(
                        "form[" + (ydsj ? "uid" : "student_id") + "]",
                        accountSession == null
                                ? options.suggested().get("studentId")
                                : accountSession.studentId());
                body.put("form[dis]", form.distance().toPlainString());
                body.put("form[run_type]", selected(options, f, "runType"));
                if ("xbd".equals(p.getProject()))
                    body.put("form[fence_id]", selected(options, f, "fenceId"));
                else body.put("form[zone_id]", selected(options, f, "zoneId"));
                if ("sdxy".equals(p.getProject())) {
                    body.put("form[run_rule_id]", selected(options, f, "runRuleId"));
                    units = BigDecimal.ONE;
                } else {
                    String repair = optional(f, "repair", 5);
                    if (!Set.of("", "false", "true").contains(repair)) throw bad("补跑参数不合法");
                    body.put("form[repair]", "true".equals(repair) ? "1" : "0");
                    if ("true".equals(repair)) units = units.multiply(new BigDecimal("2"));
                }
                if (form.taskTimes() == null
                        || form.taskTimes().size() != form.quantity()
                        || new HashSet<>(form.taskTimes()).size() != form.quantity())
                    throw bad("计划时间数必须与购买次数一致且不能重复");
                for (int i = 0; i < form.taskTimes().size(); i++) {
                    String value = form.taskTimes().get(i);
                    try {
                        LocalDateTime time = LocalDateTime.parse(value, TASK_TIME);
                        if (time.isBefore(ServiceTime.now().minusMinutes(1))
                                || time.isAfter(ServiceTime.now().plusYears(1)))
                            throw bad("计划须在未来一年内");
                    } catch (java.time.DateTimeException ex) {
                        throw bad("计划时间格式错误");
                    }
                    body.put("form[task_list][" + i + "][start_time]", value);
                }
            }
        }
        if (!"flash".equals(p.getProviderType())) {
            body.put("product_id", p.getRemoteProductId());
            body.put("times", form.quantity());
            body.put("km_per_day", form.distance().toPlainString());
        }
        return new PreparedOrder(body, form.quantity(), form.distance(), units, mask(account));
    }

    @Override
    public int refundRemaining(ApiProvider provider, ServiceOrder order) {
        if ("jiguang".equals(order.getProviderType())) {
            return count(
                    call(
                                    provider,
                                    order.getProject(),
                                    "refund_preview",
                                    Map.of("order_no", order.getExternalOrderNo()))
                            .path("item")
                            .path("remaining"));
        }
        if ("flash".equals(order.getProviderType()))
            return Math.max(0, order.getQuantity() - order.getCompleted());
        throw bad("上游未提供可验证的用户退款接口");
    }

    @Override
    public void checkAddTimes(ApiProvider provider, ServiceOrder order, int quantity) {
        if (!"jiguang".equals(order.getProviderType())) throw bad("该商品不支持增次");
        JsonNode item =
                call(
                                provider,
                                order.getProject(),
                                "addtimes_preview",
                                Map.of("order_no", order.getExternalOrderNo(), "delta", quantity))
                        .path("item");
        if (count(item.path("delta")) != quantity) throw invalid();
        // Do not silently sell additional units below the current upstream cost.
        if (decimal(item.path("cost"))
                        .compareTo(order.getUnitCharge().multiply(BigDecimal.valueOf(quantity)))
                > 0) throw bad("上游价格已变化，请联系管理员确认售价");
    }

    @Override
    public Map<String, Object> prepareAction(
            ApiProvider provider, ServiceOrder order, String action, Map<String, String> fields) {
        if (!Set.of("CHANGE_TIME", "DELAY_TASK").contains(action))
            return NativeServiceGateway.super.prepareAction(provider, order, action, fields);
        boolean delay = "DELAY_TASK".equals(action);
        if (!"flash".equals(order.getProviderType())
                || fields == null
                || !fields.keySet()
                        .equals(
                                delay
                                        ? Set.of("taskId", "page")
                                        : Set.of("taskId", "time", "page"))) throw bad("任务修改参数不合法");
        String id = fields.get("taskId"), value = fields.get("time"), page = fields.get("page");
        if (page == null
                || !page.matches("[0-9]{1,4}")
                || id == null
                || !id.matches("[A-Za-z0-9_-]{1,64}")) throw bad("任务编号或分页参数不合法");
        List<RunLog> matches =
                logs(provider, order, Integer.parseInt(page)).items().stream()
                        .filter(item -> id.equals(item.id()))
                        .toList();
        if (matches.size() != 1) throw bad("任务不属于当前订单，或日志页无法明确核实");
        RunLog task = matches.get(0);
        if (!Set.of("未开始", "需要处理").contains(task.status())) throw bad("任务状态尚未确认或已经结束，不能调整此任务");
        if (delay) return Map.of("run_task_id", id);
        try {
            LocalDateTime time = LocalDateTime.parse(value, TASK_TIME);
            if (time.isBefore(ServiceTime.now()) || time.isAfter(ServiceTime.now().plusYears(1)))
                throw bad("计划时间须在未来一年内");
        } catch (java.time.DateTimeException | NullPointerException ex) {
            throw bad("计划时间格式错误");
        }
        return Map.of("run_task_id", id, "start_time", value);
    }

    @Override
    public RemoteResult execute(
            ApiProvider provider,
            ServiceProduct p,
            ServiceOrder order,
            String action,
            Map<String, Object> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        String act;
        boolean flash = "flash".equals(p.getProviderType());
        if ("CREATE".equals(action)) {
            body.putAll(fields);
            act = flash ? p.getProject() + "_add" : "add";
        } else {
            body.put(flash ? "agg_order_id" : "order_no", order.getExternalOrderNo());
            act =
                    switch (action) {
                        case "REFUND" -> flash ? "refund" : "refund_confirm";
                        case "ADD_TIMES" -> {
                            body.put("delta", fields.get("delta"));
                            yield "addtimes_confirm";
                        }
                        case "PAUSE" -> {
                            body.put("pause", "0");
                            yield "pause";
                        }
                        case "RESUME" -> {
                            body.put("pause", "1");
                            yield "pause";
                        }
                        case "DELAY" -> "delay_task";
                        case "DELAY_TASK" -> {
                            body.put("run_task_id", fields.get("run_task_id"));
                            yield "delay_task";
                        }
                        case "CHANGE_TIME" -> {
                            body.putAll(fields);
                            yield "change_task_time";
                        }
                        default -> throw bad("不支持的订单操作");
                    };
        }
        JsonNode data = call(provider, p.getProject(), act, body);
        if ("CREATE".equals(action))
            return new RemoteResult(
                    remoteId(data.path(flash ? "agg_order_id" : "order_no")),
                    "ACTIVE",
                    0,
                    null,
                    flash
                            ? remoteId(data.path("sub_order").path(subOrderField(p.getProject())))
                            : null);
        Integer refundUnits =
                "REFUND".equals(action)
                        ? count(flash ? data.path("cnt") : data.path("item").path("remaining"))
                        : null;
        if ("ADD_TIMES".equals(action)
                && count(data.path("item").path("delta"))
                        != Integer.parseInt(fields.get("delta").toString())) throw invalid();
        return new RemoteResult(
                order.getExternalOrderNo(),
                switch (action) {
                    case "REFUND" -> "REFUNDED";
                    case "PAUSE" -> "PAUSED";
                    default -> "ACTIVE";
                },
                null,
                refundUnits);
    }

    @Override
    public RunLogPage logs(ApiProvider provider, ServiceOrder order, int page) {
        if (page < 1 || page > 1000) throw bad("日志分页超出范围");
        boolean flash = "flash".equals(order.getProviderType());
        if (!flash && !"jiguang".equals(order.getProviderType())) throw bad("上游没有提供执行日志接口");
        if (!flash && page != 1) throw bad("该上游日志不支持分页");
        if (flash && order.getExternalSubOrderNo() == null) throw bad("尚未取得上游任务编号，请先更新进度");
        JsonNode data =
                call(
                        provider,
                        order.getProject(),
                        flash ? "log" : "order_logs",
                        flash
                                ? Map.of(
                                        logOrderField(order.getProject()),
                                        order.getExternalSubOrderNo(),
                                        "page_num",
                                        page,
                                        "page_size",
                                        20)
                                : Map.of("order_no", order.getExternalOrderNo()));
        JsonNode rows = data.path("list");
        if (!rows.isArray() || rows.size() > (flash ? 20 : 1000)) throw invalid();
        List<RunLog> items = new ArrayList<>();
        for (JsonNode row : rows) {
            String time = scalar(row.path(flash ? "start_time" : "createdAt"), 40);
            if (!time.matches("[0-9TtZz:+. -]{10,40}")) throw invalid();
            String status;
            if (flash)
                status =
                        switch (row.path("status_display").asText()) {
                            case "成功" -> "已完成";
                            case "未开始" -> "未开始";
                            case "失败" -> "需要处理";
                            case "退款" -> "已取消";
                            default -> "待确认";
                        };
            else
                status =
                        switch (row.path("type").asText()) {
                            case "addTimes" -> "增加次数";
                            case "adjustCompleted" -> "更新完成次数";
                            case "refund" -> "上游退款记录（本地入账以操作记录为准）";
                            default -> "上游执行记录";
                        };
            items.add(new RunLog(remoteId(row.path(flash ? "run_task_id" : "id")), time, status));
        }
        return new RunLogPage(items, page, flash && items.size() == 20 && page < 1000);
    }

    @Override
    public RemoteResult sync(ApiProvider provider, ServiceOrder order) {
        boolean flash = "flash".equals(order.getProviderType());
        long start = System.nanoTime();
        // Flash only exposes paginated account orders, not a unique-ID lookup. Strict bound; never
        // infer absence as failure.
        for (int page = 1; page <= (flash ? 10 : 1); page++) {
            if (page > 1 && (System.nanoTime() - start) > 5_000_000_000L) break;
            Map<String, Object> fields = new LinkedHashMap<>(Map.of("page", page, "limit", 100));
            if (!flash) {
                fields.put("type", 4);
                fields.put("keywords", order.getExternalOrderNo());
            }
            JsonNode rows = call(provider, order.getProject(), "orders", fields);
            if (!rows.isArray() || rows.size() > 100) throw invalid();
            JsonNode match = null;
            for (JsonNode row : rows)
                if (order.getExternalOrderNo()
                        .equals(row.path(flash ? "agg_order_id" : "order_no").asText())) {
                    if (match != null) throw invalid();
                    match = row;
                }
            if (match != null) {
                String status;
                String remote = scalar(match.path("status"), 40);
                if (flash)
                    status =
                            switch (remote) {
                                case "1" ->
                                        "0".equals(match.path("pause").asText())
                                                ? "PAUSED"
                                                : "ACTIVE";
                                case "2" -> "COMPLETED";
                                case "3", "4" -> "ATTENTION";
                                case "5" -> "REFUND_REVIEW";
                                default -> throw invalid();
                            };
                else
                    status =
                            switch (remote) {
                                case "pending_generate",
                                                "pending",
                                                "next_batch",
                                                "sent",
                                                "wait_ack",
                                                "ack_retry",
                                                "in_progress" ->
                                        "ACTIVE";
                                case "paused" -> "PAUSED";
                                case "finished", "completed" -> "COMPLETED";
                                case "refunded", "pending_refund" -> "REFUND_REVIEW";
                                case "failed", "error" -> "ATTENTION";
                                default -> "ATTENTION";
                            };
                int completed =
                        flash
                                ? ("COMPLETED".equals(status)
                                        ? order.getQuantity()
                                        : order.getCompleted())
                                : count(match.path("completed_times"));
                return new RemoteResult(
                        order.getExternalOrderNo(),
                        status,
                        completed,
                        null,
                        flash ? remoteId(match.path(subOrderField(order.getProject()))) : null);
            }
            if (rows.size() < 100) break;
        }
        throw new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }

    private JsonNode call(
            ApiProvider provider, String project, String action, Map<String, Object> supplied) {
        String type = provider.getProviderType();
        String path =
                switch (type) {
                    case "flash" -> "/flash/api.php";
                    case "heisha" -> "/heisha/heisha.api.php";
                    case "jiguang" -> "/jiguang/jiguang.api.php";
                    default -> throw bad("不支持的供应商类型");
                };
        if (!action.matches("[a-z_]{1,50}")
                || !("default".equals(project)
                        || Set.of("sdxy", "ydsjxy", "xbd").contains(project))) throw bad("协议参数错误");
        if (provider.getUsername() == null
                || provider.getUsername().isBlank()
                || provider.getApiKey() == null
                || provider.getApiKey().isBlank())
            throw new ProviderRequestException(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE);
        Map<String, Object> form = new LinkedHashMap<>(supplied);
        form.put("login_uid", provider.getUsername());
        form.put("login_key", provider.getApiKey());
        String url =
                normalizer.normalize(provider.getApiUrl(), type).toASCIIString()
                        + path
                        + "?act="
                        + action
                        + ("flash".equals(type) ? "&appId=" + project : "");
        String response = http.postForString(provider, url, form);
        if (response == null || response.length() > 262144) throw invalid();
        try {
            JsonNode root = JSON.readTree(response);
            if (root == null
                    || !root.isObject()
                    || !root.path("code").asText().matches("-?[0-9]{1,4}")) throw invalid();
            if (Integer.parseInt(root.get("code").asText()) != ("flash".equals(type) ? 0 : 1))
                throw new ProviderRequestException(
                        ProviderRequestException.Reason.UPSTREAM_REJECTED);
            return root.path("data");
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static String subOrderField(String project) {
        return switch (project) {
            case "sdxy" -> "sdxy_order_id";
            case "ydsjxy" -> "ydsjxy_order_id";
            case "xbd" -> "xbd_order_id";
            default -> throw invalid();
        };
    }

    private static String logOrderField(String project) {
        return "ydsjxy".equals(project) ? "ydsjxy_order_id" : subOrderField(project);
    }

    private static void validateFields(Map<String, String> fields) {
        if (fields == null || fields.size() > 24 || !INPUTS.containsAll(fields.keySet()))
            throw bad("存在不支持的表单字段");
        fields.forEach(
                (key, value) -> {
                    if (value == null
                            || value.length() > 2048
                            || value.codePoints().anyMatch(Character::isISOControl))
                        throw bad("表单字段格式不合法");
                });
    }

    private static String required(Map<String, String> fields, String key, int max) {
        String v = optional(fields, key, max);
        if (v.isBlank()) throw bad("缺少必填字段：" + key);
        return v;
    }

    private static String optional(Map<String, String> fields, String key, int max) {
        String v = fields.getOrDefault(key, "");
        if (v.length() > max) throw bad("字段长度超限：" + key);
        return "password".equals(key) ? v : v.trim();
    }

    private static String selected(Lookup options, Map<String, String> fields, String field) {
        String v = required(fields, field, 100);
        if (options.choices().stream()
                .noneMatch(c -> field.equals(c.field()) && v.equals(c.value())))
            throw bad("所选计划或区域已变化，请重新查询账号");
        return v;
    }

    private static JsonNode find(JsonNode rows, String field, String value) {
        for (JsonNode row : rows) if (value.equals(row.path(field).asText())) return row;
        throw invalid();
    }

    private static void choices(
            List<Choice> out, String field, JsonNode rows, String id, String name) {
        if (!rows.isArray() || rows.size() > 200) throw invalid();
        for (JsonNode row : rows)
            out.add(new Choice(field, scalar(row.path(id), 100), scalar(row.path(name), 120)));
    }

    private static void addTypeChoices(List<Choice> out, JsonNode rules) {
        if (!rules.isObject() || rules.size() > 30) throw invalid();
        rules.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!k.matches("[A-Za-z0-9_-]{1,32}")) throw invalid();
                            out.add(new Choice("runType", k, "计划类型 " + k));
                        });
    }

    private static String scalar(JsonNode n, int max) {
        if (!(n.isTextual() || n.isIntegralNumber())
                || n.asText().isBlank()
                || n.asText().length() > max
                || n.asText().codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return n.asText();
    }

    private static String remoteId(JsonNode n) {
        String s = scalar(n, 64);
        if (!s.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return s;
    }

    private static int count(JsonNode n) {
        String s = scalar(n, 5);
        if (!s.matches("[0-9]{1,4}")) throw invalid();
        return Integer.parseInt(s);
    }

    private static BigDecimal decimal(JsonNode n) {
        String s = n.asText();
        if (!s.matches("[0-9]{1,8}(?:\\.[0-9]{1,6})?")) throw invalid();
        return new BigDecimal(s);
    }

    private static String mask(String value) {
        return value.length() < 5
                ? "***"
                : value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private static BusinessException bad(String text) {
        return new BusinessException(text);
    }

    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
