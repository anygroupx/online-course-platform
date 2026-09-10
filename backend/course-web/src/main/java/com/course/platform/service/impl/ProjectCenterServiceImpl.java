package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.projectcenter.*;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.config.RateLimitProperties;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.projectcenter.*;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Local reservation → one remote dispatch → local settlement. No database lock spans supplier HTTP.
 */
@Service
@RequiredArgsConstructor
public class ProjectCenterServiceImpl implements ProjectCenterService, ProjectAccountAccess {
    private final ServiceProjectMapper projects;
    private final ServiceProjectAccountMapper accounts;
    private final ServiceProjectOperationMapper operations;
    private final ApiProviderService providers;
    private final ProjectCenterGateway gateway;
    private final AccountLedgerServiceImpl ledger;
    private final RateLimitService limiter;
    private final RateLimitProperties limits;
    private final PlatformTransactionManager transactions;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");
    private static final String BIZ = "PROJECT_TRANSFER";

    @Override
    public List<CatalogItem> catalog(Long providerId) {
        Long uid = user();
        admin();
        rate(uid, "catalog", 20);
        return gateway.projects(provider(providerId));
    }

    @Override
    public IPage<ProjectView> projects(int page, int size, boolean admin) {
        Long uid = user();
        if (admin) admin();
        bounds(page, size);
        var query = new LambdaQueryWrapper<ServiceProject>().orderByDesc(ServiceProject::getId);
        if (!admin) {
            var bound =
                    accounts
                            .selectList(
                                    new LambdaQueryWrapper<ServiceProjectAccount>()
                                            .eq(ServiceProjectAccount::getUserId, uid))
                            .stream()
                            .map(ServiceProjectAccount::getProjectId)
                            .toList();
            query.and(
                    q -> {
                        q.eq(ServiceProject::getEnabled, true);
                        if (!bound.isEmpty()) q.or().in(ServiceProject::getId, bound);
                    });
        }
        var rows = projects.selectPage(new Page<>(page, size), query);
        return page(rows, rows.getRecords().stream().map(p -> view(p, uid, admin)).toList());
    }

    @Override
    public ProjectView save(Long id, ProjectForm f) {
        Long uid = user();
        admin();
        if (f == null || f.providerId() == null || f.remoteProjectId() == null)
            throw bad("项目参数不完整");
        if (id != null && !f.enabled())
            return tx(
                    () -> {
                        var p = projects.lock(id);
                        if (p == null) throw missing();
                        checkProjectCommand(p, f);
                        p.setEnabled(false);
                        p.setVersion(p.getVersion() + 1);
                        p.setUpdateTime(now());
                        write(projects.updateById(p));
                        return view(p, uid, true);
                    });
        text(f.title(), 100, 1);
        text(f.description() == null ? "" : f.description(), 1000, 0);
        evidence(f.upstreamChecked(), f.evidence());
        positiveRate(f.unitPrice());
        positiveRate(f.unitCost());
        if (f.unitPrice().compareTo(f.unitCost()) < 0) throw bad("项目售价不能低于已核实成本");
        if (f.validUntil() == null
                || f.validUntil().isBefore(now().toLocalDate())
                || f.validUntil().isAfter(now().toLocalDate().plusDays(90)))
            throw bad("成本核实有效期须在九十天以内");
        ApiProvider provider = provider(f.providerId());
        rate(uid, "catalog", 20);
        var remote =
                gateway.projects(provider).stream()
                        .filter(p -> p.id().equals(f.remoteProjectId()))
                        .findFirst()
                        .orElseThrow(() -> bad("上游没有该项目，请重新读取目录"));
        return tx(
                () -> {
                    var current = provider(f.providerId());
                    if (!Objects.equals(current.getConfigVersion(), provider.getConfigVersion()))
                        throw bad("接口配置已变化，请重新核实");
                    var p = id == null ? new ServiceProject() : projects.lock(id);
                    if (p == null) throw missing();
                    if (id != null) checkProjectCommand(p, f);
                    p.setProviderId(f.providerId());
                    p.setRemoteProjectId(f.remoteProjectId());
                    p.setTitle(f.title().trim());
                    p.setDescription(f.description());
                    p.setBasePrice(remote.basePrice());
                    p.setUnitPrice(f.unitPrice());
                    p.setUnitCost(f.unitCost());
                    p.setValidUntil(f.validUntil());
                    p.setPriceEvidence(f.evidence().trim());
                    p.setReviewedBy(uid);
                    p.setReviewedAt(now());
                    p.setProviderIdentity(identity(current));
                    p.setEnabled(f.enabled());
                    p.setVersion(id == null ? 0L : p.getVersion() + 1);
                    p.setUpdateTime(now());
                    if (id == null) {
                        p.setCreateTime(now());
                        write(projects.insert(p));
                    } else write(projects.updateById(p));
                    return view(p, uid, true);
                });
    }

