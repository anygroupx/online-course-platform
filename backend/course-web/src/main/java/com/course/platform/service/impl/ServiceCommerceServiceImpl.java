package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.servicecommerce.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.vo.plugin.*;
import com.course.platform.infra.integration.PluginConnectorRegistry;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.infra.servicecommerce.InternshipNativeServiceGateway;
import com.course.platform.infra.servicecommerce.JingyuNativeServiceGateway;
import com.course.platform.infra.servicecommerce.PhpNativeServiceGateway;
import com.course.platform.infra.servicecommerce.SsbenzDistanceGateway;
import com.course.platform.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Supplier;

/** Local monetary commits bracket, but NEVER encompass, the non-idempotent remote call. */
@Service
@RequiredArgsConstructor
public class ServiceCommerceServiceImpl implements ServiceCommerceService, ServiceOrderStatusRefresh {
    private final ServiceProductMapper productMapper;
    private final ServiceOrderMapper orderMapper;
    private final ServiceOperationMapper operationMapper;
    private final ApiProviderMapper providerMapper;
    private final ApiProviderService providers;
    private final PluginConnectorRegistry catalogs;
    private final NativeServiceGateway gateway;
    private final ServiceAccountSessions accounts;
    private final AccountLedgerServiceImpl ledger;
    private final PlatformTransactionManager transactions;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    @Value("${app.native-services.status-refresh.enabled:false}")
    private boolean statusRefreshEnabled;

