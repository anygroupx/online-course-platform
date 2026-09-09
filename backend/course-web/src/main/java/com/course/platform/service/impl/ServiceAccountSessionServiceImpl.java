package com.course.platform.service.impl;

import static com.course.platform.infra.servicecommerce.PhpNativeServiceGateway.isHeishaFace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.servicecommerce.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.servicecommerce.*;
import com.course.platform.domain.servicecommerce.ServiceAccountTypes.*;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderForm;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.infra.servicecommerce.HeishaFaceUrlPolicy;
import com.course.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** One-shot authentication and SMS dispatch, independently of order/ledger transactions. */
@Service
@RequiredArgsConstructor
public class ServiceAccountSessionServiceImpl implements ServiceAccountSessions {
    private final ServiceAccountSessionMapper sessions;
    private final ServiceProductMapper products;
    private final ApiProviderService providers;
    private final ServiceAccountGateway gateway;
    private final HeishaFaceUrlPolicy faceUrls;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> IN_FLIGHT =
            Set.of("SMS_SENDING", "VERIFYING", "REFRESHING", "COLLECTING", "FACE_CHECKING");
    private static final Set<String> FACE_PENDING = Set.of("FACE_PENDING", "FACE_RETRY");
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    @Override
    public boolean faceCollectionConfigured() {
        return enabled && faceUrls.configured();
    }

    @Override
    public SessionView start(Long productId, StartForm form) {
        Long uid = user();
        if (form == null
                || form.mode() == null
                || !form.authorizedAccount()
                || !Set.of("PASSWORD", "SMS").contains(form.mode())) throw bad("请确认授权方式与本人账号提交授权");
        String account = field(form.account(), 100, false).trim(),
                school = field(form.schoolName(), 120, true).trim();
        ServiceProduct product = product(productId);
        ApiProvider provider = provider(product, null);
        if (isHeishaFace(product)
                && (!"PASSWORD".equals(form.mode()) || !account.matches("1[0-9]{10}")))
            throw bad("人脸商品需要本人手机号与密码预检");
        if ("SMS".equals(form.mode())
                && (!"sdxy".equals(product.getProject()) || !account.matches("1[0-9]{10}")))
            throw bad("仅闪动校园支持手机号短信授权");
        if ("PASSWORD".equals(form.mode())
                && "sdxy".equals(product.getProject())
                && school.isBlank()) throw bad("密码授权请填写学校名称");
        rate("service-auth:start", uid.toString(), 20, Duration.ofMinutes(10));
        ServiceAccountSession session = new ServiceAccountSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(uid);
        session.setProductId(productId);
        session.setProductVersion(product.getVersion());
        session.setProviderVersion(provider.getConfigVersion());
        session.setMode(form.mode());
        session.setState("CREATED");
        session.setVersion(0L);
        session.setAccountLabel(
                account.length() < 5
                        ? "授权账号"
                        : account.substring(0, 2)
                                + "***"
                                + account.substring(account.length() - 2));
        session.setCreateTime(ServiceTime.now());
        session.setUpdateTime(session.getCreateTime());
        session.setExpiresAt(session.getCreateTime().plusMinutes(10));
        session.setSnapshotEncrypted(encrypt(new AccountSnapshot(account, "", school, null, null)));
        write(sessions.insert(session));
        return view(session, product);
    }

    private record Dispatch(
            ServiceAccountSession session,
            ServiceProduct product,
            ApiProvider provider,
            AccountSnapshot account) {
        @Override
        public String toString() {
            return "Dispatch[REDACTED]";
        }
    }

