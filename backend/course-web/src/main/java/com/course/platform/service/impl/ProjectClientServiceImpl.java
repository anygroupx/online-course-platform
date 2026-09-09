package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.projectclient.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.TokenHashUtil;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.projectcenter.ServiceProject;
import com.course.platform.domain.projectclient.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Local source-platform customers. No upstream wallet, provider request, or fake service execution.
 */
@Service
@RequiredArgsConstructor
public class ProjectClientServiceImpl implements ProjectClientService {
    private final ProjectClientMapper clients;
    private final ProjectClientOperationMapper operations;
    private final ProjectApiCredentialMapper credentials;
    private final ServiceProjectMapper projects;
    private final UserMapper users;
    private final AccountLedgerServiceImpl ledger;
    private final ProjectApiKeyService keys;
    private final Validator validator;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    private static final BigDecimal ZERO = BigDecimal.ZERO,
            MAX_MONEY = new BigDecimal("99999999.99"),
            MAX_UNITS = new BigDecimal("999999999.999999");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public IPage<ProjectView> projects(Caller c, int page, int size) {
        keys.recheck(c, false, true);
        bounds(page, size);
        return projects.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<ServiceProject>()
                                .eq(ServiceProject::getEnabled, true)
                                .orderByDesc(ServiceProject::getId))
                .convert(
                        p ->
                                new ProjectView(
                                        p.getId(),
                                        p.getTitle(),
                                        text(p.getUnitPrice()),
                                        enabled && available(p),
                                        p.getVersion()));
    }

    @Override
    public IPage<ClientView> clients(Caller c, Long projectId, int page, int size) {
        keys.recheck(c, false, true);
        bounds(page, size);
        return clients.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<ProjectClient>()
                                .eq(ProjectClient::getOwnerId, c.ownerId())
                                .eq(projectId != null, ProjectClient::getProjectId, projectId)
                                .orderByDesc(ProjectClient::getCreateTime)
                                .orderByDesc(ProjectClient::getId))
                .convert(this::view);
    }

    @Override
    public ClientView client(Caller c, String id) {
        keys.recheck(c, false, false);
        if (c.clientId() != null && !c.clientId().equals(id)) throw missing();
        return view(ownedClient(c, id));
    }

    @Override
    public OperationView quote(Caller c, QuoteForm form) {
        keys.recheck(c, true, true);
        requireEnabled();
        if (form == null || !form.consent() || !validator.validate(form).isEmpty())
            throw bad("请核对项目、客户、数量及资金预览授权");
        ProjectApiKeyServiceImpl.uuid(form.requestId());
        boolean opening = "OPEN".equals(form.action());
        if (opening
                && (form.clientId() != null
                        || (form.label() == null || form.label().trim().isEmpty())))
            throw bad("开户请填写客户别名，不传已有客户编号");
        if (!opening) {
            ProjectApiKeyServiceImpl.uuid(form.clientId());
            if (form.label() != null) throw bad("资金变动不能修改客户别名");
            if (form.units().signum() <= 0) throw bad("兑换数量必须大于零");
        }
        rate(c, "quote");
        String hash = requestHash(form);
        return tx(
                () -> {
                    var project = projects.lock(form.projectId());
                    if (project == null) throw missing();
                    users.selectByIdForUpdate(c.ownerId());
                    keys.recheck(c, true, true);
                    var existing = findRequest(c.ownerId(), form.requestId());
                    if (existing != null) {
                        if (!hash.equals(existing.getRequestHash()))
                            throw bad("请求编号已被其他参数使用，请查询原结果");
                        return view(existing);
                    }
                    var client = opening ? null : clients.lock(form.clientId());
                    if (!opening
                            && (client == null
                                    || !c.ownerId().equals(client.getOwnerId())
                                    || !project.getId().equals(client.getProjectId())))
                        throw missing();
                    boolean withdraw = "WITHDRAW".equals(form.action());
                    if (!withdraw && !available(project)) throw bad("该项目暂不可开户或充值，请等待项目价格核实");
                    if (client != null
                            && ("CLOSED".equals(client.getStatus())
                                    || (!withdraw && !"ACTIVE".equals(client.getStatus()))))
                        throw bad("该客户暂不能进行此操作");
                    BigDecimal price = opening ? project.getUnitPrice() : client.getUnitPrice();
                    if (!withdraw && price.compareTo(project.getUnitCost()) < 0)
                        throw bad("冻结单价低于当前合同成本，暂不能充值");
                    BigDecimal amount =
                            withdraw
                                    ? withdrawAmount(client, form.units())
                                    : money(form.units().multiply(price), RoundingMode.UP);
                    if ((form.units().signum() > 0 && amount.signum() <= 0)
                            || amount.compareTo(MAX_MONEY) > 0) throw bad("兑换金额不足一分或超过上限");
                    if (!withdraw
                            && client != null
                            && (client.getBalance().add(form.units()).compareTo(MAX_UNITS) > 0
                                    || client.getRefundBudget().add(amount).compareTo(MAX_MONEY)
                                            > 0)) throw bad("客户余额或累计可退金额超出安全上限");
                    var op = new ProjectClientOperation();
                    op.setId(UUID.randomUUID().toString());
                    op.setRequestId(form.requestId());
                    op.setRequestHash(hash);
                    op.setOwnerId(c.ownerId());
                    op.setClientId(opening ? UUID.randomUUID().toString() : client.getId());
                    op.setProjectId(project.getId());
                    op.setProjectTitle(project.getTitle());
                    op.setLabel(opening ? form.label().trim() : client.getLabel());
                    op.setProjectVersion(project.getVersion());
                    op.setClientVersion(opening ? null : client.getVersion());
                    op.setAction(form.action());
                    op.setState("READY");
                    op.setUnits(form.units());
                    op.setUnitPrice(price);
                    op.setAmount(amount);
                    op.setExpiresAt(now().plusMinutes(5));
                    op.setCreateTime(now());
                    op.setUpdateTime(now());
                    write(operations.insert(op));
                    return view(op);
                });
    }

    @Override
    public OperationView confirm(Caller c, String id, ConfirmForm form) {
        keys.recheck(c, true, true);
        requireEnabled();
        if (form == null || !form.consent()) throw bad("请确认预览中的客户和金额");
        var seed = ownedOperation(c, id);
        rate(c, "confirm");
        return tx(
                () -> {
                    var project = projects.lock(seed.getProjectId());
                    var owner = users.selectByIdForUpdate(c.ownerId());
                    keys.recheck(c, true, true);
                    var client = clients.lock(seed.getClientId());
                    var op = operations.lock(id);
                    if (op == null || !c.ownerId().equals(op.getOwnerId())) throw missing();
                    if (!"READY".equals(op.getState())) return view(op);
                    if (!op.getExpiresAt().isAfter(now())) return terminal(op, "EXPIRED");
                    boolean opening = "OPEN".equals(op.getAction()),
                            withdraw = "WITHDRAW".equals(op.getAction());
                    if (project == null
                            || (!withdraw
                                    && (!available(project)
                                            || !Objects.equals(
                                                    project.getVersion(), op.getProjectVersion()))))
                        return terminal(op, "STALE");
                    if (opening) {
                        if (client != null) return terminal(op, "STALE");
                        client = new ProjectClient();
                        client.setId(op.getClientId());
                        client.setOwnerId(c.ownerId());
                        client.setProjectId(project.getId());
                        client.setProjectTitle(op.getProjectTitle());
                        client.setLabel(op.getLabel());
                        client.setStatus("ACTIVE");
                        client.setVersion(0L);
                        client.setBalance(ZERO);
                        client.setUnitPrice(op.getUnitPrice());
                        client.setRefundableUnits(ZERO);
                        client.setRefundBudget(ZERO);
                        client.setCreateTime(now());
                    } else if (client == null
                            || !c.ownerId().equals(client.getOwnerId())
                            || !client.getProjectId().equals(project.getId())
                            || !Objects.equals(client.getVersion(), op.getClientVersion())
                            || "CLOSED".equals(client.getStatus())
                            || (!withdraw && !"ACTIVE".equals(client.getStatus())))
                        return terminal(op, "STALE");
                    if (!withdraw && client.getUnitPrice().compareTo(project.getUnitCost()) < 0)
                        return terminal(op, "STALE");
                    if (withdraw) {
                        if (withdrawAmount(client, op.getUnits()).compareTo(op.getAmount()) != 0)
                            return terminal(op, "STALE");
                        if (owner.getBalance().add(op.getAmount()).compareTo(MAX_MONEY) > 0)
                            throw bad("平台钱包余额达到上限，暂不能转回");
                        ledger.credit(
                                c.ownerId(),
                                op.getAmount(),
                                AccountLedgerServiceImpl.BIZ_PROJECT_TRANSFER,
                                "CLIENT:" + op.getId(),
                                "下游客户本地额度转回平台余额",
                                false);
                        client.setBalance(client.getBalance().subtract(op.getUnits()));
                        client.setRefundableUnits(
                                client.getRefundableUnits().subtract(op.getUnits()));
                        client.setRefundBudget(client.getRefundBudget().subtract(op.getAmount()));
                    } else {
                        if (client.getBalance().add(op.getUnits()).compareTo(MAX_UNITS) > 0
                                || client.getRefundBudget().add(op.getAmount()).compareTo(MAX_MONEY)
                                        > 0) throw bad("客户余额超过安全上限");
                        if (op.getAmount().signum() > 0)
                            ledger.debit(
                                    c.ownerId(),
                                    op.getAmount(),
                                    AccountLedgerServiceImpl.BIZ_PROJECT_TRANSFER,
                                    "CLIENT:" + op.getId(),
                                    "下游客户本地额度充值");
                        client.setBalance(client.getBalance().add(op.getUnits()));
                        client.setRefundableUnits(client.getRefundableUnits().add(op.getUnits()));
                        client.setRefundBudget(client.getRefundBudget().add(op.getAmount()));
                    }
                    client.setVersion(client.getVersion() + 1);
                    client.setUpdateTime(now());
                    write(opening ? clients.insert(client) : clients.updateById(client));
                    op.setClientBalanceAfter(client.getBalance());
                    op.setWalletBalanceAfter(users.selectById(c.ownerId()).getBalance());
                    return terminal(op, "APPLIED");
                });
    }

    @Override
    public OperationView operation(Caller c, String id) {
        keys.recheck(c, false, true);
        return view(ownedOperation(c, id));
    }

    @Override
    public OperationView byRequest(Caller c, String requestId) {
        keys.recheck(c, false, true);
        ProjectApiKeyServiceImpl.uuid(requestId);
        var op = findRequest(c.ownerId(), requestId);
        if (op == null) throw missing();
        return view(op);
    }

    @Override
    public IPage<OperationView> operations(Caller c, String clientId, int page, int size) {
        keys.recheck(c, false, true);
        bounds(page, size);
        if (clientId != null) ownedClient(c, clientId);
        return operations
                .selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<ProjectClientOperation>()
                                .eq(ProjectClientOperation::getOwnerId, c.ownerId())
                                .eq(clientId != null, ProjectClientOperation::getClientId, clientId)
                                .orderByDesc(ProjectClientOperation::getCreateTime)
                                .orderByDesc(ProjectClientOperation::getId))
                .convert(this::view);
    }

    @Override
    public ClientView status(Caller c, String id, StatusForm form) {
        keys.recheck(c, true, true);
        if (form == null || !form.consent() || !validator.validate(form).isEmpty())
            throw bad("请确认客户状态变更");
        if ("ACTIVE".equals(form.status())) requireEnabled();
        ownedClient(c, id);
        return tx(
                () -> {
                    users.selectByIdForUpdate(c.ownerId());
                    keys.recheck(c, true, true);
                    var client = clients.lock(id);
                    if (client == null || !c.ownerId().equals(client.getOwnerId())) throw missing();
                    if (!Objects.equals(client.getVersion(), form.version()))
                        throw bad("客户状态已变化，请重新读取");
                    if ("CLOSED".equals(client.getStatus())) throw bad("已关闭客户不能恢复，请新建客户");
                    if ("CLOSED".equals(form.status())
                            && (client.getBalance().signum() != 0
                                    || client.getRefundBudget().signum() != 0))
                        throw bad("请先转回全部本地额度，再关闭客户");
                    client.setStatus(form.status());
                    client.setVersion(client.getVersion() + 1);
                    client.setUpdateTime(now());
                    write(clients.updateById(client));
                    if ("CLOSED".equals(form.status()))
                        credentials.update(
                                null,
                                new LambdaUpdateWrapper<ProjectApiCredential>()
                                        .eq(ProjectApiCredential::getOwnerId, c.ownerId())
                                        .eq(ProjectApiCredential::getSubject, id)
                                        .set(ProjectApiCredential::getKeyHash, null)
                                        .set(ProjectApiCredential::getPrefix, null)
                                        .setSql("version=version+1")
                                        .set(ProjectApiCredential::getUpdateTime, now()));
                    return view(client);
                });
    }

    @Override
    public Stats stats(Caller c) {
        keys.recheck(c, false, true);
        long total =
                clients.selectCount(
                        new LambdaQueryWrapper<ProjectClient>()
                                .eq(ProjectClient::getOwnerId, c.ownerId()));
        long active =
                clients.selectCount(
                        new LambdaQueryWrapper<ProjectClient>()
                                .eq(ProjectClient::getOwnerId, c.ownerId())
                                .eq(ProjectClient::getStatus, "ACTIVE"));
        var counts = operations.totals(c.ownerId());
        return new Stats(
                total, active, counts.count(), text(counts.debited()), text(counts.returned()));
    }

    private BigDecimal withdrawAmount(ProjectClient client, BigDecimal units) {
        if (client == null
                || units.signum() <= 0
                || client.getBalance().compareTo(units) < 0
                || client.getRefundableUnits().compareTo(units) < 0) throw bad("客户可转回额度不足");
        // A final full return includes residual cents from rounded-up deposits, never exceeds paid
        // budget.
        BigDecimal amount =
                units.compareTo(client.getBalance()) == 0
                                && units.compareTo(client.getRefundableUnits()) == 0
                        ? client.getRefundBudget()
                        : money(units.multiply(client.getUnitPrice()), RoundingMode.DOWN)
                                .min(client.getRefundBudget());
        if (amount.signum() <= 0) throw bad("部分转回金额不足一分，可选择转回全部本地额度");
        return amount;
    }

    private ProjectClient ownedClient(Caller c, String id) {
        ProjectApiKeyServiceImpl.uuid(id);
        var client = clients.selectById(id);
        if (client == null || !c.ownerId().equals(client.getOwnerId())) throw missing();
        return client;
    }

    private ProjectClientOperation ownedOperation(Caller c, String id) {
        ProjectApiKeyServiceImpl.uuid(id);
        var op = operations.selectById(id);
        if (op == null || !c.ownerId().equals(op.getOwnerId())) throw missing();
        return op;
    }

    private ProjectClientOperation findRequest(Long owner, String id) {
        return operations.selectOne(
                new LambdaQueryWrapper<ProjectClientOperation>()
                        .eq(ProjectClientOperation::getOwnerId, owner)
                        .eq(ProjectClientOperation::getRequestId, id));
    }

    private OperationView terminal(ProjectClientOperation op, String state) {
        op.setState(state);
        op.setUpdateTime(now());
        write(operations.updateById(op));
        return view(op);
    }

    private ClientView view(ProjectClient c) {
        return new ClientView(
                c.getId(),
                c.getProjectId(),
                c.getProjectTitle(),
                c.getLabel(),
                c.getStatus(),
                c.getVersion(),
                text(c.getBalance()),
                text(c.getUnitPrice()),
                text(c.getRefundableUnits()),
                text(c.getRefundBudget()),
                c.getCreateTime());
    }

    private OperationView view(ProjectClientOperation op) {
        String state =
                "READY".equals(op.getState()) && !op.getExpiresAt().isAfter(now())
                        ? "EXPIRED"
                        : op.getState();
        String notice =
                switch (state) {
                    case "APPLIED" -> "本地客户额度与平台钱包已在同一事务结算，不代表向真实上游充值或购买项目服务。";
                    case "STALE" -> "客户或项目已变化，本操作未扣款，请重新预览。";
                    case "EXPIRED" -> "预览已过期，本操作未扣款。";
                    default -> "先核对客户、数量和金额再确认。充值向上取整到分，部分转回向下取整；全部转回退还剩余实际支付金额。仅本地子账，不是上游可用余额。";
                };
        return new OperationView(
                op.getId(),
                op.getRequestId(),
                op.getClientId(),
                op.getProjectId(),
                op.getProjectTitle(),
                op.getLabel(),
                op.getAction(),
                state,
                text(op.getUnits()),
                text(op.getUnitPrice()),
                text(op.getAmount()),
                text(op.getClientBalanceAfter()),
                text(op.getWalletBalanceAfter()),
                op.getExpiresAt(),
                op.getCreateTime(),
                notice);
    }

    private boolean available(ServiceProject p) {
        return Boolean.TRUE.equals(p.getEnabled())
                && p.getValidUntil() != null
                && !p.getValidUntil().isBefore(ServiceTime.now().toLocalDate())
                && p.getUnitCost() != null
                && p.getUnitCost().signum() > 0
                && p.getUnitPrice() != null
                && p.getUnitPrice().signum() > 0
                && p.getUnitPrice().compareTo(p.getUnitCost()) >= 0;
    }

    private String requestHash(QuoteForm f) {
        try {
            return TokenHashUtil.sha256(
                    JSON.writeValueAsString(
                            Arrays.asList(
                                    f.action(),
                                    f.projectId(),
                                    f.clientId(),
                                    f.label() == null ? null : f.label().trim(),
                                    f.units().stripTrailingZeros().toPlainString())));
        } catch (Exception e) {
            throw bad("请求格式不正确");
        }
    }

    private void rate(Caller c, String action) {
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("客户资金操作要求故障时拒绝的安全限流");
        var d =
                limiter.check(
                        new RateLimitRequest(
                                "project-client:" + action,
                                c.ownerId().toString(),
                                30,
                                Duration.ofMinutes(1),
                                "project-client"));
        if (d == null) throw bad("安全限流暂不可用");
        if (!d.allowed()) throw new RateLimitExceededException(d.retryAfterSeconds());
    }

    private void requireEnabled() {
        if (!enabled) throw bad("原生客户功能尚未开放；已有结果可继续查询");
    }

    private static String text(BigDecimal n) {
        return n == null ? null : n.toPlainString();
    }

    private static BigDecimal money(BigDecimal n, RoundingMode r) {
        return n.setScale(2, r);
    }

    private static LocalDateTime now() {
        return ServiceTime.now();
    }

    private static void bounds(int p, int n) {
        if (p < 1 || p > 10000 || n < 1 || n > 100) throw bad("分页参数不正确");
    }

    private static void write(int n) {
        if (n != 1) throw bad("本地客户和账本未能完整保存，事务已回滚，请查询原操作");
    }

    private static BusinessException bad(String m) {
        return new BusinessException(m);
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND);
    }

    private <T> T tx(Supplier<T> body) {
        return new TransactionTemplate(transactions).execute(s -> body.get());
    }
}