    @Override
    public AccountView refresh(String accountId) {
        var a = ownedAccount(accountId);
        rate(a.getUserId(), "balance", 30);
        if (!Set.of("ACTIVE", "DISABLED").contains(a.getState())
                || a.getPendingOperationId() != null
                || a.getRemoteCustomerId() == null) throw bad("账户仍待核对，请检查原操作");
        var provider = forAccount(a);
        var receipt = gateway.customer(provider, a.getRemoteProjectId(), a.getRemoteCustomerId());
        checkCustomer(a, receipt);
        return tx(
                () -> {
                    var current = accounts.lock(a.getId());
                    own(current, a.getUserId());
                    if (!Objects.equals(current.getVersion(), a.getVersion())
                            || current.getPendingOperationId() != null)
                        throw bad("账户正在处理另一笔操作，请检查原操作状态");
                    current.setRemoteBalance(receipt.balance());
                    current.setBalanceCheckedAt(now());
                    current.setState(receipt.enabled() ? "ACTIVE" : "DISABLED");
                    touch(current);
                    write(accounts.updateById(current));
                    return accountView(current);
                });
    }

    @Override
    public OperationView quote(Long projectId, QuoteForm f) {
        Long uid = user();
        if (f == null
                || !f.confirmedPolicy()
                || !Set.of("PROVISION", "TOP_UP", "WITHDRAW")
                        .contains(f.action() == null ? "" : f.action()))
            throw bad("请确认操作及项目余额兑换规则");
        rate(uid, "quote", 30);
        var p = project(projectId);
        var provider = provider(p.getProviderId());
        contract(p, provider);
        boolean provision = "PROVISION".equals(f.action()),
                withdraw = "WITHDRAW".equals(f.action());
        if (!withdraw && !Boolean.TRUE.equals(p.getEnabled())) throw bad("该项目暂未开放开户或充值");
        var account = findAccount(uid, projectId);
        if (provision) {
            if (f.units() != null && f.units().signum() != 0) units(f.units());
            if (account != null && !"NEW".equals(account.getState()))
                throw bad("项目账户已存在或仍待核对，请检查原操作");
        } else {
            if (account == null) throw bad("请先开通本人项目账户");
            requireActive(account);
            binding(account, provider);
            units(f.units());
        }
        var rate = provision ? p.getUnitPrice() : account.getUnitPrice();
        if (!withdraw && rate.compareTo(p.getUnitCost()) < 0)
            throw bad("原冻结售价低于当前已核实成本，暂不能充值，请联系管理员");
        var quantity = f.units() == null ? ZERO : f.units().stripTrailingZeros();
        var amount =
                provision && quantity.signum() == 0
                        ? ZERO
                        : money(
                                quantity.multiply(rate),
                                withdraw ? RoundingMode.DOWN : RoundingMode.UP);
        if (quantity.signum() > 0 && (amount.signum() <= 0 || amount.compareTo(MAX_MONEY) > 0))
            throw bad("兑换金额太小或超出安全上限");
        if (withdraw) {
            withdrawal(account, quantity, amount);
            var receipt =
                    gateway.customer(
                            provider, account.getRemoteProjectId(), account.getRemoteCustomerId());
            checkCustomer(account, receipt);
            if (!receipt.enabled() || receipt.balance().compareTo(quantity) < 0)
                throw bad("上游项目可用余额不足或账户已停用");
        }
        var op = new ServiceProjectOperation();
        op.setId(UUID.randomUUID().toString());
        op.setAccountId(account == null ? UUID.randomUUID().toString() : account.getId());
        op.setUserId(uid);
        op.setProjectId(p.getId());
        op.setProjectTitle(p.getTitle());
        op.setProjectVersion(p.getVersion());
        op.setProviderVersion(provider.getConfigVersion());
        op.setAccountVersion(account == null ? null : account.getVersion());
        op.setAction(f.action());
        op.setState("READY");
        op.setUnits(quantity);
        op.setUnitPrice(rate);
        op.setUnitCost(p.getUnitCost());
        op.setAmount(amount);
        op.setExpiresAt(now().plusMinutes(5));
        op.setCreateTime(now());
        op.setUpdateTime(now());
        var endOfContract = p.getValidUntil().plusDays(1).atStartOfDay();
        if (endOfContract.isBefore(op.getExpiresAt())) op.setExpiresAt(endOfContract);
        write(operations.insert(op));
        return operationView(op, false);
    }