    private Dispatch reserve(String id, String action) {
        Long uid = user();
        uuid(id);
        return tx(
                () -> {
                    ServiceAccountSession session = sessions.lock(id);
                    own(session, uid);
                    requireFresh(session);
                    boolean allowed =
                            switch (action) {
                                case "SEND" ->
                                        "SMS".equals(session.getMode())
                                                && "CREATED".equals(session.getState());
                                case "VERIFY" ->
                                        ("PASSWORD".equals(session.getMode())
                                                        && "CREATED".equals(session.getState()))
                                                || ("SMS".equals(session.getMode())
                                                        && "SMS_SENT".equals(session.getState()));
                                case "REFRESH" ->
                                        "READY".equals(session.getState())
                                                && session.getRulesRefreshAt() == null;
                                case "COLLECT" -> "FACE_REQUIRED".equals(session.getState());
                                case "CHECK_FACE" -> FACE_PENDING.contains(session.getState());
                                default -> false;
                            };
                    if (!allowed) return null;
                    ServiceProduct product = product(session.getProductId());
                    ApiProvider provider = provider(product, session);
                    AccountSnapshot snapshot = decrypt(session);
                    if (Set.of("COLLECT", "CHECK_FACE").contains(action)) {
                        if (!isHeishaFace(product) || snapshot.heisha() == null)
                            throw bad("该会话不支持人脸采集");
                        faceUrls.requireConfigured();
                    }
                    if ("REFRESH".equals(action)
                            && !Set.of("sdxy", "xbd").contains(product.getProject()))
                        throw bad("该项目没有独立规则刷新接口");
                    String identity = target(snapshot.account());
                    rate(
                            "service-auth:" + action.toLowerCase(Locale.ROOT) + ":user",
                            uid.toString(),
                            10,
                            Duration.ofHours(1));
                    rate(
                            "service-auth:" + action.toLowerCase(Locale.ROOT) + ":account",
                            identity,
                            "SEND".equals(action) ? 5 : 10,
                            Duration.ofHours(1));
                    if ("SEND".equals(action))
                        rate("service-auth:sms-cooldown", identity, 1, Duration.ofSeconds(60));
                    if ("CHECK_FACE".equals(action))
                        rate(
                                "service-auth:face-check-cooldown",
                                session.getId(),
                                1,
                                Duration.ofSeconds(3));
                    session.setState(
                            switch (action) {
                                case "SEND" -> "SMS_SENDING";
                                case "VERIFY" -> "VERIFYING";
                                case "REFRESH" -> "REFRESHING";
                                case "COLLECT" -> "COLLECTING";
                                case "CHECK_FACE" -> "FACE_CHECKING";
                                default -> throw bad("不支持的授权操作");
                            });
                    if ("REFRESH".equals(action)) session.setRulesRefreshAt(ServiceTime.now());
                    touch(session);
                    write(sessions.updateById(session));
                    return new Dispatch(session, product, provider, snapshot);
                });
    }

    @Override
    public SessionView sendCode(String id) {
        Dispatch d = reserve(id, "SEND");
        if (d == null) return get(id);
        try {
            gateway.sendCode(d.provider(), d.product(), d.account().account());
            finish(d, "SMS_SENT", d.account());
        } catch (Exception ex) {
            finish(d, "UNKNOWN", null);
        }
        return get(id);
    }

    @Override
    public SessionView verify(String id, VerifyForm form) {
        if (form == null) throw bad("请填写授权凭据");
        String secret = field(form.secret(), 200, false);
        var current = owned(id);
        if ("SMS".equals(current.getMode()) && !secret.matches("[0-9]{4,8}")) throw bad("验证码格式不合法");
        Dispatch d = reserve(id, "VERIFY");
        if (d == null) return get(id);
        try {
            AccountSnapshot result =
                    gateway.authenticate(
                            d.provider(),
                            d.product(),
                            d.session().getMode(),
                            d.account().account(),
                            secret,
                            d.account().schoolName());
            if (result == null
                    || result.lookup() == null
                    || !d.account().account().equals(result.account())) throw bad("授权回执不完整");
            finish(d, isHeishaFace(d.product()) ? "FACE_REQUIRED" : "READY", result);
        } catch (Exception ex) {
            finish(d, "UNKNOWN", null);
        }
        return get(id);
    }

    @Override
    public SessionView refreshRules(String id) {
        Dispatch d = reserve(id, "REFRESH");
        if (d == null) return get(id);
        try {
            AccountSnapshot result = gateway.refreshRules(d.provider(), d.product(), d.account());
            finish(d, result == null ? "REAUTHORIZE" : "READY", result);
        } catch (Exception ex) {
            finish(d, "UNKNOWN", null);
        }
        return get(id);
    }

    @Override
    public SessionView collectFace(String id, FaceConsentForm form) {
        if (form == null || !form.authorizedFace()) throw bad("请明确同意本人官方人脸采集流程");
        Dispatch d = reserve(id, "COLLECT");
        if (d == null) return get(id);
        try {
            var result = gateway.collectFace(d.provider(), d.product(), d.account());
            faceUrls.validate(result.heisha().collectUrl());
            // Even if collect_link says completed, require a separate authoritative face_check.
            finish(d, "FACE_PENDING", result);
        } catch (Exception ex) {
            finish(d, "UNKNOWN", null);
        }
        return get(id);
    }

