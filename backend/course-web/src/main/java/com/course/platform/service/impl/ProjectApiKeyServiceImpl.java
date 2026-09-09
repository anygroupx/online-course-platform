package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.projectclient.ProjectApiKeyService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.entity.User;
import com.course.platform.domain.projectclient.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Source-platform API credentials, not supplier keys. Only dedicated issuance returns plaintext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectApiKeyServiceImpl implements ProjectApiKeyService {
    private final SecurityAuditService audit;
    private final UserMapper users;
    private final ProjectClientMapper clients;
    private final ProjectApiCredentialMapper credentials;
    private final ProjectApiCallMapper calls;
    private final Validator validator;
    private final PasswordEncoder passwords;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Caller web() {
        Long id = SecurityUtils.getCurrentUserId();
        active(users.selectById(id));
        return new Caller(id, null, null, null, true);
    }

    @Override
    public Caller authenticate(String key) {
        if (!enabled) throw bad("原生项目 API 尚未开放");
        if (key == null || !key.matches("np[oc]_[0-9a-f]{64}")) throw unauthorized();
        String hash = TokenHashUtil.sha256(key);
        rate("auth", hash, 120, Duration.ofMinutes(1));
        var row =
                credentials.selectOne(
                        new LambdaQueryWrapper<ProjectApiCredential>()
                                .eq(ProjectApiCredential::getKeyHash, hash));
        if (row == null || row.getKeyHash() == null || !row.getExpiresAt().isAfter(now()))
            throw unauthorized();
        boolean owner = "OWNER".equals(row.getSubject());
        if (owner != key.startsWith("npo_")) throw unauthorized();
        var caller =
                new Caller(
                        row.getOwnerId(),
                        row.getId(),
                        row.getVersion(),
                        owner ? null : row.getSubject(),
                        "MANAGE".equals(row.getAccessMode()));
        recheck(caller, false, false);
        return caller;
    }

    @Override
    public void recheck(Caller c, boolean manage, boolean ownerOnly) {
        if (c == null || c.ownerId() == null) throw unauthorized();
        active(users.selectById(c.ownerId()));
        if (c.credentialId() == null) {
            if (!c.ownerId().equals(SecurityUtils.getCurrentUserId())
                    || c.clientId() != null
                    || !c.manage()) throw unauthorized();
        } else {
            var key = credentials.selectById(c.credentialId());
            if (key == null
                    || !c.ownerId().equals(key.getOwnerId())
                    || !Objects.equals(c.credentialVersion(), key.getVersion())
                    || key.getKeyHash() == null
                    || !key.getExpiresAt().isAfter(now())
                    || !Objects.equals(
                            c.clientId() == null ? "OWNER" : c.clientId(), key.getSubject())
                    || c.manage() != "MANAGE".equals(key.getAccessMode())) throw unauthorized();
            if (c.clientId() != null) {
                var client = clients.selectById(c.clientId());
                if (client == null
                        || !c.ownerId().equals(client.getOwnerId())
                        || !"ACTIVE".equals(client.getStatus())) throw unauthorized();
            }
        }
        if ((manage && !c.manage()) || (ownerOnly && c.clientId() != null)) throw forbidden();
    }

    @Override
    public void recheckTickets(Caller caller, boolean write) {
        recheck(caller, false, false);
        if (!write) return;
        if (caller.clientId() == null) {
            if (!caller.manage()) throw forbidden();
        } else {
            var row = credentials.selectById(caller.credentialId());
            if (row == null || !Objects.equals(row.getVersion(), caller.credentialVersion())
                    || !"SUPPORT".equals(row.getAccessMode())) throw forbidden();
        }
    }

    @Override
    public KeyView settings(String subject) {
        var owner = web();
        subject(owner.ownerId(), subject);
        return view(find(owner.ownerId(), subject), subject);
    }

    @Override
    public IssuedKey issue(String subject, KeyForm form) {
        if (!enabled) throw bad("原生项目密钥尚未开放");
        var actor = web();
        if (form == null || !form.consent() || !validator.validate(form).isEmpty())
            throw bad("请填写当前密码、有效期、权限并确认签发");
        if ("OWNER".equals(subject) && "SUPPORT".equals(form.access()))
            throw bad("主密钥仅支持只读或经营者管理权限");
        if (!"OWNER".equals(subject) && "MANAGE".equals(form.access()))
            throw bad("客户密钥仅支持只读或本人提单/回复，不能管理客户或资金");
        String passwordHash = verifyPassword(actor.ownerId(), form.password());
        return tx(
                () -> {
                    var user = users.selectByIdForUpdate(actor.ownerId());
                    active(user);
                    if (!Objects.equals(passwordHash, user.getPassword())) throw unauthorized();
                    subject(actor.ownerId(), subject);
                    if (!"OWNER".equals(subject)
                            && !"ACTIVE".equals(clients.selectById(subject).getStatus()))
                        throw bad("仅可为可用客户签发新密钥");
                    var row = find(actor.ownerId(), subject);
                    version(row, form.version());
                    boolean create = row == null;
                    if (create) {
                        row = new ProjectApiCredential();
                        row.setId(UUID.randomUUID().toString());
                        row.setOwnerId(actor.ownerId());
                        row.setSubject(subject);
                        row.setVersion(0L);
                        row.setCreateTime(now());
                    }
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    String raw =
                            ("OWNER".equals(subject) ? "npo_" : "npc_")
                                    + HexFormat.of().formatHex(bytes);
                    row.setKeyHash(TokenHashUtil.sha256(raw));
                    row.setPrefix(raw.substring(0, 12));
                    row.setVersion(row.getVersion() + 1);
                    row.setAccessMode(form.access());
                    row.setExpiresAt(now().plusDays(form.days()));
                    row.setUpdateTime(now());
                    write(create ? credentials.insert(row) : credentials.updateById(row));
                    audit.record(
                            "KEY_CHANGE",
                            "WARN",
                            actor.ownerId(),
                            null,
                            "/project-api-keys/" + subject,
                            "POST",
                            "本平台项目密钥已签发或轮换；旧密钥失效",
                            "version=" + row.getVersion() + ",scope=" + row.getAccessMode());
                    return new IssuedKey(raw, view(row, subject));
                });
    }

    @Override
    public KeyView revoke(String subject, KeyRevoke form) {
        var actor = web();
        if (form == null || !form.consent() || !validator.validate(form).isEmpty())
            throw bad("请填写当前密码并确认撤销密钥");
        String passwordHash = verifyPassword(actor.ownerId(), form.password());
        return tx(
                () -> {
                    var user = users.selectByIdForUpdate(actor.ownerId());
                    active(user);
                    if (!Objects.equals(passwordHash, user.getPassword())) throw unauthorized();
                    subject(actor.ownerId(), subject);
                    var row = find(actor.ownerId(), subject);
                    version(row, form.version());
                    if (row == null) return view(null, subject);
                    row.setKeyHash(null);
                    row.setPrefix(null);
                    row.setVersion(row.getVersion() + 1);
                    row.setUpdateTime(now());
                    write(credentials.updateById(row));
                    audit.record(
                            "KEY_CHANGE",
                            "WARN",
                            actor.ownerId(),
                            null,
                            "/project-api-keys/" + subject,
                            "DELETE",
                            "本平台项目密钥已撤销",
                            "version=" + row.getVersion());
                    return view(row, subject);
                });
    }

    @Override
    public void record(Caller actor, String action, boolean success) {
        if (actor == null
                || actor.credentialId() == null
                || action == null
                || !action.matches("[A-Z_]{1,40}")) return;
        try {
            var row = new ProjectApiCall();
            row.setId(UUID.randomUUID().toString());
            row.setOwnerId(actor.ownerId());
            row.setCredentialId(actor.credentialId());
            row.setAction(action);
            row.setOutcome(success ? "OK" : "FAILED");
            row.setCreateTime(now());
            calls.insert(row);
        } catch (RuntimeException ignored) {
            log.warn("项目 API 调用统计保存失败；不改变原业务结果");
        }
    }

    @Override
    public IPage<ApiCallView> calls(int page, int size) {
        Long uid = web().ownerId();
        bounds(page, size);
        var rows =
                calls.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<ProjectApiCall>()
                                .eq(ProjectApiCall::getOwnerId, uid)
                                .orderByDesc(ProjectApiCall::getCreateTime)
                                .orderByDesc(ProjectApiCall::getId));
        return rows.convert(r -> new ApiCallView(r.getAction(), r.getOutcome(), r.getCreateTime()));
    }

    private String verifyPassword(Long uid, String password) {
        rate("password", uid.toString(), 5, Duration.ofMinutes(10));
        var user = users.selectById(uid);
        active(user);
        if (!passwords.matches(password, user.getPassword())) throw bad("当前密码不正确");
        return user.getPassword();
    }

    private void subject(Long owner, String subject) {
        if ("OWNER".equals(subject)) return;
        uuid(subject);
        var client = clients.selectById(subject);
        if (client == null || !owner.equals(client.getOwnerId()))
            throw new BusinessException(ResultCode.NOT_FOUND);
    }

    private ProjectApiCredential find(Long owner, String subject) {
        return credentials.selectOne(
                new LambdaQueryWrapper<ProjectApiCredential>()
                        .eq(ProjectApiCredential::getOwnerId, owner)
                        .eq(ProjectApiCredential::getSubject, subject));
    }

    private KeyView view(ProjectApiCredential row, String subject) {
        return new KeyView(
                row != null && row.getKeyHash() != null,
                row == null ? null : row.getPrefix(),
                row == null ? "READ_ONLY" : row.getAccessMode(),
                row == null ? 0 : row.getVersion(),
                row == null ? null : row.getExpiresAt(),
                subject);
    }

    private static void version(ProjectApiCredential row, Long version) {
        if (version == null || !version.equals(row == null ? 0L : row.getVersion()))
            throw bad("密钥设置已变化，请重新查询后操作");
    }

    private void rate(String action, String key, int count, Duration duration) {
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("项目 API 要求故障时拒绝的安全限流");
        var d =
                limiter.check(
                        new RateLimitRequest(
                                "project-key:" + action, key, count, duration, "project-key"));
        if (d == null) throw bad("安全限流暂不可用");
        if (!d.allowed()) throw new RateLimitExceededException(d.retryAfterSeconds());
    }

    private static void active(User user) {
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getMustChangePassword())) throw unauthorized();
    }

    public static void uuid(String value) {
        if (value == null
                || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw bad("编号格式不正确");
    }

    private static void bounds(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw bad("分页参数不正确");
    }

    private static void write(int n) {
        if (n != 1) throw bad("密钥设置未能保存，请查询当前版本，不自动重试");
    }

    private static BusinessException bad(String m) {
        return new BusinessException(m);
    }

    private static BusinessException unauthorized() {
        return new BusinessException(ResultCode.UNAUTHORIZED);
    }

    private static BusinessException forbidden() {
        return new BusinessException(ResultCode.FORBIDDEN);
    }

    private static LocalDateTime now() {
        return ServiceTime.now();
    }

    private <T> T tx(Supplier<T> body) {
        return new TransactionTemplate(transactions).execute(s -> body.get());
    }
}