    private record Dispatch(
            ServiceProjectOperation operation,
            ServiceProjectAccount account,
            ApiProvider provider) {
        @Override
        public String toString() {
            return "Dispatch[REDACTED]";
        }
    }

    @Override
    public OperationView confirm(String id) {
        Long uid = user();
        uuid(id);
        var seed = operations.selectById(id);
        own(seed, uid);
        rate(uid, "confirm", 30);
        Dispatch d = tx(() -> reserve(seed, uid));
        if (d == null) return operation(id, false);
        try {
            if ("PROVISION".equals(d.operation().getAction())) {
                var receipt = d.operation().getUnits().signum() == 0
                        ? gateway.provision(d.provider(), d.account().getRemoteProjectId())
                        : gateway.provision(d.provider(), d.account().getRemoteProjectId(), d.operation().getUnits());
                settle(d.operation().getId(), receipt, null, null);
            } else {
                var op = d.operation();
                var delta =
                        "TOP_UP".equals(op.getAction()) ? op.getUnits() : op.getUnits().negate();
                var receipt =
                        gateway.adjust(
                                d.provider(),
                                d.account().getRemoteProjectId(),
                                d.account().getRemoteCustomerId(),
                                delta,
                                op.getId());
                if (receipt.actualPrice() != null
                        && receipt.actualPrice().compareTo(op.getUnitCost()) > 0)
                    throw bad("上游成本超过已核实快照，需人工核对本次回执");
                settle(op.getId(), null, receipt, null);
            }
        } catch (Exception ex) {
            markUnknown(id);
        }
        return operation(id, false);
    }