    @Override
    public SessionView checkFace(String id) {
        Dispatch d = reserve(id, "CHECK_FACE");
        if (d == null) return get(id);
        try {
            var result = gateway.checkFace(d.provider(), d.product(), d.account());
            finish(d, result.heisha().status().completed() ? "READY" : "FACE_PENDING", result);
        } catch (Exception ex) {
            // face_check is read-only. Only an explicit new check may recover; never assume
            // completion.
            finish(d, "FACE_RETRY", d.account());
        }
        return get(id);
    }

    @Override
    public FaceLaunchTicket issueFaceLaunch(String id) {
        Long uid = user();
        uuid(id);
        return tx(
                () -> {
                    var s = sessions.lock(id);
                    own(s, uid);
                    requireFresh(s);
                    var product = product(s.getProductId());
                    provider(product, s);
                    var account = decrypt(s);
                    requireFacePending(s, product, account);
                    faceUrls.validate(account.heisha().collectUrl());
                    rate("service-auth:face-launch", uid.toString(), 30, Duration.ofMinutes(10));
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    String ticket = HexFormat.of().formatHex(bytes);
                    var expires = ServiceTime.now().plusSeconds(60);
                    if (expires.isAfter(s.getExpiresAt())) expires = s.getExpiresAt();
                    s.setSnapshotEncrypted(
                            encrypt(
                                    account.withHeisha(
                                            account.heisha()
                                                    .launch(target(id + ":" + ticket), expires))));
                    touch(s);
                    write(sessions.updateById(s));
                    return new FaceLaunchTicket(id, ticket, expires);
                });
    }

    @Override
    public java.net.URI consumeFaceLaunch(String id, String ticket) {
        // No login JWT in a navigation URL. This endpoint consumes only a 60-second, one-use POST
        // capability.
        if (!enabled) throw bad("服务商城尚未启用");
        uuid(id);
        if (ticket == null || !ticket.matches("[0-9a-f]{64}"))
            throw new BusinessException(ResultCode.NOT_FOUND);
        return tx(
                () -> {
                    var s = sessions.lock(id);
                    if (s == null || s.getSnapshotEncrypted() == null)
                        throw new BusinessException(ResultCode.NOT_FOUND);
                    var account = decrypt(s);
                    var h = account.heisha();
                    if (h == null
                            || h.launchDigest() == null
                            || h.launchExpiresAt() == null
                            || !h.launchExpiresAt().isAfter(ServiceTime.now())
                            || !java.security.MessageDigest.isEqual(
                                    h.launchDigest().getBytes(StandardCharsets.US_ASCII),
                                    target(id + ":" + ticket).getBytes(StandardCharsets.US_ASCII)))
                        throw new BusinessException(ResultCode.NOT_FOUND);
                    requireFresh(s);
                    var product = product(s.getProductId());
                    provider(product, s);
                    requireFacePending(s, product, account);
                    var destination = faceUrls.validate(h.collectUrl());
                    rate(
                            "service-auth:face-launch-consume",
                            s.getUserId().toString(),
                            30,
                            Duration.ofMinutes(10));
                    s.setSnapshotEncrypted(encrypt(account.withHeisha(h.launch(null, null))));
                    touch(s);
                    write(sessions.updateById(s));
                    return destination;
                });
    }

    private static void requireFacePending(
            ServiceAccountSession s, ServiceProduct p, AccountSnapshot a) {
        if (!isHeishaFace(p)
                || !FACE_PENDING.contains(s.getState())
                || a.heisha() == null
                || a.heisha().faceToken() == null
                || a.heisha().collectUrl() == null) throw bad("此授权会话没有可打开的官方采集链接");
    }

    private void finish(Dispatch d, String state, AccountSnapshot snapshot) {
        tx(
                () -> {
                    var session = sessions.lock(d.session().getId());
                    if (session == null
                            || !session.getState().equals(d.session().getState())
                            || !session.getVersion().equals(d.session().getVersion())) return null;
                    if (!session.getExpiresAt().isAfter(ServiceTime.now())) {
                        stateExpired(session);
                    } else {
                        session.setState(state);
                        session.setSnapshotEncrypted(snapshot == null ? null : encrypt(snapshot));
                        touch(session);
                    }
                    write(sessions.updateById(session));
                    return null;
                });
    }