    private static final Set<String> PERIODIC_TYPES = Set.of("flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "appui", "leidian", "jingyu");
    private static final Set<String> PERIODIC_STATES = Set.of("ACTIVE", "PAUSED", "ATTENTION", "REFUND_REVIEW");

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> FINAL = Set.of("REFUNDED", "CANCELLED");

    @Override
    public IPage<ProductView> products(int page, int size, boolean admin) {
        return products(page, size, admin, null);
    }

    @Override
    public IPage<ProductView> products(int page, int size, boolean admin, String providerType) {
        user();
        if (providerType != null && PhpNativeServiceGateway.capabilities(providerType).isEmpty())
            throw bad("不支持的服务分类");
        if (admin) admin();
        bounds(page, size);
        var filter =
                new LambdaQueryWrapper<ServiceProduct>()
                        .eq(!admin, ServiceProduct::getEnabled, true)
                        .eq(providerType != null, ServiceProduct::getProviderType, providerType)
                        .orderByDesc(ServiceProduct::getId);
        IPage<ServiceProduct> rows = productMapper.selectPage(new Page<>(page, size), filter);
        return convert(rows, rows.getRecords().stream().map(p -> productView(p, admin)).toList());
    }

    @Override
    public ProductView saveProduct(Long id, ProductCommand c) {
        user();
        admin();
        if (c == null
                || c.providerId() == null
                || c.project() == null
                || c.remoteProductId() == null) throw bad("商品参数不完整");
        if (id != null && !c.enabled())
            return tx(
                    () -> {
                        ServiceProduct stored = productMapper.lock(id);
                        if (stored == null) throw missing();
                        if (!Objects.equals(stored.getVersion(), c.version())
                                || !Objects.equals(stored.getProviderId(), c.providerId())
                                || !stored.getProject().equals(c.project())
                                || !stored.getRemoteProductId().equals(c.remoteProductId()))
                            throw bad("商品版本或绑定已变化");
                        stored.setEnabled(false);
                        stored.setVersion(stored.getVersion() + 1);
                        stamp(stored);
                        requireWrite(productMapper.updateById(stored));
                        return productView(stored, true);
                    });
        ApiProvider provider = active(c.providerId(), null, null);
        if (!PhpNativeServiceGateway.supported(
                provider.getProviderType(), c.project(), c.remoteProductId()))
            throw bad("仅可上架已支持原生订单的商品");
        if (c.unitPrice() == null
                || c.unitPrice().signum() <= 0
                || c.unitPrice().compareTo(new BigDecimal("9999")) > 0
                || c.unitPrice().stripTrailingZeros().scale() > 6) throw bad("售价格式错误");
        if (c.title() == null
                || c.title().isBlank()
                || c.title().length() > 100
                || c.title().codePoints().anyMatch(Character::isISOControl)) throw bad("商品名称格式错误");
        ServiceProduct candidate = new ServiceProduct();
        candidate.setProviderId(c.providerId());
        candidate.setProviderType(provider.getProviderType());
        candidate.setProject(c.project());
        candidate.setRemoteProductId(c.remoteProductId());
        if (c.enabled()
                && PhpNativeServiceGateway.isHeishaFace(candidate)
                && !accounts.faceCollectionConfigured()) throw bad("上架人脸商品前必须配置已批准的官方 HTTPS 采集域名");
        if (daily(candidate)) attestContract(candidate, provider, c.contractPrice());
        else if (c.contractPrice() != null) throw bad("该服务须使用目录报价，不接受合同价替代");
        BigDecimal cost = catalogPrice(candidate, provider);
        if (c.unitPrice().compareTo(cost) < 0) throw bad("售价不能低于已读取的成本单价");
        ServiceProduct result =
                tx(
                        () -> {
                            ServiceProduct p = id == null ? candidate : productMapper.lock(id);
                            if (p == null) throw missing();
                            if (id != null
                                    && (!Objects.equals(p.getVersion(), c.version())
                                            || !Objects.equals(p.getProviderId(), c.providerId())
                                            || !p.getProject().equals(c.project())
                                            || !p.getRemoteProductId().equals(c.remoteProductId())))
                                throw bad("商品已更新，或尝试更换已关联的服务配置，请刷新后重试");
                            if (daily(candidate)) {
                                p.setContractUnitCost(candidate.getContractUnitCost());
                                p.setContractValidUntil(candidate.getContractValidUntil());
                                p.setContractEvidence(candidate.getContractEvidence());
                                p.setContractReviewedBy(candidate.getContractReviewedBy());
                                p.setContractReviewedAt(candidate.getContractReviewedAt());
                                p.setContractProviderIdentity(
                                        candidate.getContractProviderIdentity());
                            }
                            p.setTitle(c.title().trim());
                            p.setDescription(c.description());
                            p.setUnitPrice(c.unitPrice());
                            p.setEnabled(c.enabled());
                            p.setVersion(id == null ? 0 : p.getVersion() + 1);
                            stamp(p);
                            if (id == null) requireWrite(productMapper.insert(p));
                            else requireWrite(productMapper.updateById(p));
                            return p;
                        });
        return productView(result, true);
    }

    @Override
    public Lookup lookup(Long productId, Map<String, String> fields) {
        user();
        ServiceProduct p = forSale(productId);
        return gateway.lookup(active(p.getProviderId(), p.getProviderType(), null), p, fields);
    }

    @Override
    public PluginSchoolPage schools(Long productId, int page, String keyword) {
        user();
        ServiceProduct p = forSale(productId);
        if (daily(p) || "appui".equals(p.getProviderType()))
            return gateway.schools(
                    active(p.getProviderId(), p.getProviderType(), null), p, page, keyword);
        if (!"jiguang".equals(p.getProviderType())) throw bad("该服务不支持学校查询");
        return catalogs.getConnector(p.getProviderType())
                .searchSchools(
                        active(p.getProviderId(), p.getProviderType(), null),
                        new PluginPageQuery(page, 20, keyword));
    }

    @Override
    public QuoteView quote(Long productId, OrderForm form) {
        Long uid = user();
        limitQuotes(uid);
        ServiceProduct p = forSale(productId);
        validateOrderForm(form, p);
        ApiProvider provider = active(p.getProviderId(), p.getProviderType(), null);
        BigDecimal cost = catalogPrice(p, provider);
        var authorization =
                form.accountSessionId() == null ? null : accounts.prepare(p, provider, form);
        PreparedOrder prepared =
                authorization == null ? gateway.prepare(provider, p, form) : authorization.order();
        if (cost.compareTo(p.getUnitPrice()) > 0) throw bad("商品价格待更新，暂不能下单，请联系管理员");
        ServiceOperation op =
                newOperation(
                        uid,
                        p,
                        provider.getConfigVersion(),
                        "CREATE",
                        UUID.randomUUID().toString(),
                        null);
        if (authorization != null) {
            op.setAccountSessionId(authorization.sessionId());
            op.setAccountSessionVersion(authorization.version());
            if (authorization.expiresAt().isBefore(op.getExpiresAt()))
                op.setExpiresAt(authorization.expiresAt());
        }
        if (Set.of("flash", "jingyu").contains(p.getProviderType())) limitTaskDeadline(op, form.taskTimes());
        if (daily(p)) {
            if (prepared.plan() == null) throw bad("实习订单缺少计费周期");
            if (!prepared.plan().startDate().equals(ServiceTime.now().toLocalDate()))
                throw bad("已跨过北京时间零点，请重新预览服务天数");
            op.setScheduleJson(planJson(prepared.plan()));
            limitCalendarDeadline(op);
        }
        if (totalDistance(p.getProviderType())) {
            TotalDistancePlan plan = prepared.distancePlan();
            if (plan == null || prepared.quantity() != 1
                    || prepared.distance().compareTo(new BigDecimal(plan.totalDistance())) != 0
                    || prepared.distance().compareTo(prepared.billablePerUnit()) != 0
                    || !p.getRemoteProductId().equals(plan.typeCode())) throw bad("总公里计划快照不一致");
            op.setScheduleJson(planJson(plan));
        }
        if ("appui".equals(p.getProviderType())) {
            if (prepared.accountFingerprint() == null || prepared.plan() != null
                    || prepared.distancePlan() != null || prepared.distance() != null
                    || BigDecimal.ONE.compareTo(prepared.billablePerUnit()) != 0)
                throw bad("天数订单缺少有效的账号绑定");
            op.setScheduleJson(planJson(prepared.accountFingerprint()));
        }
        if ("leidian".equals(p.getProviderType())) {
            BigDecimal billable = "4".equals(p.getProject()) ? form.distance() : form.distance().min(new BigDecimal("2"));
            if (prepared.accountFingerprint() == null || prepared.plan() != null || prepared.distancePlan() != null
                    || prepared.quantity() != form.quantity() || prepared.distance() == null
                    || prepared.distance().compareTo(form.distance()) != 0
                    || prepared.billablePerUnit() == null || prepared.billablePerUnit().compareTo(billable) != 0)
                throw bad("运动订单缺少有效的账号与计费绑定");
            op.setScheduleJson(planJson(prepared.accountFingerprint()));
            limitRunCalendarDeadline(op);
        }
        if (jingyu(p.getProviderType())) {
            BigDecimal billable = JingyuNativeServiceGateway.billable(p.getProject(), form.distance());
            if (prepared.accountFingerprint() == null || !prepared.accountFingerprint().matches(form.fields().get("account"))
                    || prepared.plan() != null || prepared.distancePlan() != null || prepared.quantity() != form.quantity()
                    || prepared.distance() == null || prepared.distance().compareTo(form.distance()) != 0
                    || prepared.billablePerUnit() == null || prepared.billablePerUnit().compareTo(billable) != 0)
                throw bad("运动订单缺少有效的账号与计费绑定");
            op.setScheduleJson(planJson(prepared.accountFingerprint()));
        }
        op.setQuantity(prepared.quantity());
        op.setDistance(prepared.distance());
        op.setAccountLabel(prepared.accountLabel());
        // Jingyu charges each task to cents before multiplying by its quantity. Other protocols
        // retain eight-decimal unit charges and round only their final amount.
        op.setUnitCharge(jingyu(p.getProviderType())
                ? exactUnitCharge(JingyuNativeServiceGateway.unitCharge(p.getUnitPrice(), p.getProject(), prepared.distance()), BigDecimal.ONE)
                : exactUnitCharge(p.getUnitPrice(), prepared.billablePerUnit()));
        op.setAmount(money(op.getUnitCharge().multiply(BigDecimal.valueOf(op.getQuantity()))));
        if (op.getAmount().signum() <= 0) throw bad(totalDistance(p.getProviderType())
                ? "订单金额不足一分，请调整总公里数或联系管理员" : "订单金额不足一分，请调整次数或联系管理员");
        op.setPayloadEncrypted(encrypt(prepared.fields()));
        return persistQuote(op);
    }

    @Override
    public Lookup orderOptions(String id) {
        user();
        ServiceOrder o = owned(id);
        noPending(o);
        requireReadableRunRecord(o);
        return gateway.orderOptions(forOrder(o), o);
    }

    @Override
    public QuoteView quoteAction(String orderId, ActionForm form) {
        Long uid = user();
        if (form == null || form.action() == null || form.quantity() < 0 || form.quantity() > 365)
            throw bad("操作参数不合法");
        if (!"ADD_TIMES".equals(form.action()) && form.quantity() != 0) throw bad("此操作不接受增次数量");
        limitQuotes(uid);
        ServiceOrder o = owned(orderId);
        noPending(o);
        if ("SCORE_INFO".equals(form.action()) || !actions(o).contains(form.action())) throw bad("当前订单不支持此操作");
        ServiceProduct p = product(o.getProductId());
        ApiProvider provider = forOrder(o);
        ServiceOperation op =
                newOperation(
                        uid,
                        p,
                        provider.getConfigVersion(),
                        form.action(),
                        o.getId(),
                        o.getVersion());
        op.setDistance(o.getDistance());
        op.setUnitCharge(o.getUnitCharge());
        op.setAccountLabel(o.getAccountLabel());
        op.setQuantity(0);
        op.setAmount(ZERO);
        Map<String, Object> fields;
        if (daily(p)) {
            PreparedAction prepared = gateway.prepareScheduledAction(provider, p, o, form);
            fields = new LinkedHashMap<>(prepared.fields());
            op.setQuantity(prepared.quantity());
            op.setUnitCharge(prepared.unitCharge());
            op.setAmount(
                    money(prepared.unitCharge().multiply(BigDecimal.valueOf(prepared.quantity()))));
            if (prepared.quantity() > 0 && op.getAmount().signum() <= 0)
                throw bad("本次新增服务金额不足一分，请调整服务日期或联系管理员");
            if (prepared.plan() != null) op.setScheduleJson(planJson(prepared.plan()));
            if (isDebit(op) && op.getAmount().signum() > 0) {
                BigDecimal cost =
                        catalogPrice(p, provider)
                                .multiply(
                                        InternshipNativeServiceGateway.multiplier(
                                                p.getProject(),
                                                InternshipNativeServiceGateway.plan(o).schedule()));
                if (cost.compareTo(o.getUnitCharge()) > 0) throw bad("合同成本变化，原订单单价不足以续期，请联系管理员");
            }
            limitCalendarDeadline(op);
        } else {
            if (form.schedule() != null) throw bad("该服务不接受实习计划参数");
            fields =
                    new LinkedHashMap<>(
                            gateway.prepareAction(
                                    provider,
                                    o,
                                    form.action(),
                                    form.fields() == null ? Map.of() : form.fields()));
        }
        if ("CHANGE_TIME".equals(form.action())) {
            if (!(fields.get("start_time") instanceof String time)) throw bad("任务时间格式错误");
            limitTaskDeadline(op, List.of(time));
        }
        if ("EDIT_PLAN".equals(form.action()) && "leidian".equals(o.getProviderType()))
            limitRunCalendarDeadline(op);
        if ("EDIT_PLAN".equals(form.action()) && "wuxin".equals(o.getProviderType()))
            op.setDistance(new BigDecimal(fields.get("run_meter").toString()));
        if ("REFUND".equals(form.action())) {
            int remaining = gateway.refundRemaining(provider, o);
            if (remaining < 0 || remaining > o.getQuantity()) throw bad("剩余次数无法核实，需人工核对");
            op.setQuantity(remaining);
            op.setAmount(
                    money(o.getUnitCharge().multiply(BigDecimal.valueOf(remaining)))
                            .min(o.getPaidAmount().subtract(o.getRefundedAmount())));
        } else if ("ADD_TIMES".equals(form.action())) {
            if (form.quantity() < 1
                    || form.quantity() > 365
                    || o.getQuantity() + form.quantity() > 9999) throw bad("增次数量不合法");
            gateway.checkAddTimes(provider, o, form.quantity());
            op.setQuantity(form.quantity());
            op.setAmount(money(o.getUnitCharge().multiply(BigDecimal.valueOf(form.quantity()))));
            if (op.getAmount().signum() <= 0) throw bad("增次金额不足一分");
            fields.put("delta", form.quantity());
        }
        op.setPayloadEncrypted(encrypt(fields));
        return persistQuote(op);
    }

    private QuoteView persistQuote(ServiceOperation op) {
        return tx(() -> {
            requireWrite(operationMapper.insert(op));
            ServiceOperation saved = operationMapper.selectById(op.getId());
            requireExactStoredCharge(op.getUnitCharge(), saved == null ? null : saved.getUnitCharge());
            return quoteView(saved);
        });
    }

    private static void requireExactStoredCharge(BigDecimal expected, BigDecimal stored) {
        // A missing or partially applied precision migration must not silently reprice orders.
        if (expected == null || stored == null || expected.compareTo(stored) != 0)
            throw bad("计费信息暂不可用，请联系管理员后重新预览");
    }

    private record Dispatch(
            ServiceOperation op,
            ServiceOrder order,
            ServiceProduct product,
            ApiProvider provider) {}

    @Override
    public QuoteView confirm(String quoteId) {
        Long uid = user();
        uuid(quoteId);
        Dispatch dispatch = tx(() -> reserve(quoteId, uid));
        if (dispatch == null) return operation(quoteId);
        try {
            // One and only one dispatch for this durable operation ID, even with simultaneous
            // confirmations.
            RemoteResult result =
                    gateway.execute(
                            dispatch.provider(),
                            dispatch.product(),
                            dispatch.order(),
                            dispatch.op().getAction(),
                            decrypt(dispatch.op().getPayloadEncrypted()));
            tx(
                    () -> {
                        finish(quoteId, result);
                        return null;
                    });
        } catch (Exception ex) {
            // PHP bridges also return negative codes for downstream timeouts. None prove "not
            // accepted".
            String reason =
                    ex instanceof ProviderRequestException p ? p.getReason().name() : "UNCONFIRMED";
            tx(
                    () -> {
                        ServiceOperation op = operationMapper.lock(quoteId);
                        if ("DISPATCHING".equals(op.getState())) {
                            op.setState("UNKNOWN");
                            op.setErrorCategory(reason);
                            touch(op);
                            requireWrite(operationMapper.updateById(op));
                        }
                        return null;
                    });
        }
        return operation(quoteId);
    }

    private Dispatch reserve(String id, Long uid) {
        ServiceOperation op = operationMapper.lock(id);
        own(op, uid);
        if ("SETTLE_REFUND".equals(op.getAction())) throw bad("退款入账须由财务管理员确认");
        if (!"READY".equals(op.getState())) return null;
        if (!op.getExpiresAt().isAfter(ServiceTime.now())) throw bad("金额预览已过期，请重新预览");
        ServiceProduct p = productMapper.lock(op.getProductId());
        if (p == null) throw missing();
        if (!Objects.equals(p.getVersion(), op.getProductVersion())) throw bad("商品配置或售价已变化，请重新预览");
        ApiProvider provider =
                active(p.getProviderId(), p.getProviderType(), op.getProviderVersion());
        ServiceOrder o;
        if (daily(p) && isDebit(op) && op.getAmount().signum() > 0) requireContract(p, provider);
        if ("CREATE".equals(op.getAction())) {
            if (!Boolean.TRUE.equals(p.getEnabled())) throw bad("商品已下架");
            if (op.getAccountSessionId() != null)
                accounts.consume(
                        op.getAccountSessionId(), op.getAccountSessionVersion(), p, provider, uid);
            o = new ServiceOrder();
            o.setId(op.getOrderId());
            o.setUserId(uid);
            o.setProductId(p.getId());
            o.setProviderId(p.getProviderId());
            o.setProviderVersion(op.getProviderVersion());
            o.setProviderIdentity(identity(provider));
            o.setProviderType(p.getProviderType());
            o.setProject(p.getProject());
            o.setRemoteProductId(p.getRemoteProductId());
            o.setTitle(p.getTitle());
            o.setAccountLabel(op.getAccountLabel());
            o.setStatus("SUBMITTING");
            o.setQuantity(op.getQuantity());
            o.setCompleted(0);
            o.setDistance(op.getDistance());
            o.setScheduleJson(op.getScheduleJson());
            o.setUnitCharge(op.getUnitCharge());
            o.setPaidAmount(op.getAmount());
            o.setRefundedAmount(ZERO);
            o.setVersion(0L);
            o.setPendingOperationId(id);
            o.setCreateTime(ServiceTime.now());
            o.setUpdateTime(ServiceTime.now());
            requireWrite(orderMapper.insert(o));
            ServiceOrder saved = orderMapper.selectById(o.getId());
            requireExactStoredCharge(op.getUnitCharge(), saved == null ? null : saved.getUnitCharge());
        } else {
            o = orderMapper.lock(op.getOrderId());
            if (o == null || !uid.equals(o.getUserId())) throw missing();
            noPending(o);
            if (!Objects.equals(o.getVersion(), op.getOrderVersion())
                    || !actions(o).contains(op.getAction())) throw bad("订单状态已变化，请重新预览");
            if (!identity(provider).equals(o.getProviderIdentity()))
                throw bad("服务配置已变更，旧订单须先人工核对");
            o.setPendingOperationId(id);
            o.setVersion(o.getVersion() + 1);
            o.setUpdateTime(ServiceTime.now());
            requireWrite(orderMapper.updateById(o));
        }
        if (isDebit(op) && op.getAmount().signum() > 0)
            ledger.debit(
                    uid,
                    op.getAmount(),
                    AccountLedgerServiceImpl.BIZ_ORDER,
                    "SERVICE:" + id,
                    "服务订单确认");
        op.setState("DISPATCHING");
        touch(op);
        requireWrite(operationMapper.updateById(op));
        return new Dispatch(op, o, p, provider);
    }

    private void finish(String id, RemoteResult result) {
        ServiceOperation op = operationMapper.lock(id);
        if (op == null || !("DISPATCHING".equals(op.getState()) || "UNKNOWN".equals(op.getState())))
            return;
        ServiceOrder o = orderMapper.lock(op.getOrderId());
        if (o == null || !id.equals(o.getPendingOperationId())) throw bad("订单操作版本冲突");
        boolean jingyu = jingyu(o.getProviderType());
        if (jingyu) {
            boolean refund = "REFUND".equals(op.getAction());
            requireJingyuResult(o, result, refund && op.getResolvedBy() != null);
            if (!"CREATE".equals(op.getAction()) && (!Objects.equals(o.getExternalOrderNo(), result.externalOrderNo())
                    || !Objects.equals(o.getExternalSubOrderNo(), result.externalSubOrderNo()))) throw bad("订单编号不一致");
            if (refund && (op.getResolvedBy() == null || result.refundedUnits() == null
                    || !"REFUND_REVIEW".equals(result.status()) || result.refundedUnits() > op.getQuantity()))
                throw bad("退款数量须人工核对后入账");
            o.setCompleted(result.completed());
        }
        if ("CREATE".equals(op.getAction())) {
            if (result.externalOrderNo() == null
                    || !result.externalOrderNo().matches("[A-Za-z0-9_-]{1,64}"))
                throw bad("未取得可核实的订单编号");
            if (totalDistance(o.getProviderType())
                    && (!SsbenzDistanceGateway.validId(result.externalOrderNo())
                            || !"SUBMITTED".equals(result.status()) || result.completed() != null
                            || result.refundedUnits() != null || result.externalSubOrderNo() != null))
                throw bad("总公里订单仅可确认提交记录，不能确认执行或退款");
            if ("leidian".equals(o.getProviderType())) {
                requireLeidianResult(o, result, false);
                o.setCompleted(result.completed());
            }
            o.setExternalOrderNo(result.externalOrderNo());
            o.setExternalSubOrderNo(result.externalSubOrderNo());
            o.setStatus(totalDistance(o.getProviderType()) ? "SUBMITTED" : jingyu || "leidian".equals(o.getProviderType()) ? result.status() : "ACTIVE");
        } else if ("CANCEL".equals(op.getAction())) {
            if (!"leidian".equals(o.getProviderType()) || op.getAmount().signum() != 0 || op.getQuantity() != 0)
                throw bad("取消订单回执无法核实");
            requireLeidianResult(o, result, true);
            if (!o.getExternalOrderNo().equals(result.externalOrderNo())
                    || !o.getExternalSubOrderNo().equals(result.externalSubOrderNo())) throw bad("订单编号不一致");
            o.setCompleted(result.completed());
            // Cancellation proves neither returned units nor money. Settlement is a separate, audited operation.
            o.setStatus("REFUND_REVIEW");
        } else if ("ADD_TIMES".equals(op.getAction())) {
            o.setQuantity(o.getQuantity() + op.getQuantity());
            o.setPaidAmount(o.getPaidAmount().add(op.getAmount()));
            o.setStatus("ACTIVE");
        } else if ("EDIT_SCHEDULE".equals(op.getAction())) {
            if (op.getScheduleJson() == null) throw bad("缺少新实习周期");
            o.setScheduleJson(op.getScheduleJson());
            o.setQuantity(o.getQuantity() + op.getQuantity());
            o.setPaidAmount(o.getPaidAmount().add(op.getAmount()));
            if ("COMPLETED".equals(o.getStatus())) o.setStatus("ACTIVE");
        } else if ("RUN_NOW".equals(op.getAction())) {
            o.setPaidAmount(o.getPaidAmount().add(op.getAmount()));
        } else if ("REFUND".equals(op.getAction())) {
            if ("appui".equals(o.getProviderType()) && result.refundedUnits() != null
                    && result.refundedUnits() > op.getQuantity())
                throw bad("退款天数超过已确认上限，请人工核对");
            applyRefund(op, o, result.refundedUnits());
        } else if ("EDIT_PLAN".equals(op.getAction())) {
            o.setDistance(op.getDistance());
        } else if (jingyu) {
            o.setStatus(result.status());
        } else if (!Set.of("CHANGE_TIME", "DELAY_TASK", "REPORT").contains(op.getAction()))
            o.setStatus("PAUSE".equals(op.getAction()) ? "PAUSED" : "ACTIVE");
        completeOperation(op, o);
    }

    private static void requireLeidianResult(ServiceOrder order, RemoteResult result, boolean cancellation) {
        if (result == null || result.externalOrderNo() == null || !result.externalOrderNo().matches("[A-Za-z0-9_-]{1,64}")
                || result.externalSubOrderNo() == null || !result.externalSubOrderNo().matches("[1-9][0-9]{0,18}")
                || result.completed() == null || result.completed() < order.getCompleted()
                || result.completed() > order.getQuantity() || result.refundedUnits() != null || result.status() == null
                || !(cancellation ? Set.of("REFUND_REVIEW") : Set.of("ACTIVE", "ATTENTION")).contains(result.status()))
            throw bad("订单编号、已用次数或取消结果无法核实");
    }

    private static boolean jingyu(String type) { return JingyuNativeServiceGateway.TYPE.equals(type); }

    private static boolean identityRecovery(ServiceOrder order, ServiceOperation operation) {
        return jingyu(order.getProviderType()) || "leidian".equals(order.getProviderType()) && "CREATE".equals(operation.getAction());
    }

    private static void requireJingyuResult(ServiceOrder order, RemoteResult result, boolean reconciledRefund) {
        if (result == null || result.externalOrderNo() == null || !result.externalOrderNo().matches("[A-Za-z0-9_-]{1,64}")
                || result.externalSubOrderNo() == null || !result.externalSubOrderNo().matches("[1-9][0-9]{0,18}")
                || result.completed() == null || result.completed() < order.getCompleted() || result.completed() > order.getQuantity()
                || result.status() == null || !Set.of("ACTIVE", "PAUSED", "COMPLETED", "ATTENTION", "REFUND_REVIEW").contains(result.status())
                || "COMPLETED".equals(result.status()) && result.completed() != order.getQuantity().intValue()
                || !reconciledRefund && result.refundedUnits() != null
                || reconciledRefund && (result.refundedUnits() == null || result.refundedUnits() < 0
                        || result.refundedUnits() > order.getQuantity() - result.completed()))
            throw bad("订单编号、完成次数或处理结果无法核实");
    }

    private static boolean cancellationReview(ServiceOrder order) {
        return "leidian".equals(order.getProviderType()) && "REFUND_REVIEW".equals(order.getStatus());
    }

    private static boolean leidianRecordRemoved(ServiceOrder order) {
        return "leidian".equals(order.getProviderType())
                && ("REFUND_REVIEW".equals(order.getStatus()) || FINAL.contains(order.getStatus()));
    }

    private static void requireReadableRunRecord(ServiceOrder order) {
        // A successful cancellation deletes this protocol's remote row; settlement cannot restore it.
        if (leidianRecordRemoved(order)) throw bad("订单已取消，无法读取执行记录或安排，请查看订单操作记录");
    }

    private void applyRefund(ServiceOperation op, ServiceOrder o, Integer units) {
        if (units == null || units < 0 || units > o.getQuantity() - o.getCompleted())
            throw bad("退款回执次数不合法");
        BigDecimal amount =
                money(o.getUnitCharge().multiply(BigDecimal.valueOf(units)))
                        .min(op.getAmount())
                        .min(o.getPaidAmount().subtract(o.getRefundedAmount()));
        if (amount.signum() > 0)
            ledger.credit(
                    o.getUserId(),
                    amount,
                    AccountLedgerServiceImpl.BIZ_REFUND,
                    "SERVICE:" + op.getId(),
                    "服务订单退款",
                    false);
        op.setQuantity(units);
        op.setAmount(amount);
        o.setRefundedAmount(o.getRefundedAmount().add(amount));
        o.setStatus("REFUNDED");
    }

    private void completeOperation(ServiceOperation op, ServiceOrder o) {
        o.setPendingOperationId(null);
        o.setVersion(o.getVersion() + 1);
        o.setUpdateTime(ServiceTime.now());
        requireWrite(orderMapper.updateById(o));
        op.setState("SUCCEEDED");
        op.setPayloadEncrypted(null);
        op.setErrorCategory(null);
        touch(op);
        requireWrite(operationMapper.updateById(op));
    }

    @Override
    public QuoteView operation(String id) {
        Long uid = user();
        uuid(id);
        ServiceOperation op = operationMapper.selectById(id);
        own(op, uid);
        if ("READY".equals(op.getState()) && !op.getExpiresAt().isAfter(ServiceTime.now())) {
            tx(
                    () -> {
                        ServiceOperation latest = operationMapper.lock(id);
                        if ("READY".equals(latest.getState())
                                && !latest.getExpiresAt().isAfter(ServiceTime.now())) {
                            latest.setState("EXPIRED");
                            latest.setPayloadEncrypted(null);
                            touch(latest);
                            requireWrite(operationMapper.updateById(latest));
                        }
                        return null;
                    });
            op = operationMapper.selectById(id);
        }
        // Process death between local commit and remote reply must remain a reconciliation case,
        // never a retry.
        if ("DISPATCHING".equals(op.getState())
                && op.getUpdateTime().isBefore(ServiceTime.now().minusMinutes(5))) {
            tx(
                    () -> {
                        ServiceOperation latest = operationMapper.lock(id);
                        if ("DISPATCHING".equals(latest.getState())
                                && latest.getUpdateTime()
                                        .isBefore(ServiceTime.now().minusMinutes(5))) {
                            latest.setState("UNKNOWN");
                            latest.setErrorCategory("PROCESS_INTERRUPTED");
                            touch(latest);
                            requireWrite(operationMapper.updateById(latest));
                        }
                        return null;
                    });
            op = operationMapper.selectById(id);
        }
        return quoteView(op);
    }

    @Override
    public IPage<OrderView> orders(int page, int size, boolean admin) {
        return orders(page, size, admin, ServiceOrderFilter.empty());
    }

    @Override
    public IPage<OrderView> orders(int page, int size, boolean admin, ServiceOrderFilter filter) {
        Long uid = user();
        if (admin) admin();
        bounds(page, size);
        ServiceOrderFilter f = filter == null ? ServiceOrderFilter.empty() : filter;
        if (f.ownerId() != null && (!admin || f.ownerId() < 1))
            throw bad("所属用户筛选不可用");
        String keyword = orderSearchText(f.keyword(), 100).strip();
        String type = orderSearchText(f.providerType(), 20);
        String status = orderSearchText(f.status(), 24);
        String orderId = orderSearchText(f.orderId(), 36);
        if (!type.isEmpty() && !Set.of("flash", "heisha", "jiguang", "wuxin", "sxdk_tw", "appui", "leidian", "jingyu", "ssbenz_xbd").contains(type))
            throw bad("服务类型筛选不正确");
        if (!status.isEmpty() && !Set.of("ACTIVE", "PAUSED", "COMPLETED", "REFUNDED", "CANCELLED",
                "CONFIRMING", "REFUND_REVIEW", "ATTENTION", "SUBMITTING", "SUBMITTED", "SUBMISSION_REVIEW").contains(status))
            throw bad("订单状态筛选不正确");
        if (!orderId.isEmpty()) uuid(orderId);
        LocalDate from = orderSearchDate(f.createdFrom());
        LocalDate to = orderSearchDate(f.createdTo());
        if (from != null && to != null && from.isAfter(to)) throw bad("开始日期不能晚于结束日期");

        var query = new LambdaQueryWrapper<ServiceOrder>()
                .eq(!admin, ServiceOrder::getUserId, uid)
                .eq(admin && f.ownerId() != null, ServiceOrder::getUserId, f.ownerId())
                .eq(!type.isEmpty(), ServiceOrder::getProviderType, type)
                .eq(!orderId.isEmpty(), ServiceOrder::getId, orderId);
        // Filter the state shown to the user, not a pre-operation status hidden by a pending write.
        if ("CONFIRMING".equals(status)) {
            query.and(q -> q.isNotNull(ServiceOrder::getPendingOperationId).or().eq(ServiceOrder::getStatus, status));
        } else if (!status.isEmpty()) {
            query.isNull(ServiceOrder::getPendingOperationId).eq(ServiceOrder::getStatus, status);
        }
        if (from != null) query.ge(ServiceOrder::getCreateTime, from.atStartOfDay());
        if (to != null) query.lt(ServiceOrder::getCreateTime, to.plusDays(1).atStartOfDay());
        if (!keyword.isEmpty()) {
            // Fixed SQL and bound parameters; %, _ and ! are literal search characters.
            // Only the already-visible label is searched, never encrypted account/password material.
            String literal = "%" + keyword.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%";
            query.and(q -> q.apply("id LIKE {0} ESCAPE '!' OR title LIKE {0} ESCAPE '!' "
                    + "OR account_label LIKE {0} ESCAPE '!'", literal));
        }
        query.orderByDesc(ServiceOrder::getCreateTime).orderByDesc(ServiceOrder::getId);
        IPage<ServiceOrder> found = orderMapper.selectPage(new Page<>(page, size), query);
        return convert(found, found.getRecords().stream().map(this::orderView).toList());
    }

    private static String orderSearchText(String value, int max) {
        if (value == null) return "";
        if (value.length() > max || value.codePoints().anyMatch(Character::isISOControl))
            throw bad("订单筛选条件不正确");
        return value;
    }

    private static LocalDate orderSearchDate(String value) {
        String raw = orderSearchText(value, 10);
        if (raw.isEmpty()) return null;
        try {
            if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("date", raw, 0);
            LocalDate date = LocalDate.parse(raw);
            if (date.getYear() < 1000 || date.getYear() > 9998) throw bad("创建日期超出支持范围");
            return date;
        } catch (DateTimeParseException ex) {
            throw bad("创建日期不正确");
        }
    }

    @Override
    public OrderView order(String id) {
        user();
        return orderView(owned(id));
    }

    @Override
    public OrderView sync(String id) {
        Long uid = user();
        uuid(id);
        String token = UUID.randomUUID().toString();
        ServiceOrder order = tx(() -> {
            ServiceOrder current = orderMapper.lock(id);
            if (current == null || !uid.equals(current.getUserId())) throw missing();
            noPending(current);
            if (FINAL.contains(current.getStatus()) || cancellationReview(current)) return current;
            if (current.getExternalOrderNo() == null || current.getExternalOrderNo().isBlank())
                throw bad("订单尚未取得可核实的编号，请联系管理员核对");
            if (!enabled || orderMapper.activeStatusOwner(uid) != 1) throw bad("进度更新已暂停");
            var now = ServiceTime.now();
            // Manual reads share the same lease as scheduled reads. A later, stale response
            // must not overwrite progress just because the first read left the version unchanged.
            if (orderMapper.claimStatusCheck(id, token, now, now.plusMinutes(5), now.plusMinutes(5)) != 1)
                throw bad("正在更新进度，请稍后刷新订单");
            return current;
        });
        if (FINAL.contains(order.getStatus()) || cancellationReview(order)) return orderView(order);
        try {
            return readStatus(order, token, false);
        } catch (RuntimeException ex) {
            failedStatusCheck(order, token, false);
            throw ex;
        }
    }

    @Override
    public boolean statusRefreshActive() {
        return enabled && statusRefreshEnabled;
    }

    @Override
    public int refreshDueStatuses() {
        if (!statusRefreshActive()) return 0;
        // A due-time queue is persisted per order. Failed first rows cannot starve later orders.
        List<String> ids = orderMapper.dueStatusChecks(ServiceTime.now(), 10);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
        int attempted = 0;
        for (String id : ids) {
            if (!statusRefreshActive() || Thread.currentThread().isInterrupted()
                    || System.nanoTime() >= deadline) break;
            String token = UUID.randomUUID().toString();
            ServiceOrder order = tx(() -> {
                ServiceOrder current = orderMapper.lock(id);
                var now = ServiceTime.now();
                if (!statusRefreshActive() || !periodicEligible(current)
                        || (current.getStatusCheckAfter() != null && current.getStatusCheckAfter().isAfter(now))
                        || (current.getStatusCheckUntil() != null && current.getStatusCheckUntil().isAfter(now))
                        || orderMapper.activeStatusOwner(current.getUserId()) != 1) return null;
                // Readers are bounded to five seconds between pages plus a <=120s HTTP call.
                // A five-minute lease survives that budget; expired tokens can never commit.
                if (orderMapper.claimStatusCheck(id, token, now, now.plusMinutes(5), now.plusMinutes(5)) != 1)
                    return null;
                return current;
            });
            if (order == null) continue;
            attempted++;
            try {
                readStatus(order, token, true);
            } catch (RuntimeException ex) {
                // Never save or log remote bodies, credentials, exception text, or account labels.
                failedStatusCheck(order, token, true);
            }
        }
        return attempted;
    }

    private OrderView readStatus(ServiceOrder order, String leaseToken, boolean automatic) {
        ApiProvider provider = forOrder(order);
        Long configuration = provider.getConfigVersion();
        if (!enabled || (automatic && !statusRefreshActive())
                || orderMapper.activeStatusOwner(order.getUserId()) != 1) throw bad("进度更新已暂停");
        RemoteResult remote = gateway.sync(provider, order);
        if ("leidian".equals(order.getProviderType())) {
            requireLeidianResult(order, remote, false);
            if (!Objects.equals(order.getExternalSubOrderNo(), remote.externalSubOrderNo())) throw bad("订单编号不一致");
        }
        if (jingyu(order.getProviderType())) {
            requireJingyuResult(order, remote, false);
            if (!Objects.equals(order.getExternalSubOrderNo(), remote.externalSubOrderNo())) throw bad("订单编号不一致");
        }
        boolean distanceOrder = totalDistance(order.getProviderType());
        boolean internshipOrder = "sxdk_tw".equals(order.getProviderType());
        Set<String> allowedStates = distanceOrder ? Set.of("SUBMITTED", "SUBMISSION_REVIEW")
                : Set.of("ACTIVE", "PAUSED", "COMPLETED", "REFUND_REVIEW", "ATTENTION");
        if (remote == null || !order.getExternalOrderNo().equals(remote.externalOrderNo())
                || remote.status() == null || !allowedStates.contains(remote.status())
                || remote.refundedUnits() != null
                || (remote.externalSubOrderNo() != null && !remote.externalSubOrderNo().matches("[A-Za-z0-9_-]{1,64}"))
                || (distanceOrder && (remote.completed() != null || remote.externalSubOrderNo() != null))
                || (internshipOrder && (!Integer.valueOf(0).equals(remote.completed())
                        || remote.externalSubOrderNo() != null)))
            throw bad("订单回执无法核实");
        return tx(() -> {
            ServiceOrder current = orderMapper.lock(order.getId());
            var now = ServiceTime.now();
            if (!enabled || !sameStatusTarget(order, current) || current.getPendingOperationId() != null)
                throw bad("订单正在更新，请刷新");
            if ((automatic && (!statusRefreshActive() || !periodicEligible(current)))
                    || !leaseToken.equals(current.getStatusCheckToken())
                    || current.getStatusCheckUntil() == null || !current.getStatusCheckUntil().isAfter(now)
                    || orderMapper.activeStatusOwner(current.getUserId()) != 1) throw bad("进度更新已暂停");
            // A credential/configuration change during the HTTP read invalidates that response.
            if (!Objects.equals(configuration, forOrder(current).getConfigVersion()))
                throw bad("服务配置已变更，请重新核对进度");
            if (!distanceOrder && (remote.completed() == null || remote.completed() < 0
                    || remote.completed() > current.getQuantity())) throw bad("完成次数异常");
            String status = "REFUND_REVIEW".equals(current.getStatus()) ? current.getStatus() : remote.status();
            String subOrder = remote.externalSubOrderNo() == null ? current.getExternalSubOrderNo() : remote.externalSubOrderNo();
            // Internship status is a plan state, never evidence of completed attendance days.
            Integer completed = distanceOrder || internshipOrder ? current.getCompleted() : remote.completed();
            boolean changed = !Objects.equals(status, current.getStatus())
                    || !Objects.equals(subOrder, current.getExternalSubOrderNo())
                    || !Objects.equals(completed, current.getCompleted());
            if (changed) {
                current.setStatus(status);
                current.setExternalSubOrderNo(subOrder);
                current.setCompleted(completed);
                current.setVersion(current.getVersion() + 1);
                current.setUpdateTime(now);
                requireWrite(orderMapper.updateById(current));
            }
            // Successful reads without a business change do not invalidate a frozen action quote.
            requireWrite(orderMapper.statusCheckSucceeded(current.getId(), leaseToken, now, now.plusMinutes(5)));
            current.setStatusCheckedAt(now);
            current.setStatusCheckState("OK");
            return orderView(current);
        });
    }

    private void failedStatusCheck(ServiceOrder snapshot, String leaseToken, boolean automatic) {
        tx(() -> {
            ServiceOrder current = orderMapper.lock(snapshot.getId());
            if (current == null || !leaseToken.equals(current.getStatusCheckToken())) return null;
            var now = ServiceTime.now();
            if (!enabled || (automatic && (!statusRefreshActive() || !periodicEligible(current)))
                    || orderMapper.activeStatusOwner(current.getUserId()) != 1
                    || current.getPendingOperationId() != null || FINAL.contains(current.getStatus())
                    || !sameStatusTarget(snapshot, current)
                    || current.getStatusCheckUntil() == null || !current.getStatusCheckUntil().isAfter(now)) {
                orderMapper.releaseStatusCheck(current.getId(), leaseToken, now.plusMinutes(5));
                return null;
            }
            int failures = Math.min(8, (current.getStatusCheckFailures() == null ? 0 : current.getStatusCheckFailures()) + 1);
            long minutes = Math.min(60, 5L << (failures - 1));
            requireWrite(orderMapper.statusCheckFailed(current.getId(), leaseToken, now, now.plusMinutes(minutes), failures));
            return null;
        });
    }

    private static boolean sameStatusTarget(ServiceOrder snapshot, ServiceOrder current) {
        return current != null && Objects.equals(current.getVersion(), snapshot.getVersion())
                && Objects.equals(current.getExternalOrderNo(), snapshot.getExternalOrderNo())
                && Objects.equals(current.getExternalSubOrderNo(), snapshot.getExternalSubOrderNo())
                && Objects.equals(current.getUserId(), snapshot.getUserId())
                && Objects.equals(current.getProviderId(), snapshot.getProviderId())
                && Objects.equals(current.getProviderType(), snapshot.getProviderType())
                && Objects.equals(current.getProviderIdentity(), snapshot.getProviderIdentity())
                && Objects.equals(current.getProject(), snapshot.getProject());
    }

    private static boolean periodicEligible(ServiceOrder order) {
        return order != null && !cancellationReview(order) && PERIODIC_TYPES.contains(order.getProviderType())
                && PERIODIC_STATES.contains(order.getStatus()) && order.getPendingOperationId() == null
                && order.getExternalOrderNo() != null && !order.getExternalOrderNo().isBlank();
    }

    @Override
    public RunLogPage logs(String id, int page) {
        user();
        ServiceOrder o = owned(id);
        noPending(o);
        requireReadableRunRecord(o);
        if (o.getExternalOrderNo() == null) throw bad("订单尚无可核实的编号");
        return gateway.logs(forOrder(o), o, page);
    }

    @Override
    public OrderText scoreInfo(String id) {
        user();
        ServiceOrder order = owned(id);
        noPending(order);
        if (!actions(order).contains("SCORE_INFO")) throw bad("该订单暂不能查询成绩信息");
        return gateway.scoreInfo(forOrder(order), order);
    }

    @Override
    public List<EventView> events(String id) {
        user();
        owned(id);
        return operationMapper
                .selectList(
                        new LambdaQueryWrapper<ServiceOperation>()
                                .eq(ServiceOperation::getOrderId, id)
                                .orderByDesc(ServiceOperation::getCreateTime)
                                .last("LIMIT 100"))
                .stream()
                .map(
                        op ->
                                new EventView(
                                        op.getId(),
                                        op.getAction(),
                                        op.getState(),
                                        plain(op.getAmount()),
                                        op.getErrorCategory(),
                                        op.getCreateTime()))
                .toList();
    }

    @Override
    public QuoteView adminOperation(String id) {
        user();
        admin();
        SecurityUtils.requireAuthority("payment:reconcile");
        uuid(id);
        ServiceOperation op = operationMapper.selectById(id);
        if (op == null) throw missing();
        return quoteView(op);
    }

    @Override
    public OrderAuditView audit(String orderId) {
        financeAdmin();
        uuid(orderId);
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw missing();
        var events =
                operationMapper
                        .selectList(
                                new LambdaQueryWrapper<ServiceOperation>()
                                        .eq(ServiceOperation::getOrderId, orderId)
                                        .orderByDesc(ServiceOperation::getCreateTime)
                                        .last("LIMIT 100"))
                        .stream()
                        .map(
                                op ->
                                        new AuditEventView(
                                                new EventView(
                                                        op.getId(),
                                                        op.getAction(),
                                                        op.getState(),
                                                        plain(op.getAmount()),
                                                        op.getErrorCategory(),
                                                        op.getCreateTime()),
                                                op.getResolvedBy(),
                                                op.getResolutionNote()))
                        .toList();
        return new OrderAuditView(
                orderView(order),
                order.getUserId(),
                order.getProviderId(),
                order.getExternalOrderNo(),
                order.getExternalSubOrderNo(),
                events);
    }

    @Override
    public QuoteView quoteRefundSettlement(String orderId, RefundSettlementForm form) {
        Long actor = financeAdmin();
        uuid(orderId);
        if (form == null) throw bad("退款核对信息不完整");
        evidence(form.upstreamChecked(), form.evidence());
        ServiceOrder o = orderMapper.selectById(orderId);
        if (o == null) throw missing();
        requireRefundReview(o);
        if (!Objects.equals(o.getVersion(), form.orderVersion())) throw bad("订单状态已变化，请刷新后重新核对");
        if (form.refundedUnits() < 0 || form.refundedUnits() > o.getQuantity() - o.getCompleted())
            throw bad("退款次数不能超过尚未完成次数");
        limitQuotes(o.getUserId());
        ServiceOperation op =
                newOperation(
                        o.getUserId(),
                        product(o.getProductId()),
                        o.getProviderVersion(),
                        "SETTLE_REFUND",
                        o.getId(),
                        o.getVersion());
        op.setDistance(o.getDistance());
        op.setUnitCharge(o.getUnitCharge());
        op.setAccountLabel(o.getAccountLabel());
        op.setQuantity(form.refundedUnits());
        op.setAmount(
                money(o.getUnitCharge().multiply(BigDecimal.valueOf(form.refundedUnits())))
                        .min(o.getPaidAmount().subtract(o.getRefundedAmount())));
        op.setResolvedBy(actor);
        op.setResolutionNote(form.evidence().trim());
        return persistQuote(op);
    }

    @Override
    public QuoteView confirmRefundSettlement(String id) {
        Long actor = financeAdmin();
        uuid(id);
        return tx(
                () -> {
                    ServiceOperation op = operationMapper.lock(id);
                    if (op == null
                            || !"SETTLE_REFUND".equals(op.getAction())
                            || !actor.equals(op.getResolvedBy())) throw missing();
                    if ("SUCCEEDED".equals(op.getState())) return quoteView(op);
                    if (!"READY".equals(op.getState())
                            || !op.getExpiresAt().isAfter(ServiceTime.now()))
                        throw bad("退款预览已过期，请重新核对");
                    ServiceOrder o = orderMapper.lock(op.getOrderId());
                    if (o == null || !Objects.equals(op.getOrderVersion(), o.getVersion()))
                        throw bad("订单状态已变化，请刷新后重新核对");
                    requireRefundReview(o);
                    applyRefund(op, o, op.getQuantity());
                    completeOperation(op, o);
                    return quoteView(op);
                });
    }

    private Long financeAdmin() {
        Long actor = user();
        admin();
        SecurityUtils.requireAuthority("payment:reconcile");
        return actor;
    }

    private void requireRefundReview(ServiceOrder o) {
        noPending(o);
        if (totalDistance(o.getProviderType())) throw bad("总公里计划不支持按次数核对退款");
        if (!"REFUND_REVIEW".equals(o.getStatus()) || o.getExternalOrderNo() == null)
            throw bad("仅可为已记录退款状态、且没有待处理操作的订单核对入账");
    }

    private static void evidence(boolean checked, String note) {
        if (!checked
                || note == null
                || note.trim().length() < 10
                || note.length() > 1000
                || note.codePoints().anyMatch(Character::isISOControl))
            throw bad("请先核实实际处理结果和资金记录，并填写不含密码的核对依据");
    }

    @Override
    public QuoteView resolve(String operationId, ResolveForm form) {
        Long actor = user();
        admin();
        SecurityUtils.requireAuthority("payment:reconcile");
        uuid(operationId);
        if (form == null || !form.upstreamChecked()
                || form.evidence() == null
                || form.evidence().trim().length() < 10
                || form.evidence().length() > 1000
                || form.evidence().codePoints().anyMatch(Character::isISOControl))
            throw bad("请先核实实际处理结果和资金记录，并填写不含密码的核对依据");
        IdentityRecovery recovery = prepareIdentityRecovery(operationId, form);
        return tx(
                () -> {
                    ServiceOperation op = operationMapper.lock(operationId);
                    if (op == null) throw missing();
                    if ("DISPATCHING".equals(op.getState())
                            && op.getUpdateTime().isBefore(ServiceTime.now().minusMinutes(5)))
                        op.setState("UNKNOWN");
                    if (!"UNKNOWN".equals(op.getState())) throw bad("仅可核对结果不确定的操作；处理中至少等待五分钟");
                    ServiceOrder o = orderMapper.lock(op.getOrderId());
                    if (o == null || !operationId.equals(o.getPendingOperationId()))
                        throw bad("操作已被处理");
                    if (totalDistance(o.getProviderType()) && form.refundedUnits() != null)
                        throw bad("总公里提交核对不接受退款次数");
                    if ("leidian".equals(o.getProviderType()) && form.refundedUnits() != null)
                        throw bad("取消或下单核对不接受退款次数；退款须单独核对入账");
                    if (jingyu(o.getProviderType()) && form.refundedUnits() != null
                            && !("REFUND".equals(op.getAction()) && "ACCEPTED".equals(form.outcome())))
                        throw bad("只有退款已受理核对可填写退款次数");
                    if (identityRecovery(o, op) && "ACCEPTED".equals(form.outcome())) {
                        if (recovery == null || !sameStatusTarget(recovery.order(), o)
                                || !Objects.equals(recovery.operation().getUpdateTime(), op.getUpdateTime())
                                || !Objects.equals(recovery.order().getScheduleJson(), o.getScheduleJson())
                                || !Objects.equals(recovery.order().getQuantity(), o.getQuantity()))
                            throw bad("订单在核对期间已变化，请重新读取编号");
                        ApiProvider current = active(o.getProviderId(), o.getProviderType(), recovery.providerVersion());
                        if (!identity(current).equals(o.getProviderIdentity())) throw bad("服务配置已变化，请重新核对");
                    }
                    op.setResolvedBy(actor);
                    op.setResolutionNote(form.evidence().trim());
                    requireWrite(operationMapper.updateById(op));
                    if ("ACCEPTED".equals(form.outcome())) {
                        // Explicit privileged upstream attestation, audited; never infer acceptance
                        // from a timeout.
                        RemoteResult accepted;
                        if (identityRecovery(o, op)) accepted = recovery.result();
                        else if ("leidian".equals(o.getProviderType()) && "CANCEL".equals(op.getAction()))
                            accepted = new RemoteResult(o.getExternalOrderNo(), "REFUND_REVIEW", o.getCompleted(),
                                    null, o.getExternalSubOrderNo());
                        else accepted = new RemoteResult(
                                "CREATE".equals(op.getAction()) ? form.externalOrderNo() : o.getExternalOrderNo(),
                                totalDistance(o.getProviderType()) ? "SUBMITTED" : "ACTIVE", null, form.refundedUnits());
                        finish(operationId, accepted);
                    } else if ("NOT_ACCEPTED".equals(form.outcome())) {
                        if (isDebit(op) && op.getAmount().signum() > 0)
                            ledger.credit(
                                    op.getUserId(),
                                    op.getAmount(),
                                    AccountLedgerServiceImpl.BIZ_REFUND,
                                    "SERVICE_CANCEL:" + operationId,
                                    "核实未受理，退回服务订单扣款",
                                    false);
                        if ("CREATE".equals(op.getAction())) {
                            o.setStatus("CANCELLED");
                            o.setRefundedAmount(o.getPaidAmount());
                        }
                        o.setPendingOperationId(null);
                        o.setVersion(o.getVersion() + 1);
                        o.setUpdateTime(ServiceTime.now());
                        requireWrite(orderMapper.updateById(o));
                        op.setState("NOT_ACCEPTED");
                        op.setPayloadEncrypted(null);
                        touch(op);
                        requireWrite(operationMapper.updateById(op));
                    } else throw bad("核对结论不合法");
                    return quoteView(operationMapper.selectById(operationId));
                });
    }

    private record IdentityRecovery(ServiceOperation operation, ServiceOrder order, Long providerVersion, RemoteResult result) {}

    /** Identity-bound reads stay outside ledger/order locks and never repeat a business write. */
    private IdentityRecovery prepareIdentityRecovery(String operationId, ResolveForm form) {
        if (!"ACCEPTED".equals(form.outcome())) return null;
        ServiceOperation operation = operationMapper.selectById(operationId);
        if (operation == null) throw missing();
        ServiceOrder order = orderMapper.selectById(operation.getOrderId());
        if (order == null || !operationId.equals(order.getPendingOperationId())) throw bad("操作已被处理");
        if (!identityRecovery(order, operation)) {
            if (form.externalSubOrderNo() != null && !form.externalSubOrderNo().isBlank())
                throw bad("此操作不接受额外的记录编号");
            return null;
        }
        if (!("UNKNOWN".equals(operation.getState()) || "DISPATCHING".equals(operation.getState())
                && operation.getUpdateTime().isBefore(ServiceTime.now().minusMinutes(5))))
            throw bad("仅可核对结果不确定的操作；处理中至少等待五分钟");
        boolean create = "CREATE".equals(operation.getAction());
        boolean refund = jingyu(order.getProviderType()) && "REFUND".equals(operation.getAction());
        if (refund ? form.refundedUnits() == null || form.refundedUnits() < 0 || form.refundedUnits() > operation.getQuantity()
                : form.refundedUnits() != null) throw bad("请按实际处理结果核对退款次数，不可超过预览上限");
        ServiceOrder candidate = new ServiceOrder();
        org.springframework.beans.BeanUtils.copyProperties(order, candidate);
        if (create) {
            if (form.externalOrderNo() == null || !form.externalOrderNo().matches("[A-Za-z0-9_-]{1,64}")
                    || form.externalSubOrderNo() == null || !form.externalSubOrderNo().matches("[1-9][0-9]{0,18}"))
                throw bad("请填写并核实订单编号和记录编号");
            candidate.setExternalOrderNo(form.externalOrderNo());
            candidate.setExternalSubOrderNo(form.externalSubOrderNo());
        } else if (form.externalOrderNo() != null && !form.externalOrderNo().isBlank()
                || form.externalSubOrderNo() != null && !form.externalSubOrderNo().isBlank()) {
            throw bad("请使用订单已保存的编号核对，不可另填编号");
        }
        ApiProvider provider = forOrder(order);
        Long version = provider.getConfigVersion();
        RemoteResult result = gateway.sync(provider, candidate);
        if (jingyu(order.getProviderType())) requireJingyuResult(candidate, result, false);
        else requireLeidianResult(candidate, result, false);
        if (!candidate.getExternalOrderNo().equals(result.externalOrderNo())
                || !candidate.getExternalSubOrderNo().equals(result.externalSubOrderNo())) throw bad("核对编号不一致");
        if (refund) {
            if (!"REFUND_REVIEW".equals(result.status()) || form.refundedUnits() > order.getQuantity() - result.completed())
                throw bad("退款状态或次数无法核实，请重新核对实际结果");
            // Only the privileged, audited attestation supplies refund units; a status read supplies none.
            result = new RemoteResult(result.externalOrderNo(), result.status(), result.completed(),
                    form.refundedUnits(), result.externalSubOrderNo());
        }
        return new IdentityRecovery(operation, order, version, result);
    }

    /** Local-only expiry/recovery; never dispatches or repeats an upstream operation. */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelay = 3600000,
            initialDelay = 3600000)
    public void expireQuotes() {
        if (!enabled) return;
        operationMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                                ServiceOperation>()
                        .eq(ServiceOperation::getState, "READY")
                        .lt(ServiceOperation::getExpiresAt, ServiceTime.now())
                        .set(ServiceOperation::getState, "EXPIRED")
                        .set(ServiceOperation::getPayloadEncrypted, null)
                        .set(ServiceOperation::getUpdateTime, ServiceTime.now()));
        operationMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                                ServiceOperation>()
                        .eq(ServiceOperation::getState, "DISPATCHING")
                        .lt(ServiceOperation::getUpdateTime, ServiceTime.now().minusMinutes(5))
                        .set(ServiceOperation::getState, "UNKNOWN")
                        .set(ServiceOperation::getErrorCategory, "PROCESS_INTERRUPTED")
                        .set(ServiceOperation::getUpdateTime, ServiceTime.now()));
        operationMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                                ServiceOperation>()
                        .eq(ServiceOperation::getState, "UNKNOWN")
                        .lt(ServiceOperation::getCreateTime, ServiceTime.now().minusDays(7))
                        .isNotNull(ServiceOperation::getPayloadEncrypted)
                        .set(ServiceOperation::getPayloadEncrypted, null));
    }

    private ServiceOperation newOperation(
            Long uid,
            ServiceProduct p,
            Long providerVersion,
            String action,
            String orderId,
            Long version) {
        ServiceOperation op = new ServiceOperation();
        op.setId(UUID.randomUUID().toString());
        op.setOrderId(orderId);
        op.setUserId(uid);
        op.setProductId(p.getId());
        op.setProductVersion(p.getVersion());
        op.setProviderVersion(providerVersion);
        op.setOrderVersion(version);
        op.setAction(action);
        op.setState("READY");
        op.setExpiresAt(ServiceTime.now().plusMinutes(5));
        op.setCreateTime(ServiceTime.now());
        touch(op);
        return op;
    }

    private ApiProvider forOrder(ServiceOrder o) {
        ApiProvider p = active(o.getProviderId(), o.getProviderType(), null);
        if (!identity(p).equals(o.getProviderIdentity())) throw bad(totalDistance(o.getProviderType())
                ? "服务配置或访问密钥已变更，旧订单须先人工核对"
                : "服务配置已变更，旧订单须先人工核对");
        return p;
    }

    private ApiProvider active(Long id, String type, Long version) {
        ApiProvider p = providers.loadDecrypted(id);
        if (p == null
                || !Integer.valueOf(1).equals(p.getStatus())
                || p.getVerifiedAt() == null
                || (type != null && !type.equals(p.getProviderType()))
                || (version != null && !version.equals(p.getConfigVersion())))
            throw new ProviderRequestException(ProviderRequestException.Reason.PROVIDER_NOT_ACTIVE);
        return p;
    }

    private static boolean daily(ServiceProduct p) {
        return "sxdk_tw".equals(p.getProviderType());
    }

    private void attestContract(ServiceProduct product, ApiProvider provider, ContractPriceForm c) {
        if (c == null) throw bad("该服务暂无可核实的实时报价，请填写已核实的合同单价和依据");
        evidence(c.upstreamChecked(), c.evidence());
        var today = ServiceTime.now().toLocalDate();
        if (c.unitCost() == null
                || c.unitCost().signum() < 0
                || c.unitCost().compareTo(new BigDecimal("9999")) > 0
                || c.unitCost().stripTrailingZeros().scale() > 6
                || c.validUntil() == null
                || c.validUntil().isBefore(today)
                || c.validUntil().isAfter(today.plusDays(90))) throw bad("合同单价或有效期不合法（最长九十天）");
        product.setContractUnitCost(c.unitCost());
        product.setContractValidUntil(c.validUntil());
        product.setContractEvidence(c.evidence().trim());
        product.setContractReviewedBy(SecurityUtils.getCurrentUserId());
        product.setContractReviewedAt(ServiceTime.now());
        product.setContractProviderIdentity(identity(provider));
    }

    private boolean contractCurrent(ServiceProduct p, ApiProvider provider) {
        return p.getContractUnitCost() != null
                && p.getContractValidUntil() != null
                && !p.getContractValidUntil().isBefore(ServiceTime.now().toLocalDate())
                && p.getContractReviewedBy() != null
                && identity(provider).equals(p.getContractProviderIdentity());
    }

    private void requireContract(ServiceProduct p, ApiProvider provider) {
        if (!contractCurrent(p, provider)) throw bad("合同价格未核实、已过期或配置身份变更，请管理员重新核对");
    }

    private static String planJson(Object plan) {
        try {
            return JSON.writeValueAsString(plan);
        } catch (Exception ex) {
            throw bad("计划快照无法保存");
        }
    }

    private static void limitRunCalendarDeadline(ServiceOperation op) {
        var midnight = op.getCreateTime().toLocalDate().plusDays(1).atStartOfDay();
        if (!midnight.isAfter(ServiceTime.now())) throw bad("已跨过北京时间零点，请重新预览执行安排");
        if (midnight.isBefore(op.getExpiresAt())) op.setExpiresAt(midnight);
    }

    private static void limitCalendarDeadline(ServiceOperation op) {
        var midnight = op.getCreateTime().toLocalDate().plusDays(1).atStartOfDay();
        if (!midnight.isAfter(ServiceTime.now())) throw bad("已跨过北京时间零点，请重新预览服务天数");
        if (midnight.isBefore(op.getExpiresAt())) op.setExpiresAt(midnight);
    }

    private BigDecimal catalogPrice(ServiceProduct p, ApiProvider provider) {
        if (daily(p)) {
            requireContract(p, provider);
            return p.getContractUnitCost();
        }
        var connector = catalogs.getConnector(p.getProviderType());
        if (connector == null) throw bad("不支持的服务类型");
        return connector
                .fetchCatalog(provider, Set.of("flash", "leidian", "jingyu").contains(p.getProviderType()) ? p.getProject() : null)
                .stream()
                .filter(item -> p.getRemoteProductId().equals(item.id()))
                .map(PluginProduct::unitPrice)
                .findFirst()
                .orElseThrow(() -> bad("服务目录中未找到该商品"));
    }

    private ProductView productView(ServiceProduct p, boolean admin) {
        ApiProvider provider = providerMapper.selectById(p.getProviderId());
        boolean available =
                Boolean.TRUE.equals(p.getEnabled())
                        && provider != null
                        && Integer.valueOf(1).equals(provider.getStatus())
                        && provider.getVerifiedAt() != null
                        && p.getProviderType().equals(provider.getProviderType())
                        && (!daily(p) || contractCurrent(p, provider))
                        && (!PhpNativeServiceGateway.isHeishaFace(p)
                                || accounts.faceCollectionConfigured());
        return new ProductView(
                p.getId(),
                admin ? p.getProviderId() : null,
                p.getProviderType(),
                p.getProject(),
                p.getRemoteProductId(),
                p.getTitle(),
                p.getDescription(),
                plain(p.getUnitPrice()),
                jingyu(p.getProviderType()) ? ("bdlp".equals(p.getProject()) ? "元/次" : "元/次·公里") : "leidian".equals(p.getProviderType()) ? "元/次·公里" : "appui".equals(p.getProviderType()) ? "元/天" : daily(p)
                        ? "元/服务日"
                        : ("wuxin".equals(p.getProviderType())
                                        || ("flash".equals(p.getProviderType())
                                                && "sdxy".equals(p.getProject())))
                                ? "元/次"
                                : "元/公里",
                Boolean.TRUE.equals(p.getEnabled()),
                available,
                p.getVersion(),
                PhpNativeServiceGateway.capabilities(p.getProviderType()),
                admin && daily(p)
                        ? new ContractPriceView(
                                plain(p.getContractUnitCost()),
                                p.getContractValidUntil(),
                                p.getContractEvidence(),
                                p.getContractReviewedBy(),
                                p.getContractReviewedAt())
                        : null);
    }

    private OrderView orderView(ServiceOrder o) {
        return new OrderView(
                o.getId(),
                o.getTitle(),
                o.getAccountLabel(),
                o.getProviderType(),
                o.getProject(),
                o.getPendingOperationId() != null ? "CONFIRMING" : o.getStatus(),
                o.getQuantity(),
                "sxdk_tw".equals(o.getProviderType()) || totalDistance(o.getProviderType())
                                || ("flash".equals(o.getProviderType())
                                        && !"COMPLETED".equals(o.getStatus()))
                        ? null
                        : o.getCompleted(),
                o.getDistance() == null ? null : plain(o.getDistance()),
                plain(o.getPaidAmount()),
                plain(o.getRefundedAmount()),
                o.getPendingOperationId(),
                o.getCreateTime(),
                o.getVersion(),
                actions(o),
                "sxdk_tw".equals(o.getProviderType())
                        ? InternshipNativeServiceGateway.plan(o).schedule()
                        : null,
                Set.of("sxdk_tw", "appui").contains(o.getProviderType()) ? "天" : totalDistance(o.getProviderType()) ? "单" : "次",
                totalDistance(o.getProviderType()) ? distancePlan(o.getScheduleJson()) : null,
                PERIODIC_TYPES.contains(o.getProviderType()) && !leidianRecordRemoved(o)
                        ? new StatusCheckView(o.getStatusCheckedAt(), "RETRY".equals(o.getStatusCheckState())) : null);
    }

    private List<String> actions(ServiceOrder o) {
        if (o.getPendingOperationId() != null
                || FINAL.contains(o.getStatus())
                || "REFUND_REVIEW".equals(o.getStatus())
                || o.getExternalOrderNo() == null) return List.of();
        List<String> actions =
                new ArrayList<>(PhpNativeServiceGateway.capabilities(o.getProviderType()));
        actions.removeAll(List.of("CREATE", "LOOKUP", "SYNC"));
        if ("PAUSED".equals(o.getStatus())) actions.remove("PAUSE");
        // ATTENTION may coexist with a paused task list. Do not strand a paused Jingyu order;
        // the adapter rechecks the actual pause flag before either action, with no blind toggle.
        else if (!(jingyu(o.getProviderType()) && "ATTENTION".equals(o.getStatus()))) actions.remove("RESUME");
        if ("COMPLETED".equals(o.getStatus()))
            actions.removeAll(
                    List.of(
                            "PAUSE",
                            "RESUME",
                            "DELAY",
                            "DELAY_TASK",
                            "REFUND",
                            "CHANGE_TIME",
                            "EDIT_PLAN",
                            "REASSIGN"));
        if (Set.of("sxdk_tw", "appui").contains(o.getProviderType()) && "COMPLETED".equals(o.getStatus())) {
            actions.remove("RUN_NOW");
            if (!actions.contains("REFUND")) actions.add("REFUND");
        }
        return List.copyOf(actions);
    }

    private QuoteView quoteView(ServiceOperation op) {
        ServiceProduct p = product(op.getProductId());
        String state =
                "READY".equals(op.getState()) && !op.getExpiresAt().isAfter(ServiceTime.now())
                        ? "EXPIRED"
                        : op.getState();
        return new QuoteView(
                op.getId(),
                "CREATE".equals(op.getAction()) && Set.of("READY", "EXPIRED").contains(state)
                        ? null
                        : op.getOrderId(),
                op.getAction(),
                state,
                p.getTitle(),
                op.getQuantity(),
                plain(op.getAmount()),
                Set.of("REFUND", "SETTLE_REFUND").contains(op.getAction())
                        ? ("SUCCEEDED".equals(op.getState()) ? "已退回账户余额" : "预计退款上限（按实际核实数量结算）")
                        : isDebit(op) && op.getAmount().signum() > 0 ? "本次余额扣款" : "无需额外扣款",
                op.getExpiresAt(),
                op.getErrorCategory(),
                (daily(p) && !Set.of("RUN_NOW", "REPORT").contains(op.getAction()))
                        || "appui".equals(p.getProviderType()) ? "天"
                        : totalDistance(p.getProviderType()) ? "单" : "次",
                totalDistance(p.getProviderType()) ? distancePlan(op.getScheduleJson()) : null,
                op.getQuantity() > 0 && Set.of("CREATE", "ADD_TIMES", "EDIT_SCHEDULE", "RUN_NOW",
                        "REFUND", "SETTLE_REFUND").contains(op.getAction()) ? plain(op.getUnitCharge()) : null);
    }

    private ServiceProduct product(Long id) {
        ServiceProduct p = productMapper.selectById(id);
        if (p == null) throw missing();
        return p;
    }

    private ServiceProduct forSale(Long id) {
        ServiceProduct p = product(id);
        if (!Boolean.TRUE.equals(p.getEnabled())) throw bad("该商品已下架");
        return p;
    }

    private ServiceOrder owned(String id) {
        uuid(id);
        ServiceOrder o = orderMapper.selectById(id);
        if (o == null || !SecurityUtils.getCurrentUserId().equals(o.getUserId())) throw missing();
        return o;
    }

    private void own(ServiceOperation op, Long uid) {
        if (op == null || !uid.equals(op.getUserId())) throw missing();
    }

    private Long user() {
        Long id = SecurityUtils.getCurrentUserId();
        if (!enabled) throw bad("服务商城尚未启用，请联系管理员完成原生订单迁移与配置");
        return id;
    }

    private void admin() {
        SecurityUtils.requireAuthority("api-provider:update");
    }

    private void noPending(ServiceOrder o) {
        if (o.getPendingOperationId() != null) throw bad("上一笔操作仍待确认，请勿重复下单或退款");
    }

    private void limitQuotes(Long uid) {
        long count =
                operationMapper.selectCount(
                        new LambdaQueryWrapper<ServiceOperation>()
                                .eq(ServiceOperation::getUserId, uid)
                                .eq(ServiceOperation::getState, "READY")
                                .gt(ServiceOperation::getExpiresAt, ServiceTime.now()));
        if (count >= 20) throw bad("未确认的预览过多，请稍后重试");
    }

    private String encrypt(Map<String, Object> fields) {
        try {
            return SecretCrypto.encrypt(JSON.writeValueAsString(fields), cryptoSecret);
        } catch (Exception ex) {
            throw bad("无法安全保存订单参数");
        }
    }

    private Map<String, Object> decrypt(String cipher) {
        try {
            if (!SecretCrypto.isEncrypted(cipher)) throw bad("订单参数不可用");
            return JSON.readValue(
                    SecretCrypto.decrypt(cipher, cryptoSecret), new TypeReference<>() {});
        } catch (Exception ex) {
            throw bad("订单参数不可用，请人工核对");
        }
    }

    private String identity(ApiProvider p) {
        try {
            if (totalDistance(p.getProviderType())) {
                // P05 has no independent UID/account probe. A different token may name a different
                // account with the same receipt IDs. HMAC, not a public hash of a possibly weak key.
                if (p.getApiKey() == null || p.getApiKey().isBlank()) throw bad("服务访问密钥未配置");
                String address = new com.course.platform.infra.http.ProviderUrlNormalizer()
                        .normalize(p.getApiUrl(), p.getProviderType()).toASCIIString();
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(
                        cryptoSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(
                        ("native-service:ssbenz_xbd:v1\n" + address + "\n" + p.getApiKey())
                                .getBytes(StandardCharsets.UTF_8)));
            }
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (p.getProviderType()
                                                            + "\n"
                                                            + p.getApiUrl()
                                                            + "\n"
                                                            + p.getUsername())
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            if (totalDistance(p.getProviderType())) throw bad("无法安全核实服务配置，请联系管理员");
            throw new IllegalStateException("Cannot fingerprint provider");
        }
    }

    private static void limitTaskDeadline(ServiceOperation op, List<String> times) {
        if (times == null || times.isEmpty()) throw bad("缺少任务时间");
        try {
            var formatter =
                    java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
            for (String time : times) {
                var deadline = java.time.LocalDateTime.parse(time, formatter);
                if (!deadline.isAfter(ServiceTime.now())) throw bad("任务时间已过，请重新选择未来时间");
                if (deadline.isBefore(op.getExpiresAt())) op.setExpiresAt(deadline);
            }
        } catch (java.time.DateTimeException | NullPointerException ex) {
            throw bad("任务时间格式错误");
        }
    }

    private static boolean totalDistance(String type) {
        return SsbenzDistanceGateway.TYPE.equals(type);
    }

    private static TotalDistancePlan distancePlan(String snapshot) {
        try {
            if (snapshot == null) throw bad("缺少总公里计划快照");
            return JSON.readValue(snapshot, TotalDistancePlan.class);
        } catch (Exception ex) {
            throw bad("总公里计划快照无法读取，请联系管理员核对");
        }
    }

    private static void validateOrderForm(OrderForm form, ServiceProduct product) {
        boolean daily = daily(product);
        boolean distanceOrder = totalDistance(product.getProviderType());
        boolean dayQuota = "appui".equals(product.getProviderType());
        boolean jingyu = jingyu(product.getProviderType());
        if (form == null || !form.authorizedAccount()) throw bad("请确认授权使用该账号");
        if (distanceOrder) {
            TotalDistancePlan.distance(form.distance());
            if (form.quantity() != 1 || form.schedule() != null || form.accountSessionId() != null
                    || (form.taskTimes() != null && !form.taskTimes().isEmpty()))
                throw bad("总公里计划每次仅提交一单，不接受次数或任务列表");
        }
        if ("leidian".equals(product.getProviderType()) && (form.quantity() < 1 || form.quantity() > 100
                || form.distance() == null || form.distance().compareTo(BigDecimal.ONE) < 0
                || form.distance().compareTo(BigDecimal.TEN) > 0 || form.distance().stripTrailingZeros().scale() > 1
                || form.schedule() != null || form.accountSessionId() != null
                || form.taskTimes() != null && !form.taskTimes().isEmpty())) throw bad("请选择 1–100 次，每次 1–10 公里，最多一位小数");
        if (jingyu && (form.quantity() < 1 || form.quantity() > 365
                || form.distance() == null || form.distance().compareTo(BigDecimal.ONE) < 0
                || form.distance().compareTo(new BigDecimal("100")) > 0 || form.distance().stripTrailingZeros().scale() > 1
                || form.schedule() != null || form.accountSessionId() != null || form.taskTimes() == null
                || form.taskTimes().size() != form.quantity())) throw bad("请选择 1–365 次，每次 1–100 公里，并安排每次任务时间");
        if (dayQuota && (form.quantity() < 1 || form.quantity() > 365 || form.distance() != null
                || form.schedule() != null || form.accountSessionId() != null
                || form.taskTimes() != null && !form.taskTimes().isEmpty()))
            throw bad("天数订单参数不正确");
        if ((!daily && !distanceOrder && !dayQuota && !jingyu
                        && (form.quantity() < 1
                                || form.quantity() > 365
                                || form.distance() == null
                                || form.distance().compareTo(new BigDecimal("0.1")) < 0
                                || form.distance().compareTo(new BigDecimal("50")) > 0
                                || form.distance().stripTrailingZeros().scale() > 2
                                || form.schedule() != null))
                || (daily
                        && (form.quantity() != 0
                                || form.distance() != null
                                || form.schedule() == null
                                || (form.taskTimes() != null && !form.taskTimes().isEmpty())))
                || form.fields() == null
                || form.fields().size() > (daily ? 64 : 24)
                || (form.taskTimes() != null && form.taskTimes().size() > 365))
            throw bad("订单参数不合法");
        form.fields()
                .forEach(
                        (key, value) -> {
                            if (key == null
                                    || key.length() > 40
                                    || value == null
                                    || value.length() > 2048
                                    || value.codePoints().anyMatch(Character::isISOControl))
                                throw bad("订单字段格式错误");
                        });
    }

    private static void bounds(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw bad("分页参数超出范围");
    }

    private static void uuid(String id) {
        if (id == null
                || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw missing();
    }

    private static BigDecimal exactUnitCharge(BigDecimal price, BigDecimal units) {
        if (price == null || units == null || price.signum() <= 0 || units.signum() <= 0)
            throw bad("计费单价不可用，请重新预览");
        try {
            BigDecimal charge = price.multiply(units).setScale(8, RoundingMode.UNNECESSARY);
            if (charge.precision() - charge.scale() > 10)
                throw bad("计费单价超出支持范围，请重新预览");
            return charge;
        } catch (ArithmeticException ex) {
            throw bad("计费单价精度不合法，请重新预览");
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        BigDecimal m = amount.setScale(2, RoundingMode.HALF_UP);
        if (m.signum() < 0 || m.compareTo(new BigDecimal("9999999.99")) > 0) throw bad("订单金额超出范围");
        return m;
    }

    private static boolean isDebit(ServiceOperation op) {
        return Set.of("CREATE", "ADD_TIMES", "EDIT_SCHEDULE", "RUN_NOW").contains(op.getAction());
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0.00" : v.toPlainString();
    }

    private static void requireWrite(int rows) {
        if (rows != 1) throw bad("数据已变化，请刷新后重试");
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
    }

    private static void stamp(ServiceProduct p) {
        if (p.getCreateTime() == null) p.setCreateTime(ServiceTime.now());
        p.setUpdateTime(ServiceTime.now());
    }

    private static void touch(ServiceOperation op) {
        op.setUpdateTime(ServiceTime.now());
    }

    private <T> T tx(Supplier<T> task) {
        return new TransactionTemplate(transactions).execute(status -> task.get());
    }

    private static <S, T> IPage<T> convert(IPage<S> page, List<T> rows) {
        Page<T> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(rows);
        return result;
    }
}