    private Dispatch reserve(ServiceProjectOperation seed, Long uid) {
        var p = projects.lock(seed.getProjectId());
        if (p == null) throw missing();
        var account = findAccount(uid, p.getId());
        if (account != null) account = accounts.lock(account.getId());
        var op = operations.lock(seed.getId());
        own(op, uid);
        if (!"READY".equals(op.getState())) return null;
        if (!op.getExpiresAt().isAfter(now())) {
            op.setState("EXPIRED");
            op.setUpdateTime(now());
            write(operations.updateById(op));
            return null;
        }
        var provider = provider(p.getProviderId());
        contract(p, provider);
        if (!Objects.equals(p.getVersion(), op.getProjectVersion())
                || !Objects.equals(provider.getConfigVersion(), op.getProviderVersion()))
            throw bad("项目价格或接口已变更，请重新预览");
        if (!"WITHDRAW".equals(op.getAction()) && !Boolean.TRUE.equals(p.getEnabled()))
            throw bad("项目已停止开户或充值");
        if ("PROVISION".equals(op.getAction())) {
            if (account != null
                    && (!"NEW".equals(account.getState())
                            || !account.getId().equals(op.getAccountId())
                            || !Objects.equals(account.getVersion(), op.getAccountVersion())))
                throw bad("项目账户已经开通或状态已变化");
            if (account == null) {
                account = new ServiceProjectAccount();
                account.setId(op.getAccountId());
                account.setUserId(uid);
                account.setProjectId(p.getId());
                account.setProviderId(p.getProviderId());
                account.setRemoteProjectId(p.getRemoteProjectId());
                account.setProviderIdentity(identity(provider));
                account.setUnitPrice(op.getUnitPrice());
                account.setState("NEW");
                account.setRefundableUnits(ZERO);
                account.setRefundBudget(ZERO);
                account.setVersion(0L);
                account.setCreateTime(now());
                account.setUpdateTime(now());
                write(accounts.insert(account));
            } else {
                binding(account, provider);
                account.setUnitPrice(op.getUnitPrice());
            }
        } else {
            if (account == null
                    || !account.getId().equals(op.getAccountId())
                    || !Objects.equals(account.getVersion(), op.getAccountVersion()))
                throw bad("项目账户状态已变化，请重新预览");
            requireActive(account);
            binding(account, provider);
            if ("WITHDRAW".equals(op.getAction()))
                withdrawal(account, op.getUnits(), op.getAmount());
            else {
                if (account.getUnitPrice().compareTo(p.getUnitCost()) < 0
                        || account.getRefundBudget().add(op.getAmount()).compareTo(MAX_MONEY) > 0)
                    throw bad("冻结价格或累计充值金额超出安全限制");
                ledger.debit(uid, op.getAmount(), BIZ, "SYY:" + op.getId(), "项目额度充值预扣，等待上游确认");
            }
        }
        if (fundedProvision(op)) {
            // Reserve funds and the sole dispatch in the same transaction, before remote I/O.
            ledger.debit(uid, op.getAmount(), BIZ, "SYY:" + op.getId(), "项目开户初始额度预扣，等待确认");
        }
        account.setPendingOperationId(op.getId());
        account.setState("BUSY");
        touch(account);
        write(accounts.updateById(account));
        op.setState("DISPATCHING");
        op.setUpdateTime(now());
        write(operations.updateById(op));
        return new Dispatch(op, account, provider);
    }

    /**
     * Settlement contains no remote I/O. A failed local commit leaves the durable operation
     * UNKNOWN.
     */
    private void settle(
            String id,
            CustomerReceipt customer,
            AdjustmentReceipt adjustment,
            Resolution resolution) {
        var seed = operations.selectById(id);
        if (seed == null) throw missing();
        tx(
                () -> {
                    var account = accounts.lock(seed.getAccountId());
                    var op = operations.lock(id);
                    boolean manual = resolution != null;
                    if (account == null
                            || op == null
                            || !id.equals(account.getPendingOperationId())) return null;
                    if (!(manual ? "UNKNOWN" : "DISPATCHING").equals(op.getState())) return null;
                    boolean accepted = !manual || "ACCEPTED".equals(resolution.form().outcome());
                    if (accepted) {
                        if ("PROVISION".equals(op.getAction())) {
                            if (customer == null || customer.balance() == null
                                    || !account.getRemoteProjectId().equals(customer.projectId())
                                    || !manual && (!customer.enabled() || customer.balance().compareTo(op.getUnits()) != 0))
                                throw bad("开户回执与已确认的初始额度不匹配，请核对原操作");
                            account.setRemoteCustomerId(customer.id());
                            account.setCustomerKeyEncrypted(encrypt(customer.apiKey()));
                            account.setRemoteBalance(customer.balance());
                            account.setRefundableUnits(op.getUnits());
                            account.setRefundBudget(op.getAmount());
                        } else {
                            if (adjustment == null || adjustment.balanceAfter() == null)
                                throw bad("兑换回执不完整");
                            account.setRemoteBalance(adjustment.balanceAfter());
                            if ("TOP_UP".equals(op.getAction())) {
                                account.setRefundableUnits(
                                        account.getRefundableUnits().add(op.getUnits()));
                                account.setRefundBudget(
                                        account.getRefundBudget().add(op.getAmount()));
                            } else {
                                withdrawal(account, op.getUnits(), op.getAmount());
                                ledger.credit(
                                        op.getUserId(),
                                        op.getAmount(),
                                        BIZ,
                                        "SYY:" + op.getId(),
                                        "项目额度转回平台余额",
                                        false);
                                account.setRefundableUnits(
                                        account.getRefundableUnits().subtract(op.getUnits()));
                                account.setRefundBudget(
                                        account.getRefundBudget().subtract(op.getAmount()));
                            }
                        }
                        account.setBalanceCheckedAt(now());
                        account.setState(
                                customer != null && !customer.enabled() ? "DISABLED" : "ACTIVE");
                        op.setBalanceAfter(account.getRemoteBalance());
                        op.setState("SUCCEEDED");
                    } else {
                        if ("TOP_UP".equals(op.getAction()) || fundedProvision(op))
                            ledger.credit(
                                    op.getUserId(),
                                    op.getAmount(),
                                    BIZ,
                                    "SYY:" + op.getId(),
                                    "已核实未受理，返还项目充值预扣",
                                    false);
                        account.setState(account.getRemoteCustomerId() == null ? "NEW" : "ACTIVE");
                        account.setRemoteBalance(null);
                        account.setBalanceCheckedAt(null);
                        op.setState("NOT_ACCEPTED");
                    }
                    if (manual) {
                        op.setResolvedBy(resolution.uid());
                        op.setResolutionEvidence(resolution.form().evidence().trim());
                    }
                    account.setPendingOperationId(null);
                    touch(account);
                    op.setUpdateTime(now());
                    op.setErrorCategory(null);
                    write(accounts.updateById(account));
                    write(operations.updateById(op));
                    return null;
                });
    }

