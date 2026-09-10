package com.course.platform.service.impl;

import com.course.platform.application.service.orderreceipt.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.orderreceipt.OrderReceiptRecovery;
import com.course.platform.domain.orderreceipt.OrderReceiptTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.cache.SystemVariableCache;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class OrderReceiptRecoveryServiceImpl implements OrderReceiptRecoveryService {
    private final OrderReceiptRecoveryMapper recoveries;
    private final CourseOrderMapper orders;
    private final CoursePlatformMapper platforms;
    private final ApiProviderMapper providers;
    private final UserMapper users;
    private final UserAuthorityService authorities;
    private final OrderReceiptGateway gateway;
    private final ProviderUrlNormalizer urls;
    private final Validator validator;
    private final PlatformTransactionManager transactions;
    @Value("${app.native-services.enabled:false}") private boolean enabled;
    @Value("${app.crypto.secret}") private String cryptoSecret;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> PERMISSIONS = Set.of("order:update", "api-provider:update");
    private record Context(CourseOrder order, CoursePlatform platform, ApiProvider provider, List<Long> aliases) {}
    private record Started(OrderReceiptRecovery recovery, Context context, boolean read) {}

    @Override
    public List<View> recent(long orderId) {
        long actor = actor();
        if (orderId < 1) throw bad("订单编号格式不正确");
        return recoveries.recent(actor, orderId).stream().map(this::view).toList();
    }

    @Override
    public View preview(long orderId, PreviewForm form) {
        long actor = actor();
        if (orderId < 1 || form == null || !validator.validate(form).isEmpty()
                || !form.ownershipConfirmed() || form.evidence().codePoints().anyMatch(Character::isISOControl)
                || form.evidence().trim().length() < 10) throw bad("请提供执行编号、核对依据并确认订单归属");
        String requestHash = hash(orderId, form.receiptId(), form.evidence().trim());
        Started start;
        try {
            start = transaction(() -> {
                actor();
                var existing = recoveries.lock(form.requestId());
                if (existing != null) {
                    own(existing, actor, orderId);
                    if (!existing.getRequestHash().equals(requestHash)) throw conflict("该请求编号已用于不同的核对内容");
                    expire(existing);
                    return new Started(existing, null, false);
                }
                Context context = context(orderId);
                eligible(context);
                unclaimed(context, form.receiptId());
                var order = context.order();
                var now = ServiceTime.now();
                var row = new OrderReceiptRecovery();
                row.setId(form.requestId()); row.setActorId(actor); row.setOrderId(orderId); row.setOwnerId(order.getUserId());
                row.setOrderNo(order.getOrderNo()); row.setCourseName(order.getCourseName()); row.setReceiptId(form.receiptId());
                row.setProviderId(context.provider().getId()); row.setState("READING"); row.setRequestHash(requestHash);
                row.setOrderHash(orderHash(order)); row.setPlatformHash(platformHash(context.platform()));
                row.setProviderHash(providerHash(context.provider())); row.setSourceIdentity(source(context.provider()));
                row.setEvidenceEncrypted(encryptEvidence(form.evidence().trim()));
                row.setExpiresAt(now.plusMinutes(5)); row.setCreateTime(now); row.setUpdateTime(now);
                if (recoveries.insert(row) != 1) throw conflict("核对请求保存失败，未发起查询");
                return new Started(row, context, true);
            });
        } catch (org.springframework.dao.DuplicateKeyException e) {
            var row = recoveries.selectById(form.requestId());
            own(row, actor, orderId);
            if (!row.getRequestHash().equals(requestHash)) throw conflict("该请求编号已用于不同的核对内容");
            return view(row);
        }
        if (!start.read()) return view(start.recovery());
        // READING is durable before exactly one read. No database locks or transaction span HTTP.
        boolean verified;
        try {
            Context context = start.context();
            var result = gateway.verify(runtime(context.provider()), context.platform(), context.order(), form.receiptId());
            verified = result != null && form.receiptId().equals(result.receiptId());
        } catch (Exception e) {
            verified = false; // Read failure must not expose raw response/credentials or authorize association.
        }
        final boolean matched = verified;
        return transaction(() -> {
            actor();
            var row = recoveries.lock(form.requestId()); own(row, actor, orderId);
            expire(row);
            if (!"READING".equals(row.getState())) return view(row);
            if (!matched) return transition(row, "READ_FAILED");
            try {
                Context current = context(orderId); eligible(current); unclaimed(current, row.getReceiptId());
                if (!same(row, current)) return transition(row, "CONFLICT");
            } catch (BusinessException e) { return transition(row, "CONFLICT"); }
            // Lock waits can outlive the preview; check the deadline again before authorizing confirmation.
            expire(row);
            return "READING".equals(row.getState()) ? transition(row, "READY") : view(row);
        });
    }

    @Override
    public View get(long orderId, String requestId) {
        long actor = actor(); requestId(requestId);
        return transaction(() -> {
            var row = recoveries.lock(requestId); own(row, actor, orderId); expire(row);
            return view(row);
        });
    }

    @Override
    public View confirm(long orderId, String requestId, ConfirmForm form) {
        long actor = actor(); requestId(requestId);
        if (form == null || !form.consent()) throw bad("请确认仅恢复执行编号，不重新下单或扣费");
        try {
            return transaction(() -> {
                actor();
                var row = recoveries.lock(requestId); own(row, actor, orderId); expire(row);
                if (!"READY".equals(row.getState())) return view(row);
                try {
                    Context current = context(orderId); eligible(current); unclaimed(current, row.getReceiptId());
                    if (!same(row, current)) return transition(row, "CONFLICT");
                } catch (BusinessException e) { return transition(row, "CONFLICT"); }
                expire(row);
                if (!"READY".equals(row.getState())) return view(row);
                row.setUpdateTime(ServiceTime.now());
                if (recoveries.claim(row) != 1 || recoveries.bind(orderId, row.getReceiptId(), row.getUpdateTime()) != 1)
                    throw new org.springframework.dao.DataIntegrityViolationException("Receipt association changed");
                row.setAppliedAt(row.getUpdateTime());
                return transition(row, "APPLIED");
            });
        } catch (DataAccessException e) {
            // Duplicate source claims/deadlocks roll back both the link and audit. Never replay the lookup.
            return transaction(() -> {
                actor(); var row = recoveries.lock(requestId); own(row, actor, orderId);
                return "READY".equals(row.getState()) ? transition(row, "CONFLICT") : view(row);
            });
        }
    }

    private Context context(long orderId) {
        CourseOrder observed = orders.selectById(orderId);
        if (observed == null || observed.getApiProviderId() == null) throw bad("订单没有可核对的执行配置");
        ApiProvider initial = providers.selectById(observed.getApiProviderId()); requireProvider(initial);
        String identity = source(initial);
        List<ApiProvider> all = recoveries.benzProviders();
        if (all.size() > 500) throw bad("执行配置数量超出核对范围，请先清理重复配置");
        List<Long> aliases = new ArrayList<>();
        for (ApiProvider p : all) {
            try { if (identity.equals(source(p))) aliases.add(p.getId()); }
            catch (BusinessException ignored) { if (p.getId().equals(initial.getId())) throw ignored; }
        }
        if (!aliases.contains(initial.getId())) throw conflict("执行配置已变化，请重新核对");
        // All recoveries for aliased source accounts acquire provider locks before order/platform locks.
        ApiProvider locked = null;
        for (Long id : aliases.stream().sorted().toList()) {
            var p = recoveries.lockProvider(id);
            if (p == null || !identity.equals(source(p))) throw conflict("执行配置已变化，请重新核对");
            if (id.equals(initial.getId())) locked = p;
        }
        requireProvider(locked);
        CourseOrder order = recoveries.lockOrder(orderId);
        if (order == null || !Objects.equals(locked.getId(), order.getApiProviderId())) throw conflict("订单执行配置已变化");
        CoursePlatform platform = recoveries.lockPlatform(order.getPlatformId());
        if (platform == null || !Objects.equals(locked.getId(), platform.getDockApiId())) throw conflict("课程执行配置已变化");
        return new Context(order, platform, locked, aliases);
    }
    private void eligible(Context c) {
        CourseOrder order = c.order();
        if (!Integer.valueOf(0).equals(order.getIsDeleted()) || Integer.valueOf(1).equals(order.getIsSelfOperated())
                || Integer.valueOf(1).equals(c.platform().getIsSelfOperated()) || order.getThirdOrderId() != null && !order.getThirdOrderId().isBlank())
            throw bad("仅能恢复未归档、未记录执行编号的普通订单");
        int dock = order.getDockStatus() == null ? -1 : order.getDockStatus();
        if (dock != status("dock_status", "success") && dock != status("dock_status", "failed"))
            throw bad("订单仍在提交或已停止，不能恢复执行编号");
        boolean allowed = java.util.stream.Stream.of("pending", "processing", "completed", "failed", "exam_pending", "exam_processing", "exam_completed")
                .anyMatch(key -> Objects.equals(order.getOrderStatus(), status("order_status", key)));
        if (!allowed || order.getOrderNo() == null || order.getUserId() == null || !text(order.getCourseName(), 255)
                || !text(order.getStudentAccount(), 255) || !text(order.getStudentPassword(), 2048) || !text(c.platform().getDockParam(), 255))
            throw bad("订单状态或身份资料不足，不能恢复执行编号");
        List<Long> matching = recoveries.matchingOrders(c.aliases(), order.getStudentAccount(), order.getStudentPassword(),
                order.getCourseName(), c.platform().getDockParam());
        if (!matching.equals(List.of(order.getId()))) throw bad("存在多笔身份相同的订单，无法确认编号归属");
    }
    private void unclaimed(Context c, String receipt) {
        if (!recoveries.boundOrders(c.aliases(), receipt).isEmpty()
                || recoveries.claimed(source(c.provider()), receipt, c.order().getId()) != 0)
            throw conflict("该执行编号或订单已有核对记录，不能重复关联");
    }
    private boolean same(OrderReceiptRecovery row, Context c) {
        return row.getOrderHash().equals(orderHash(c.order())) && row.getPlatformHash().equals(platformHash(c.platform()))
                && row.getProviderHash().equals(providerHash(c.provider())) && row.getSourceIdentity().equals(source(c.provider()));
    }
    private String orderHash(CourseOrder o) {
        return hash(o.getId(), o.getOrderNo(), o.getUserId(), o.getPlatformId(), o.getApiProviderId(), o.getThirdOrderId(),
                o.getStudentAccount(), o.getStudentPassword(), o.getSchoolName(), o.getCourseName(), o.getCourseId(), o.getAmount(),
                o.getDockStatus(), o.getOrderStatus(), o.getRetryCount(), o.getIsDeleted(), o.getIsSelfOperated(), o.getUpdateTime());
    }
    private String platformHash(CoursePlatform p) {
        return hash(p.getId(), p.getDockApiId(), p.getDockParam(), p.getIsSelfOperated(), p.getStatus(), p.getUpdateTime());
    }
    private String providerHash(ApiProvider p) {
        return hash(p.getId(), source(p), p.getConfigVersion(), p.getStatus(), p.getApiKey(), p.getPassword(), p.getToken(), p.getCookie(), p.getVerifiedAt());
    }
    private String source(ApiProvider p) {
        try {
            if (p == null || !text(p.getUsername(), 100)) throw bad("执行账号资料不完整");
            return hash("Benz-receipt-source-v1", urls.normalize(p.getApiUrl(), "27").toASCIIString(), p.getUsername());
        } catch (Exception e) { throw bad("执行配置无法安全核对"); }
    }
    private void requireProvider(ApiProvider p) {
        if (p == null || !"27".equals(p.getProviderType()) || !Integer.valueOf(ApiProvider.STATUS_ACTIVE).equals(p.getStatus())
                || p.getConfigVersion() == null || p.getVerifiedAt() == null || !text(p.getApiKey(), 4096))
            throw bad("此订单的执行配置未通过核对或暂不支持编号恢复");
    }
    private ApiProvider runtime(ApiProvider saved) {
        ApiProvider copy = new ApiProvider(); BeanUtils.copyProperties(saved, copy);
        copy.setApiKey(SecretCrypto.decrypt(saved.getApiKey(), cryptoSecret));
        copy.setApiUrl(urls.normalize(saved.getApiUrl(), "27").toASCIIString());
        return copy;
    }
    private long actor() {
        if (!enabled) throw bad("订单编号恢复尚未开放");
        for (String permission : PERMISSIONS) SecurityUtils.requireAuthority(permission);
        long id = SecurityUtils.getCurrentUserId();
        User user = users.selectById(id);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN);
        var current = authorities.loadAuthorities(id).stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        if (!current.containsAll(PERMISSIONS)) throw new BusinessException(ResultCode.FORBIDDEN);
        return id;
    }
    private void own(OrderReceiptRecovery row, long actor, long orderId) {
        if (row == null || !Objects.equals(row.getActorId(), actor) || !Objects.equals(row.getOrderId(), orderId))
            throw new BusinessException(ResultCode.NOT_FOUND);
    }
    private void expire(OrderReceiptRecovery row) {
        if (Set.of("READING", "READY").contains(row.getState()) && !row.getExpiresAt().isAfter(ServiceTime.now()))
            transition(row, "READING".equals(row.getState()) ? "INTERRUPTED" : "EXPIRED");
    }
    private View transition(OrderReceiptRecovery row, String state) {
        row.setState(state); row.setUpdateTime(ServiceTime.now());
        if (recoveries.updateById(row) != 1) throw conflict("核对记录已变化，请检查原请求");
        return view(row);
    }
    private View view(OrderReceiptRecovery row) {
        String notice = switch (row.getState()) {
            case "READING" -> "正在核对；请检查原请求，不重复发起查询。";
            case "READY" -> "执行编号与完整订单身份一致；确认后仅关联编号，不重新下单或扣费。";
            case "APPLIED" -> "执行编号已恢复；订单金额、执行状态和进度未改变，可另行刷新执行记录。";
            case "EXPIRED" -> "核对预览已过期，未关联编号。请重新核对。";
            case "INTERRUPTED" -> "原查询未完成，未关联编号。重新核对需要使用新的请求编号。";
            case "CONFLICT" -> "订单、配置或编号归属已变化，未关联编号。请重新核对。";
            default -> "无法核实完整订单身份，未关联编号；查不到不表示订单不存在。";
        };
        return new View(row.getId(), row.getOrderId(), row.getOrderNo(), row.getCourseName(), row.getReceiptId(),
                row.getState(), row.getExpiresAt(), row.getAppliedAt(), notice);
    }
    private static boolean text(String v, int max) { return v != null && !v.isBlank() && v.length() <= max && v.codePoints().noneMatch(Character::isISOControl); }
    private static int status(String type, String key) { return SystemVariableCache.getStatusValue(type, key); }
    private static void requestId(String v) { if (v == null || !v.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw bad("请求编号格式不正确"); }
    private String encryptEvidence(String evidence) {
        try { return SecretCrypto.encrypt(JSON.writeValueAsString(Map.of("note", evidence)), cryptoSecret); }
        catch (Exception e) { throw bad("核对依据保存失败，未发起查询"); }
    }
    private String hash(Object... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cryptoSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(JSON.writeValueAsBytes(Arrays.asList(values))));
        } catch (Exception e) { throw bad("核对摘要生成失败"); }
    }
    private <T> T transaction(Supplier<T> work) {
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(s -> work.get());
    }
    private static BusinessException bad(String message) { return new BusinessException(message); }
    private static BusinessException conflict(String message) { return new BusinessException(ResultCode.CONFLICT, message); }
}