    @Override
    public SessionView get(String id) {
        var session = owned(id);
        if (!session.getExpiresAt().isAfter(ServiceTime.now())
                || (IN_FLIGHT.contains(session.getState())
                        && session.getUpdateTime().isBefore(ServiceTime.now().minusMinutes(2)))) {
            tx(
                    () -> {
                        var locked = sessions.lock(id);
                        own(locked, user());
                        if (!locked.getExpiresAt().isAfter(ServiceTime.now())) stateExpired(locked);
                        else if (IN_FLIGHT.contains(locked.getState())
                                && locked.getUpdateTime()
                                        .isBefore(ServiceTime.now().minusMinutes(2))) {
                            boolean faceCheck = "FACE_CHECKING".equals(locked.getState());
                            locked.setState(faceCheck ? "FACE_RETRY" : "UNKNOWN");
                            if (!faceCheck) locked.setSnapshotEncrypted(null);
                            touch(locked);
                        }
                        write(sessions.updateById(locked));
                        return null;
                    });
            session = owned(id);
        }
        ServiceProduct product = products.selectById(session.getProductId());
        return view(session, product);
    }

    @Override
    public void revoke(String id) {
        Long uid = user();
        uuid(id);
        tx(
                () -> {
                    var s = sessions.lock(id);
                    own(s, uid);
                    s.setState("REVOKED");
                    s.setSnapshotEncrypted(null);
                    touch(s);
                    write(sessions.updateById(s));
                    return null;
                });
    }

    @Override
    public AccountPreparation prepare(
            ServiceProduct product, ApiProvider provider, OrderForm form) {
        var session = owned(form.accountSessionId());
        requireReady(session);
        binding(session, product, provider);
        var snapshot = decrypt(session);
        if (isHeishaFace(product)) faceUrls.validate(snapshot.heisha().collectUrl());
        var prepared = gateway.prepareAuthenticated(provider, product, form, snapshot);
        return new AccountPreparation(
                prepared, session.getId(), session.getVersion(), session.getExpiresAt());
    }