    private void markUnknown(String id) {
        var seed = operations.selectById(id);
        if (seed == null) return;
        tx(
                () -> {
                    var account = accounts.lock(seed.getAccountId());
                    var op = operations.lock(id);
                    if (account == null
                            || op == null
                            || !"DISPATCHING".equals(op.getState())
                            || !id.equals(account.getPendingOperationId())) return null;
                    op.setState("UNKNOWN");
                    op.setErrorCategory("UPSTREAM_RESULT_UNKNOWN");
                    op.setUpdateTime(now());
                    account.setState("UNKNOWN");
                    touch(account);
                    write(accounts.updateById(account));
                    write(operations.updateById(op));
                    return null;
                });
    }

    @Override
    public OperationView operation(String id, boolean admin) {
        Long uid = user();
        uuid(id);
        if (admin) reconciler();
        var op = operations.selectById(id);
        if (admin) {
            if (op == null) throw missing();
        } else own(op, uid);
        if ("DISPATCHING".equals(op.getState())
                && op.getUpdateTime().isBefore(now().minusMinutes(2))) {
            markUnknown(id);
            op = operations.selectById(id);
        }
        if ("READY".equals(op.getState()) && !op.getExpiresAt().isAfter(now())) {
            tx(
                    () -> {
                        var current = operations.lock(id);
                        if (current != null
                                && "READY".equals(current.getState())
                                && !current.getExpiresAt().isAfter(now())) {
                            current.setState("EXPIRED");
                            current.setUpdateTime(now());
                            write(operations.updateById(current));
                        }
                        return null;
                    });
            op = operations.selectById(id);
        }
        return operationView(op, admin);
    }

    @Override
    public IPage<OperationView> operations(int page, int size, Long projectId, boolean admin) {
        Long uid = user();
        if (admin) reconciler();
        bounds(page, size);
        var query =
                new LambdaQueryWrapper<ServiceProjectOperation>()
                        .eq(!admin, ServiceProjectOperation::getUserId, uid)
                        .eq(projectId != null, ServiceProjectOperation::getProjectId, projectId)
                        .orderByDesc(ServiceProjectOperation::getCreateTime);
        var rows = operations.selectPage(new Page<>(page, size), query);
        return page(rows, rows.getRecords().stream().map(o -> operationView(o, admin)).toList());
    }

    private record Resolution(Long uid, ResolveForm form) {}

