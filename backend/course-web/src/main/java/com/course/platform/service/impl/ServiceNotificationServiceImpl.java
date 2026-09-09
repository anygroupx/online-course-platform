package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.servicenotification.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.servicecommerce.ServiceOrder;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.domain.servicenotification.*;
import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Consent → receiver proof → durable local observation outbox. Never fetches supplier progress. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceNotificationServiceImpl implements ServiceNotificationService {
    private final ServiceNotificationPreferenceMapper preferences;
    private final ServiceNotificationDeliveryMapper deliveries;
    private final ServiceOrderMapper orders;
    private final ServiceOperationMapper operations;
    private final ServiceNotificationSender sender;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    @Value("${app.native-services.notifications.enabled:false}")
    private boolean deliveryEnabled;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, String> STATES =
            Map.ofEntries(
                    Map.entry("ACTIVE", "服务进行中"),
                    Map.entry("PAUSED", "服务已暂停"),
                    Map.entry("COMPLETED", "服务周期已结束，不代表考勤成功"),
                    Map.entry("ATTENTION", "需查看上游执行记录"),
                    Map.entry("REFUNDED", "平台已记录退款"),
                    Map.entry("CANCELLED", "订单已取消"),
                    Map.entry("UNKNOWN", "结果待核对"),
                    Map.entry("REFUND_REVIEW", "退款待核对"),
                    Map.entry("PENDING", "等待上游确认"));

    @Override
    public SettingsView settings(String id) {
        var o = owned(id);
        return view(id, preferences.selectById(id), o.getUserId());
    }

    @Override
    public SettingsView configure(String id, ConfigureForm form) {
        var o = owned(id);
        requireDelivery();
        if (form == null
                || !form.consent()
                || form.token() == null
                || !form.token().matches("[A-Za-z0-9_-]{16,128}"))
            throw bad("请填写本人 ShowDoc 推送密钥（不是 URL），并确认通知授权");
        rate("configure", o.getUserId().toString(), 10, Duration.ofHours(1));
        return tx(
                () -> {
                    // Serialize initial preference creation as well as later versioned changes.
                    requireOwned(orders.lock(id), o.getUserId());
                    var p = preferences.lock(id);
                    version(p, form.version());
                    if (p == null) {
                        p = new ServiceNotificationPreference();
                        p.setOrderId(id);
                        p.setUserId(o.getUserId());
                        p.setVersion(0L);
                        p.setSequence(0L);
                        p.setCreateTime(now());
                    }
                    boolean create = p.getVersion() == 0L;
                    p.setUserId(o.getUserId());
                    p.setVersion(p.getVersion() + 1);
                    p.setTokenEncrypted(encrypt(form.token()));
                    p.setEnabled(false);
                    p.setCodeHash(null);
                    p.setChallengeDeliveryId(null);
                    p.setChallengeExpiresAt(null);
                    p.setVerifyAttempts(0);
                    p.setVerifiedAt(null);
                    p.setObservedJson(null);
                    p.setScannedAt(now());
                    p.setUpdateTime(now());
                    write(create ? preferences.insert(p) : preferences.updateById(p));
                    cancelQueued(id);
                    return view(id, p, o.getUserId());
                });
    }

    @Override
    public SettingsView challenge(String id, VersionForm form) {
        var o = owned(id);
        requireDelivery();
        consent(form);
        rate("challenge", o.getUserId().toString(), 5, Duration.ofHours(1));
        String deliveryId =
                tx(
                        () -> {
                            requireOwned(orders.lock(id), o.getUserId());
                            var p = preferences.lock(id);
                            version(p, form.version());
                            configured(p, o.getUserId());
                            if (p.getChallengeDeliveryId() != null)
                                return p.getChallengeDeliveryId();
                            if (p.getVerifiedAt() != null) throw bad("当前接收渠道已经验证");
                            String code =
                                    String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1000000));
                            p.setChallengeExpiresAt(now().plusMinutes(10));
                            p.setVerifyAttempts(0);
                            p.setCodeHash(codeHash(p, code));
                            var d =
                                    enqueue(
                                            p,
                                            "VERIFY_RECEIVER",
                                            new Message(
                                                    "实习订单通知 · 接收验证",
                                                    "接收验证码："
                                                            + code
                                                            + "（十分钟有效）。\n"
                                                            + "仅在你刚刚操作的本平台通知设置中填写；不要把验证码发给他人。\n"
                                                            + "本消息不包含上游账号、密码或考勤资料。"),
                                            p.getChallengeExpiresAt());
                            p.setChallengeDeliveryId(d.getId());
                            p.setUpdateTime(now());
                            write(preferences.updateById(p));
                            return d.getId();
                        });
        // The durable READY row authorizes at most one send; repeat requests never generate a new
        // code.
        dispatch(deliveryId);
        return settings(id);
    }

    private record Verification(boolean accepted, SettingsView view) {}

    @Override
    public SettingsView verify(String id, VerifyForm form) {
        var o = owned(id);
        requireDelivery();
        if (form == null
                || !form.consent()
                || form.code() == null
                || !form.code().matches("[0-9]{6}")) throw bad("请填写接收到的六位验证码并确认启用通知");
        rate("verify", o.getUserId().toString(), 15, Duration.ofMinutes(10));
        var result =
                tx(
                        () -> {
                            requireOwned(orders.lock(id), o.getUserId());
                            var p = preferences.lock(id);
                            version(p, form.version());
                            configured(p, o.getUserId());
                            if (p.getVerifiedAt() != null && Boolean.TRUE.equals(p.getEnabled()))
                                return new Verification(true, view(id, p, o.getUserId()));
                            if (p.getCodeHash() == null
                                    || p.getVerifyAttempts() >= 5
                                    || p.getChallengeExpiresAt() == null
                                    || !p.getChallengeExpiresAt().isAfter(now()))
                                throw bad("接收验证码已过期或尝试次数已用尽，请重新配置后发送验证通知");
                            p.setVerifyAttempts(p.getVerifyAttempts() + 1);
                            boolean matches =
                                    MessageDigest.isEqual(
                                            p.getCodeHash().getBytes(StandardCharsets.UTF_8),
                                            codeHash(p, form.code())
                                                    .getBytes(StandardCharsets.UTF_8));
                            if (matches) {
                                p.setVerifiedAt(now());
                                p.setEnabled(true);
                                p.setCodeHash(null);
                                p.setObservedJson(json(observation(orders.selectById(id))));
                                p.setScannedAt(now());
                            } else if (p.getVerifyAttempts() >= 5) p.setCodeHash(null);
                            p.setUpdateTime(now());
                            write(preferences.updateById(p));
                            return new Verification(matches, view(id, p, o.getUserId()));
                        });
        if (!result.accepted()) throw bad("接收验证码不正确，最多允许五次尝试");
        return result.view();
    }

    @Override
    public SettingsView disconnect(String id, VersionForm form) {
        var o = owned(id);
        consent(form);
        return tx(
                () -> {
                    requireOwned(orders.lock(id), o.getUserId());
                    var p = preferences.lock(id);
                    version(p, form.version());
                    if (p == null) return view(id, null, o.getUserId());
                    p.setUserId(o.getUserId());
                    p.setEnabled(false);
                    p.setTokenEncrypted(null);
                    p.setCodeHash(null);
                    p.setVerifiedAt(null);
                    p.setObservedJson(null);
                    p.setChallengeDeliveryId(null);
                    p.setChallengeExpiresAt(null);
                    p.setVerifyAttempts(0);
                    p.setVersion(p.getVersion() + 1);
                    p.setUpdateTime(now());
                    write(preferences.updateById(p));
                    cancelQueued(id);
                    return view(id, p, o.getUserId());
                });
    }

    @Override
    public IPage<DeliveryView> deliveries(String id, int page, int size) {
        var o = owned(id);
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw bad("分页参数不合法");
        var data =
                deliveries.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<ServiceNotificationDelivery>()
                                .eq(ServiceNotificationDelivery::getOrderId, id)
                                .eq(ServiceNotificationDelivery::getUserId, o.getUserId())
                                .orderByDesc(ServiceNotificationDelivery::getCreateTime)
                                .orderByDesc(ServiceNotificationDelivery::getId));
        var view = new Page<DeliveryView>(page, size, data.getTotal());
        view.setRecords(data.getRecords().stream().map(this::deliveryView).toList());
        return view;
    }

    @Override
    public DeliveryView delivery(String id) {
        Long uid = user();
        uuid(id);
        var d = deliveries.selectById(id);
        if (d == null || !uid.equals(d.getUserId())) throw missing();
        owned(d.getOrderId());
        return deliveryView(d);
    }

    public boolean deliveryActive() {
        return enabled && deliveryEnabled;
    }

    /** Called by a dedicated bounded worker, never on the shared scheduler thread. */
    public void processBatch() {
        if (!deliveryActive()) return;
        deliveries.update(
                null,
                new LambdaUpdateWrapper<ServiceNotificationDelivery>()
                        .eq(ServiceNotificationDelivery::getState, "DISPATCHING")
                        .lt(ServiceNotificationDelivery::getUpdateTime, now().minusMinutes(2))
                        .set(ServiceNotificationDelivery::getState, "UNKNOWN")
                        .set(ServiceNotificationDelivery::getPayloadEncrypted, null)
                        .set(ServiceNotificationDelivery::getUpdateTime, now()));
        // Expiry must not depend on Redis availability or a recipient's exhausted quota.
        deliveries.update(
                null,
                new LambdaUpdateWrapper<ServiceNotificationDelivery>()
                        .eq(ServiceNotificationDelivery::getState, "READY")
                        .le(ServiceNotificationDelivery::getExpiresAt, now())
                        .set(ServiceNotificationDelivery::getState, "EXPIRED")
                        .set(ServiceNotificationDelivery::getPayloadEncrypted, null)
                        .set(ServiceNotificationDelivery::getUpdateTime, now()));
        for (String id : preferences.scanBatch()) {
            try {
                observe(id);
            } catch (RuntimeException e) {
                log.warn("实习通知观察未完成：category={}", e.getClass().getSimpleName());
            }
        }
        for (String id : deliveries.readyBatch()) {
            try {
                dispatch(id);
            } catch (RuntimeException e) {
                // Only unsent READY rows rotate. DISPATCHING/UNKNOWN writes are never retried.
                deliveries.update(
                        null,
                        new LambdaUpdateWrapper<ServiceNotificationDelivery>()
                                .eq(ServiceNotificationDelivery::getId, id)
                                .eq(ServiceNotificationDelivery::getState, "READY")
                                .set(ServiceNotificationDelivery::getUpdateTime, now()));
                log.warn("实习通知派发未完成：category={}", e.getClass().getSimpleName());
            }
        }
    }

    /** Persist latest-known local status, not an invented upstream attendance result. */
    public void observe(String orderId) {
        if (!deliveryActive()) return;
        tx(
                () -> {
                    var order = orders.lock(orderId);
                    var p = preferences.lock(orderId);
                    if (p == null
                            || !Boolean.TRUE.equals(p.getEnabled())
                            || p.getVerifiedAt() == null
                            || p.getTokenEncrypted() == null) return null;
                    if (!Integer.valueOf(1).equals(preferences.ownerStatus(p.getUserId()))) {
                        revoke(p);
                        return null;
                    }
                    if (order == null
                            || !p.getUserId().equals(order.getUserId())
                            || !"sxdk_tw".equals(order.getProviderType())
                            || order.getExternalOrderNo() == null) {
                        revoke(p);
                        return null;
                    }
                    var current = observation(order);
                    String snapshot = json(current);
                    if (p.getObservedJson() != null && !snapshot.equals(p.getObservedJson()))
                        enqueue(p, "ORDER_UPDATE", message(orderId, current), now().plusHours(24));
                    p.setObservedJson(snapshot);
                    p.setScannedAt(now());
                    p.setUpdateTime(now());
                    write(preferences.updateById(p));
                    return null;
                });
    }

    private ServiceNotificationDelivery enqueue(
            ServiceNotificationPreference p, String kind, Message message, LocalDateTime expiry) {
        p.setSequence(p.getSequence() + 1);
        var d = new ServiceNotificationDelivery();
        d.setId(UUID.randomUUID().toString());
        d.setOrderId(p.getOrderId());
        d.setUserId(p.getUserId());
        d.setPreferenceVersion(p.getVersion());
        d.setSequence(p.getSequence());
        d.setKind(kind);
        d.setState("READY");
        d.setPayloadEncrypted(encrypt(json(message)));
        d.setExpiresAt(expiry);
        d.setCreateTime(now());
        d.setUpdateTime(now());
        write(deliveries.insert(d));
        return d;
    }

    private record Dispatch(String token, Message message) {
        @Override
        public String toString() {
            return "NotificationDispatch[REDACTED]";
        }
    }

    public void dispatch(String id) {
        if (!deliveryActive()) return;
        var seed = deliveries.selectById(id);
        if (seed == null || !"READY".equals(seed.getState())) return;
        if (!seed.getExpiresAt().isAfter(now())) {
            tx(
                    () -> {
                        var d = deliveries.lock(id);
                        if (d != null
                                && "READY".equals(d.getState())
                                && !d.getExpiresAt().isAfter(now())) terminal(d, "EXPIRED");
                        return null;
                    });
            return;
        }
        var current = preferences.selectById(seed.getOrderId());
        if (current == null || current.getTokenEncrypted() == null) {
            cancelOne(seed);
            return;
        }
        if (!Integer.valueOf(1).equals(preferences.ownerStatus(current.getUserId()))) {
            tx(
                    () -> {
                        var p = preferences.lock(seed.getOrderId());
                        if (p != null) revoke(p);
                        return null;
                    });
            cancelOne(seed);
            return;
        }
        // Rate checks precede SQL locks and use HMACs, never destination credentials.
        rate("delivery-user", seed.getUserId().toString(), 30, Duration.ofHours(1));
        rate(
                "delivery-target",
                hmac("target:" + decrypt(current.getTokenEncrypted())),
                60,
                Duration.ofHours(1));
        Dispatch dispatch =
                tx(
                        () -> {
                            var order = orders.lock(seed.getOrderId());
                            var p = preferences.lock(seed.getOrderId());
                            var d = deliveries.lock(id);
                            if (d == null || !"READY".equals(d.getState())) return null;
                            if (!d.getExpiresAt().isAfter(now())) {
                                terminal(d, "EXPIRED");
                                return null;
                            }
                            if (p == null
                                    || !Objects.equals(p.getVersion(), d.getPreferenceVersion())
                                    || p.getTokenEncrypted() == null
                                    || !p.getUserId().equals(d.getUserId())
                                    || ("ORDER_UPDATE".equals(d.getKind())
                                            && (!Boolean.TRUE.equals(p.getEnabled())
                                                    || p.getVerifiedAt() == null))) {
                                terminal(d, "CANCELLED");
                                return null;
                            }
                            if (!Integer.valueOf(1).equals(preferences.ownerStatus(d.getUserId()))
                                    || order == null
                                    || !d.getUserId().equals(order.getUserId())
                                    || !"sxdk_tw".equals(order.getProviderType())
                                    || order.getExternalOrderNo() == null) {
                                revoke(p);
                                return null;
                            }
                            Message message =
                                    decode(decrypt(d.getPayloadEncrypted()), Message.class);
                            String token = decrypt(p.getTokenEncrypted());
                            d.setState("DISPATCHING");
                            d.setUpdateTime(now());
                            write(deliveries.updateById(d));
                            return new Dispatch(token, message);
                        });
        if (dispatch == null) return;
        Receipt receipt;
        try {
            receipt = sender.send(dispatch.token(), dispatch.message());
        } catch (Exception e) {
            receipt = Receipt.UNKNOWN;
        }
        String state = receipt == null ? "UNKNOWN" : receipt.name();
        try {
            settle(id, state);
        } catch (RuntimeException e) {
            settle(id, "UNKNOWN");
        }
    }

    private void settle(String id, String state) {
        tx(
                () -> {
                    var d = deliveries.lock(id);
                    if (d != null && "DISPATCHING".equals(d.getState())) terminal(d, state);
                    return null;
                });
    }

    private void terminal(ServiceNotificationDelivery d, String state) {
        d.setState(state);
        d.setPayloadEncrypted(null);
        d.setUpdateTime(now());
        write(deliveries.updateById(d));
    }

    private void cancelOne(ServiceNotificationDelivery seed) {
        tx(
                () -> {
                    var d = deliveries.lock(seed.getId());
                    if (d != null && "READY".equals(d.getState())) terminal(d, "CANCELLED");
                    return null;
                });
    }

    private void revoke(ServiceNotificationPreference p) {
        p.setEnabled(false);
        p.setTokenEncrypted(null);
        p.setCodeHash(null);
        p.setChallengeDeliveryId(null);
        p.setChallengeExpiresAt(null);
        p.setVerifyAttempts(0);
        p.setVerifiedAt(null);
        p.setObservedJson(null);
        p.setVersion(p.getVersion() + 1);
        p.setUpdateTime(now());
        write(preferences.updateById(p));
        cancelQueued(p.getOrderId());
    }

    private void cancelQueued(String id) {
        deliveries.update(
                null,
                new LambdaUpdateWrapper<ServiceNotificationDelivery>()
                        .eq(ServiceNotificationDelivery::getOrderId, id)
                        .eq(ServiceNotificationDelivery::getState, "READY")
                        .set(ServiceNotificationDelivery::getState, "CANCELLED")
                        .set(ServiceNotificationDelivery::getPayloadEncrypted, null)
                        .set(ServiceNotificationDelivery::getUpdateTime, now()));
    }

    private Observation observation(ServiceOrder o) {
        String status = STATES.containsKey(o.getStatus()) ? o.getStatus() : "UNKNOWN";
        boolean uncertain = "UNKNOWN".equals(status) || "REFUND_REVIEW".equals(status);
        if (o.getPendingOperationId() != null) {
            var op = operations.selectById(o.getPendingOperationId());
            uncertain |= op != null && "UNKNOWN".equals(op.getState());
        }
        return new Observation(status, null, o.getQuantity(), uncertain);
    }

    private Message message(String id, Observation o) {
        String progress =
                o.completed() == null
                        ? "上游未提供可核实的完成次数；本平台记录计划服务日：" + o.quantity() + "。服务日历不代表考勤完成。"
                        : "已同步进度：" + o.completed() + " / " + o.quantity() + "。日历不代表考勤完成。";
        return new Message(
                "实习服务订单状态更新",
                "本平台订单："
                        + id
                        + "\n当前记录："
                        + STATES.getOrDefault(o.status(), "结果待核对")
                        + "。\n"
                        + progress
                        + (o.uncertain() ? "\n原操作结果仍待核对，请勿重复提交或自动退款。" : "")
                        + "\n请返回本平台查看详情。本通知只反映已经同步到平台的状态，不会额外查询上游或执行任务。");
    }

    private SettingsView view(String id, ServiceNotificationPreference p, Long ownerId) {
        // Reassigned orders never inherit the former owner's recipient or receiver proof.
        boolean ownedPreference = p != null && ownerId.equals(p.getUserId());
        boolean configured = ownedPreference && p.getTokenEncrypted() != null,
                verified = configured && p.getVerifiedAt() != null;
        boolean canVerify =
                configured
                        && !verified
                        && p.getCodeHash() != null
                        && p.getVerifyAttempts() < 5
                        && p.getChallengeExpiresAt() != null
                        && p.getChallengeExpiresAt().isAfter(now());
        return new SettingsView(
                id,
                configured,
                configured && Boolean.TRUE.equals(p.getEnabled()),
                verified,
                deliveryActive(),
                canVerify,
                p == null ? 0 : p.getVersion(),
                ownedPreference ? p.getChallengeDeliveryId() : null,
                ownedPreference ? p.getChallengeExpiresAt() : null,
                ownedPreference ? p.getVerifiedAt() : null,
                List.of(
                        "仅本人显式验证的 ShowDoc 渠道可接收本平台订单编号、已同步状态及计划服务日，不包含账号密码、位置或报告。",
                        "保存不发送。验证通知仅派发一次；接收到验证码后才启用。网络未知不会自动重发。",
                        "本功能是本平台的通知服务，不是校友帮微信授权或绑定；不会收取绑定费。",
                        "更新通知仅观察最新本地记录，离线期间可能合并多次状态；不主动查询上游、执行任务或改变余额。",
                        "停用会取消尚未派发的通知并清除密钥，但已派发消息无法撤回。"));
    }

    private DeliveryView deliveryView(ServiceNotificationDelivery d) {
        String note =
                switch (d.getState()) {
                    case "ACCEPTED" -> "ShowDoc 已受理，不代表接收人已阅读。";
                    case "REJECTED" -> "ShowDoc 明确拒绝，未自动重试。";
                    case "UNKNOWN", "DISPATCHING" -> "发送结果尚未核实，不自动重发；验证通知如已收到，仍可使用原验证码。";
                    case "CANCELLED" -> "设置已变更或停用，未派发的消息已取消。";
                    case "EXPIRED" -> "发送窗口已过期，不再发送。";
                    default -> "已保存待发送记录；安全限流不可用时不会发送。";
                };
        return new DeliveryView(
                d.getId(),
                d.getOrderId(),
                d.getKind(),
                d.getState(),
                d.getCreateTime(),
                d.getUpdateTime(),
                note);
    }

    private ServiceOrder owned(String id) {
        Long uid = user();
        uuid(id);
        return requireOwned(orders.selectById(id), uid);
    }

    private ServiceOrder requireOwned(ServiceOrder o, Long uid) {
        if (o == null
                || !uid.equals(o.getUserId())
                || !"sxdk_tw".equals(o.getProviderType())
                || !Integer.valueOf(1).equals(preferences.ownerStatus(uid))) throw missing();
        if (o.getExternalOrderNo() == null) throw bad("须先核实本人实习订单，才能配置通知");
        return o;
    }

    private Long user() {
        Long id = SecurityUtils.getCurrentUserId();
        if (!enabled) throw bad("原生服务尚未启用");
        return id;
    }

    private void requireDelivery() {
        if (!deliveryActive()) throw bad("实习通知尚未开放，请管理员审阅迁移与配置后启用");
    }

    private static void version(ServiceNotificationPreference p, Long version) {
        if (version == null || !Objects.equals(p == null ? 0L : p.getVersion(), version))
            throw bad("通知设置已变化，请重新读取后操作");
    }

    private static void configured(ServiceNotificationPreference p, Long ownerId) {
        if (p == null || !ownerId.equals(p.getUserId()) || p.getTokenEncrypted() == null)
            throw bad("请先保存本人推送密钥");
    }

    private static void consent(VersionForm f) {
        if (f == null || !f.consent()) throw bad("请确认本次通知操作");
    }

    private void rate(String action, String key, int count, Duration window) {
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("通知要求故障时拒绝的安全限流");
        var decision =
                limiter.check(
                        new RateLimitRequest(
                                "notification:" + action,
                                key,
                                count,
                                window,
                                "service-notification"));
        if (decision == null) throw bad("安全限流暂不可用");
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }

    private String codeHash(ServiceNotificationPreference p, String code) {
        return hmac(
                "verify:"
                        + p.getUserId()
                        + ":"
                        + p.getOrderId()
                        + ":"
                        + p.getVersion()
                        + ":"
                        + code);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(cryptoSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw bad("通知安全校验不可用");
        }
    }

    private String encrypt(String text) {
        try {
            return SecretCrypto.encrypt(text, cryptoSecret);
        } catch (Exception e) {
            throw bad("通知数据无法安全保存");
        }
    }

    private String decrypt(String text) {
        try {
            if (!SecretCrypto.isEncrypted(text)) throw bad("通知数据格式错误");
            return SecretCrypto.decrypt(text, cryptoSecret);
        } catch (Exception e) {
            throw bad("通知数据无法安全读取");
        }
    }

    private String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw bad("通知快照无法保存");
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return JSON.readValue(value, type);
        } catch (Exception e) {
            throw bad("通知快照无法读取");
        }
    }

    private static void uuid(String id) {
        if (id == null
                || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw missing();
    }

    private static LocalDateTime now() {
        return ServiceTime.now();
    }

    private static void write(int n) {
        if (n != 1) throw bad("通知状态未能保存，请检查原发送结果");
    }

    private <T> T tx(Supplier<T> body) {
        return new TransactionTemplate(transactions).execute(s -> body.get());
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND);
    }
}