    @Override
    public void consume(
            String id, Long version, ServiceProduct product, ApiProvider provider, Long uid) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive())
            throw new IllegalStateException("Account consumption must share the order transaction");
        uuid(id);
        var s = sessions.lock(id);
        own(s, uid);
        requireReady(s);
        binding(s, product, provider);
        if (!Objects.equals(version, s.getVersion())) throw bad("授权状态或规则已变化，请重新预览");
        if (isHeishaFace(product)) faceUrls.validate(decrypt(s).heisha().collectUrl());
        s.setState("USED");
        s.setSnapshotEncrypted(null);
        touch(s);
        write(sessions.updateById(s));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void expire() {
        if (!enabled) return;
        sessions.update(
                null,
                new LambdaUpdateWrapper<ServiceAccountSession>()
                        .le(ServiceAccountSession::getExpiresAt, ServiceTime.now())
                        .isNotNull(ServiceAccountSession::getSnapshotEncrypted)
                        .set(ServiceAccountSession::getState, "EXPIRED")
                        .set(ServiceAccountSession::getSnapshotEncrypted, null));
        sessions.delete(
                new LambdaQueryWrapper<ServiceAccountSession>()
                        .lt(ServiceAccountSession::getExpiresAt, ServiceTime.now().minusDays(1)));
    }

    private SessionView view(ServiceAccountSession s, ServiceProduct p) {
        return new SessionView(
                s.getId(),
                s.getProductId(),
                s.getMode(),
                s.getState(),
                s.getAccountLabel(),
                s.getExpiresAt(),
                Set.of(
                                        "READY",
                                        "FACE_REQUIRED",
                                        "COLLECTING",
                                        "FACE_PENDING",
                                        "FACE_RETRY",
                                        "FACE_CHECKING")
                                .contains(s.getState())
                        ? decrypt(s).lookup()
                        : null,
                "READY".equals(s.getState())
                        && s.getRulesRefreshAt() == null
                        && p != null
                        && "flash".equals(p.getProviderType())
                        && Set.of("sdxy", "xbd").contains(p.getProject()),
                faceView(s, p));
    }

    private FaceSessionView faceView(ServiceAccountSession s, ServiceProduct p) {
        if (!isHeishaFace(p) || s.getSnapshotEncrypted() == null) return null;
        var h = decrypt(s).heisha();
        if (h == null) return null;
        var status = h.status();
        if (status != null)
            status =
                    new FaceStatus(
                            "READY".equals(s.getState()) && status.completed(),
                            status.fileCount(),
                            status.minFileCount(),
                            status.maxFileCount());
        return new FaceSessionView(
                h.collectUrl() == null ? null : faceUrls.origin(h.collectUrl()),
                status,
                "FACE_REQUIRED".equals(s.getState()),
                FACE_PENDING.contains(s.getState()),
                FACE_PENDING.contains(s.getState()) && h.collectUrl() != null);
    }

    private ServiceProduct product(Long id) {
        var p = products.selectById(id);
        if (p == null
                || !Boolean.TRUE.equals(p.getEnabled())
                || (!"flash".equals(p.getProviderType()) && !isHeishaFace(p)))
            throw bad("该商品不支持临时账号授权");
        return p;
    }

    private ApiProvider provider(ServiceProduct p, ServiceAccountSession s) {
        var provider = providers.loadDecrypted(p.getProviderId());
        if (provider == null
                || !Integer.valueOf(1).equals(provider.getStatus())
                || provider.getVerifiedAt() == null
                || !p.getProviderType().equals(provider.getProviderType())
                || !Objects.equals(p.getProviderId(), provider.getId())) throw bad("服务接口暂不可用");
        if (s != null) binding(s, p, provider);
        return provider;
    }

    private void binding(ServiceAccountSession s, ServiceProduct p, ApiProvider provider) {
        if ((!"flash".equals(p.getProviderType()) && !isHeishaFace(p))
                || !Objects.equals(p.getProviderId(), provider.getId())
                || !p.getProviderType().equals(provider.getProviderType())
                || !s.getProductId().equals(p.getId())
                || !Objects.equals(s.getProductVersion(), p.getVersion())
                || !Objects.equals(s.getProviderVersion(), provider.getConfigVersion()))
            throw bad("商品或接口配置已变更，请重新授权");
    }

    private ServiceAccountSession owned(String id) {
        Long uid = user();
        uuid(id);
        var s = sessions.selectById(id);
        own(s, uid);
        return s;
    }

    private static void own(ServiceAccountSession s, Long uid) {
        if (s == null || !Objects.equals(s.getUserId(), uid))
            throw new BusinessException(ResultCode.NOT_FOUND);
    }

    private Long user() {
        if (!enabled) throw bad("服务商城尚未启用");
        return SecurityUtils.getCurrentUserId();
    }

    private static void requireFresh(ServiceAccountSession s) {
        if (!s.getExpiresAt().isAfter(ServiceTime.now())) throw bad("授权会话已过期，请重新授权");
    }

    private static void requireReady(ServiceAccountSession s) {
        requireFresh(s);
        if (!"READY".equals(s.getState())) throw bad("请先完成账号授权；已使用或结果未知的会话不能再次下单");
    }

    private static void stateExpired(ServiceAccountSession s) {
        s.setState("EXPIRED");
        s.setSnapshotEncrypted(null);
        touch(s);
    }

    private static void touch(ServiceAccountSession s) {
        s.setVersion(s.getVersion() + 1);
        s.setUpdateTime(ServiceTime.now());
    }

    private static void write(int count) {
        if (count != 1) throw bad("授权会话保存失败，请检查状态，勿重复提交");
    }

    private void rate(String dimension, String key, int count, Duration window) {
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("短信和账号授权要求启用安全限流且故障时拒绝请求");
        var decision =
                limiter.check(
                        new RateLimitRequest(dimension, key, count, window, "service-account"));
        if (decision == null) throw bad("授权限流服务不可用");
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }

    private String target(String account) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(cryptoSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(account.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw bad("无法安全核实授权请求");
        }
    }

    private String encrypt(AccountSnapshot snapshot) {
        try {
            return SecretCrypto.encrypt(JSON.writeValueAsString(snapshot), cryptoSecret);
        } catch (Exception ex) {
            throw bad("授权资料无法安全保存");
        }
    }

    private AccountSnapshot decrypt(ServiceAccountSession s) {
        try {
            String value = s.getSnapshotEncrypted();
            if (!SecretCrypto.isEncrypted(value) || value.length() > 400000) throw bad("授权资料不可用");
            var result =
                    JSON.readValue(
                            SecretCrypto.decrypt(value, cryptoSecret), AccountSnapshot.class);
            if (result == null) throw bad("授权资料不可用");
            return result;
        } catch (Exception ex) {
            throw bad("授权资料不可用，请重新授权");
        }
    }

    private static String field(String value, int max, boolean optional) {
        if (value == null && optional) return "";
        if (value == null
                || value.length() > max
                || (!optional && value.isBlank())
                || value.codePoints().anyMatch(Character::isISOControl)) throw bad("账号授权参数不合法");
        return value;
    }

    private static void uuid(String id) {
        if (id == null
                || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new BusinessException(ResultCode.NOT_FOUND);
    }

    private <T> T tx(Supplier<T> task) {
        return new TransactionTemplate(transactions).execute(s -> task.get());
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }
}