    @Override
    public OperationView resolve(String id, ResolveForm f) {
        Long uid = user();
        reconciler();
        uuid(id);
        if (f == null
                || !Set.of("ACCEPTED", "NOT_ACCEPTED")
                        .contains(f.outcome() == null ? "" : f.outcome())) throw bad("核对结果不合法");
        evidence(f.upstreamChecked(), f.evidence());
        rate(uid, "reconcile", 10);
        var op = operations.selectById(id);
        if (op == null) throw missing();
        if ("SUCCEEDED".equals(op.getState()) || "NOT_ACCEPTED".equals(op.getState()))
            return operationView(op, true);
        if (!"UNKNOWN".equals(op.getState())) throw bad("只能核对结果未知的操作，请先查询当前状态");
        var account = accounts.selectById(op.getAccountId());
        if (account == null) throw missing();
        CustomerReceipt customer = null;
        AdjustmentReceipt adjustment = null;
        if ("ACCEPTED".equals(f.outcome())) {
            var provider = forAccount(account);
            if ("PROVISION".equals(op.getAction())) {
                if (f.customerId() == null) throw bad("核对开户须提供已核实的上游客户编号");
                customer = gateway.customer(provider, account.getRemoteProjectId(), f.customerId());
            } else {
                if (f.customerId() != null) throw bad("余额核对不接受替换上游客户");
                var receipt =
                        gateway.customer(
                                provider,
                                account.getRemoteProjectId(),
                                account.getRemoteCustomerId());
                checkCustomer(account, receipt);
                customer = receipt;
                adjustment = new AdjustmentReceipt(receipt.balance(), null);
            }
        }
        settle(id, customer, adjustment, new Resolution(uid, f));
        return operation(id, true);
    }

    @Override
    public ProjectAccountAccess.Context forTickets(String accountId, boolean asAdmin) {
        Long uid = user();
        if (asAdmin) admin();
        uuid(accountId);
        var account = accounts.selectById(accountId);
        if (asAdmin) {
            if (account == null) throw missing();
        } else own(account, uid);
        if (account.getRemoteCustomerId() == null) throw bad("须先核实并开通项目账户，才能提交或查询工单");
        var provider = forAccount(account);
        return new ProjectAccountAccess.Context(
                account,
                provider,
                decrypt(account.getCustomerKeyEncrypted()),
                project(account.getProjectId()).getTitle());
    }

    private ProjectView view(ServiceProject p, Long uid, boolean admin) {
        boolean available = Boolean.TRUE.equals(p.getEnabled());
        try {
            contract(p, provider(p.getProviderId()));
        } catch (RuntimeException e) {
            available = false;
        }
        var a = findAccount(uid, p.getId());
        return new ProjectView(
                p.getId(),
                admin ? p.getProviderId() : null,
                p.getRemoteProjectId(),
                p.getTitle(),
                p.getDescription(),
                admin ? plain(p.getBasePrice()) : null,
                plain(p.getUnitPrice()),
                admin ? plain(p.getUnitCost()) : null,
                p.getValidUntil(),
                Boolean.TRUE.equals(p.getEnabled()),
                available,
                p.getVersion(),
                a == null ? null : accountView(a));
    }

    private AccountView accountView(ServiceProjectAccount a) {
        return new AccountView(
                a.getId(),
                a.getProjectId(),
                a.getState(),
                plain(a.getUnitPrice()),
                plain(a.getRemoteBalance()),
                plain(a.getRefundableUnits()),
                plain(a.getRefundBudget()),
                a.getBalanceCheckedAt(),
                a.getPendingOperationId(),
                a.getRemoteCustomerId() != null);
    }

    private OperationView operationView(ServiceProjectOperation op, boolean admin) {
        List<String> warnings = new ArrayList<>();
        warnings.add("项目余额以核实后的记录为准；预览不扣款，确认后仅提交一次。");
        if (fundedProvision(op)) {
            warnings.add("本次同时开通账户并充值 " + plain(op.getUnits()) + " 额度；确认后预扣 ¥"
                    + op.getAmount().setScale(2).toPlainString() + "，成功后计入可转回额度与金额。");
            warnings.add("响应不确定时保留预扣并检查原操作；不能重新开户或凭余额变化自动退款。");
        } else if ("PROVISION".equals(op.getAction())) {
            warnings.add("零余额开通不扣款；账户凭据加密保管，不会在页面显示。");
        }
        if (!"PROVISION".equals(op.getAction()) || fundedProvision(op))
            warnings.add("充值费用向上取整到分，转回金额向下取整到分；只能转回已充值且尚未退回的额度与金额，不兑付赠额。");
        if ("UNKNOWN".equals(op.getState()) || "DISPATCHING".equals(op.getState()))
            warnings.add("结果尚未核实，请检查原操作；不重复派发、不自动退款，不凭余额变化猜测是否成功。");
        return new OperationView(
                op.getId(),
                op.getAccountId(),
                op.getProjectId(),
                op.getProjectTitle(),
                admin ? op.getUserId() : null,
                op.getAction(),
                op.getState(),
                plain(op.getUnits()),
                plain(op.getUnitPrice()),
                op.getAmount().setScale(2).toPlainString(),
                plain(op.getBalanceAfter()),
                op.getExpiresAt(),
                op.getCreateTime(),
                List.copyOf(warnings));
    }

    private ServiceProject project(Long id) {
        var p = projects.selectById(id);
        if (p == null) throw missing();
        return p;
    }

    private ServiceProjectAccount findAccount(Long uid, Long project) {
        return accounts.selectOne(
                new LambdaQueryWrapper<ServiceProjectAccount>()
                        .eq(ServiceProjectAccount::getUserId, uid)
                        .eq(ServiceProjectAccount::getProjectId, project));
    }

    private ServiceProjectAccount ownedAccount(String id) {
        Long uid = user();
        uuid(id);
        var a = accounts.selectById(id);
        own(a, uid);
        return a;
    }

    private ApiProvider provider(Long id) {
        var p = providers.loadDecrypted(id);
        if (p == null
                || !Objects.equals(p.getId(), id)
                || !"syyv5".equals(p.getProviderType())
                || !Integer.valueOf(1).equals(p.getStatus())
                || p.getVerifiedAt() == null) throw bad("项目上游接口尚未验证或已停用");
        return p;
    }

    private ApiProvider forAccount(ServiceProjectAccount a) {
        var p = provider(a.getProviderId());
        binding(a, p);
        return p;
    }

    private void binding(ServiceProjectAccount a, ApiProvider p) {
        if (!identity(p).equals(a.getProviderIdentity()))
            throw bad("上游地址或主密钥已改变，旧项目账户须人工核对，不能转到新账户");
    }

    private void contract(ServiceProject p, ApiProvider provider) {
        if (p.getValidUntil() == null
                || p.getValidUntil().isBefore(now().toLocalDate())
                || !identity(provider).equals(p.getProviderIdentity()))
            throw bad("兑换成本核实已到期或接口身份变化，请管理员重新核实");
    }

    private void checkCustomer(ServiceProjectAccount a, CustomerReceipt r) {
        if (r == null
                || !a.getRemoteCustomerId().equals(r.id())
                || !a.getRemoteProjectId().equals(r.projectId())
                || !decrypt(a.getCustomerKeyEncrypted()).equals(r.apiKey()))
            throw bad("上游账户回执不匹配，已停止操作");
    }

    private static void requireActive(ServiceProjectAccount a) {
        if (!"ACTIVE".equals(a.getState())
                || a.getPendingOperationId() != null
                || a.getRemoteCustomerId() == null) throw bad("账户不可操作或上一笔仍待确认，请检查原操作");
    }

    private static void withdrawal(ServiceProjectAccount a, BigDecimal units, BigDecimal amount) {
        if (a.getRefundableUnits().compareTo(units) < 0
                || a.getRefundBudget().compareTo(amount) < 0)
            throw bad("转回不能超过本平台累计未退的充值额度与金额；外部赠额不自动兑付");
    }

    private static void checkProjectCommand(ServiceProject p, ProjectForm f) {
        if (!Objects.equals(p.getVersion(), f.version())
                || !p.getProviderId().equals(f.providerId())
                || !p.getRemoteProjectId().equals(f.remoteProjectId()))
            throw bad("项目版本或绑定已变化，不能替换供应商/远端项目");
    }

    private String identity(ApiProvider p) {
        if (p.getApiKey() == null || p.getApiKey().isBlank()) throw bad("项目上游主密钥缺失");
        try {
            String address = new ProviderUrlNormalizer().normalize(p.getApiUrl()).toASCIIString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(cryptoSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(
                            mac.doFinal(
                                    (address + "\n" + p.getApiKey())
                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw bad("无法安全核实项目接口身份");
        }
    }

    private String encrypt(String key) {
        if (key == null || key.isBlank() || key.length() > 2048) throw bad("上游客户凭据不可用");
        try {
            return SecretCrypto.encrypt(key, cryptoSecret);
        } catch (Exception ex) {
            throw bad("无法安全保存客户凭据");
        }
    }

    private String decrypt(String value) {
        try {
            if (!SecretCrypto.isEncrypted(value)) throw bad("客户凭据不可用");
            return SecretCrypto.decrypt(value, cryptoSecret);
        } catch (Exception ex) {
            throw bad("客户凭据不可用");
        }
    }

    private Long user() {
        Long id = SecurityUtils.getCurrentUserId();
        if (!enabled) throw bad("项目中心尚未启用，请先完成迁移与配置");
        return id;
    }

    private static void admin() {
        SecurityUtils.requireAuthority("api-provider:update");
    }

    private static void reconciler() {
        admin();
        SecurityUtils.requireAuthority("payment:reconcile");
    }

    private void rate(Long uid, String action, int count) {
        if (!limits.isEnabled() || !limits.isFailClosed()) throw bad("项目开户与资金操作要求故障时拒绝的安全限流");
        var decision =
                limiter.check(
                        new RateLimitRequest(
                                "project:" + action,
                                uid.toString(),
                                count,
                                Duration.ofMinutes(10),
                                "project-center"));
        if (decision == null) throw bad("安全限流暂不可用");
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }

    private static void own(ServiceProjectAccount a, Long uid) {
        if (a == null || !uid.equals(a.getUserId())) throw missing();
    }

    private static void own(ServiceProjectOperation o, Long uid) {
        if (o == null || !uid.equals(o.getUserId())) throw missing();
    }

    private static boolean fundedProvision(ServiceProjectOperation op) {
        return "PROVISION".equals(op.getAction()) && op.getUnits().signum() > 0;
    }

    private static void units(BigDecimal amount) {
        if (amount == null
                || amount.signum() <= 0
                || amount.compareTo(new BigDecimal("100000")) > 0
                || amount.stripTrailingZeros().scale() > 6) throw bad("项目额度须大于零、最多六位小数且不超过十万");
    }

    private static void positiveRate(BigDecimal rate) {
        if (rate == null
                || rate.signum() <= 0
                || rate.compareTo(new BigDecimal("9999")) > 0
                || rate.stripTrailingZeros().scale() > 6) throw bad("单位价格须大于零且最多六位小数");
    }

    private static BigDecimal money(BigDecimal amount, RoundingMode mode) {
        return amount.setScale(2, mode);
    }

    private static void evidence(boolean checked, String value) {
        if (!checked) throw bad("须确认已经与上游核对");
        text(value, 1000, 10);
    }

    private static void text(String text, int max, int min) {
        if (text == null
                || text.trim().length() < min
                || text.length() > max
                || text.codePoints()
                        .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r'))
            throw bad("文本格式或长度不合法");
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static void uuid(String id) {
        if (id == null
                || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw missing();
    }

    private static void bounds(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw bad("分页参数不合法");
    }

    private static void write(int rows) {
        if (rows != 1) throw bad("项目操作未能保存，请检查原操作状态");
    }

    private static void touch(ServiceProjectAccount a) {
        a.setVersion(a.getVersion() + 1);
        a.setUpdateTime(now());
    }

    private static LocalDateTime now() {
        return ServiceTime.now();
    }

    private <T> T tx(Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(s -> work.get());
    }

    private static <A, B> IPage<B> page(IPage<A> source, List<B> rows) {
        var result = new Page<B>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(rows);
        return result;
    }

    private static BusinessException bad(String message) {
        return new BusinessException(message);
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND);
    }
}
