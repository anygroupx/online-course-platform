package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.projectcenter.ProjectRecordsService;
import com.course.platform.application.service.projectclient.ProjectApiKeyService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.projectcenter.ProjectRecordTypes.*;
import com.course.platform.domain.projectcenter.ProjectReportTypes;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.ProjectRecordsMapper;
import com.course.platform.infra.persistence.mapper.ProjectRecordsMapper.*;
import com.course.platform.infra.persistence.mapper.ProjectReportMapper;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProjectRecordsServiceImpl implements ProjectRecordsService {
    private final ProjectRecordsMapper records;
    private final ProjectReportMapper usage;
    private final ProjectApiKeyService keys;
    private final PlatformTransactionManager transactions;

    @Override
    public IPage<Owner> owners(String keyword, int page, int size) {
        admin(); bounds(page, size);
        String text = keyword(keyword);
        var search = new Search(null, null, null, null, null, null, null, like(text), numeric(text));
        return snapshot(() -> new Page<Owner>(page, size, records.ownerCount(search))
                .setRecords(records.owners(search, offset(page, size), size).stream().map(ProjectRecordsServiceImpl::owner).toList()));
    }

    @Override
    public OwnerDetail owner(long id) {
        admin(); positive(id);
        return snapshot(() -> {
            var identity = identity(id);
            var through = ServiceTime.now();
            var window = new ProjectReportTypes.Window(through.minusHours(24), through, "Asia/Shanghai");
            var calls = usage.calls(id, window.from(), window.through());
            var actions = usage.actions(id, window.from(), window.through());
            var report = new ProjectReportTypes.OwnerReport(window, calls, actions, calls.actionKinds() > actions.size(),
                    usage.clients(id), usage.tickets(id), funding(usage.localFunding(id)));
            return new OwnerDetail(identity, report, funding(records.accountFunding(id)));
        });
    }

    @Override
    public IPage<Account> accounts(long ownerId, int page, int size) {
        admin(); positive(ownerId); bounds(page, size);
        return snapshot(() -> {
            identity(ownerId);
            return new Page<Account>(page, size, records.accountCount(ownerId)).setRecords(
                    records.accounts(ownerId, offset(page, size), size).stream().map(r -> new Account(r.id(), r.projectId(),
                            r.title(), r.status(), units(r.cachedBalance()), r.balanceCheckedAt(), units(r.unitPrice()),
                            units(r.refundableUnits()), money(r.refundBudget()), money(r.debited()), money(r.returned()),
                            r.unresolvedOperations())).toList());
        });
    }

    @Override
    public IPage<Customer> customers(long ownerId, Long projectId, String status, int page, int size) {
        admin(); positive(ownerId); if (projectId != null) positive(projectId); bounds(page, size);
        var state = choice(status, Set.of("ACTIVE", "SUSPENDED", "CLOSED"));
        return snapshot(() -> {
            identity(ownerId);
            return new Page<Customer>(page, size, records.customerCount(ownerId, projectId, state)).setRecords(
                    records.customers(ownerId, projectId, state, offset(page, size), size).stream().map(r -> new Customer(
                            r.id(), r.projectId(), r.title(), r.label(), r.status(), units(r.balance()), units(r.unitPrice()),
                            units(r.refundableUnits()), money(r.refundBudget()), money(r.debited()), money(r.returned()), r.createdAt())).toList());
        });
    }

    @Override
    public LedgerPage adminLedger(LedgerFilter filter, int page, int size) {
        admin(); bounds(page, size);
        var search = search(filter, filter == null ? null : filter.ownerId());
        return snapshot(() -> ledger(search, page, size));
    }

    @Override
    public LedgerPage ownLedger(Caller caller, LedgerFilter filter, int page, int size) {
        // Authenticate before any query, including an invalid/empty filter; customer keys are not owner reports.
        keys.recheck(caller, false, true);
        bounds(page, size);
        if (filter != null && filter.ownerId() != null && !caller.ownerId().equals(filter.ownerId()))
            throw new BusinessException(ResultCode.FORBIDDEN);
        var search = search(filter, caller.ownerId());
        return snapshot(() -> {
            keys.recheck(caller, false, true);
            return ledger(search, page, size);
        });
    }

    private LedgerPage ledger(Search search, int page, int size) {
        long count = records.ledgerCount(search);
        var rows = records.ledger(search, offset(page, size), size).stream().map(r -> new LedgerEntry(
                r.book(), r.id(), r.ownerId(), r.projectId(), r.title(), r.subjectId(), r.action(), r.direction(),
                money(r.amount()), units(r.units()), units(r.unitPrice()), units(r.subjectBalanceAfter()),
                money(r.walletBalanceAfter()), r.requestedAt(), r.settledAt())).toList();
        var totals = records.ledgerTotals(search).stream().map(r -> new BookTotals(r.book(), r.operations(),
                money(r.debited()), money(r.returned()), money(r.debited().subtract(r.returned())))).toList();
        return new LedgerPage(rows, count, page, size, totals, ServiceTime.now(), "Asia/Shanghai");
    }

    private Owner identity(long id) {
        var q = new Search(id, null, null, null, null, null, null, null, null);
        var owners = records.owners(q, 0, 1);
        if (owners.isEmpty()) throw new BusinessException(ResultCode.NOT_FOUND);
        return owner(owners.get(0));
    }

    private void admin() {
        SecurityUtils.requireAuthority("api-provider:update");
        SecurityUtils.requireAuthority("payment:reconcile");
        keys.recheck(keys.web(), false, true);
    }

    private static Search search(LedgerFilter form, Long owner) {
        if (owner != null) positive(owner);
        if (form == null) return new Search(owner, null, null, null, null, null, null, null, null);
        if (form.projectId() != null) positive(form.projectId());
        String client = form.clientId();
        if (client != null && !client.isBlank() && !client.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw bad("客户编号格式不正确");
        if (client != null && client.isBlank()) client = null;
        String book = choice(form.book(), Set.of("PROJECT_ACCOUNT", "CUSTOMER_CREDIT"));
        String direction = choice(form.direction(), Set.of("DEBIT", "CREDIT"));
        if (client != null && "PROJECT_ACCOUNT".equals(book)) throw bad("客户编号只能筛选客户额度记录");
        LocalDate from = date(form.fromDate()), through = date(form.throughDate());
        if (from != null && through != null && through.isBefore(from)) throw bad("结束日期不能早于开始日期");
        return new Search(owner, form.projectId(), client, book, direction,
                from == null ? null : from.atStartOfDay(), through == null ? null : through.plusDays(1).atStartOfDay(),
                like(keyword(form.keyword())), null);
    }

    private static Owner owner(OwnerRow r) {
        return new Owner(r.id(), r.username(), r.status(), r.activeAccounts(), r.activeClients(), money(r.accountDebited()),
                money(r.accountReturned()), money(r.clientDebited()), money(r.clientReturned()), r.lastActivity());
    }
    private static ProjectReportTypes.Funding funding(ProjectReportMapper.FundingRow r) {
        return new ProjectReportTypes.Funding(r.settledOperations(), money(r.debited()), money(r.returned()),
                money(r.debited().subtract(r.returned())), r.unresolvedOperations());
    }
    private static String keyword(String value) {
        if (value == null) return null;
        if (value.length() > 100 || value.codePoints().anyMatch(Character::isISOControl)) throw bad("搜索内容最多100字且不能包含控制字符");
        return value.isBlank() ? null : value.trim();
    }
    private static String like(String value) {
        return value == null ? null : "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    private static Long numeric(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }
    private static String choice(String value, Set<String> allowed) {
        if (value == null || value.isEmpty() || value.equals("ALL")) return null;
        if (!allowed.contains(value)) throw bad("筛选条件不支持，请重新选择");
        return value;
    }
    private static LocalDate date(LocalDate value) {
        if (value != null && (value.getYear() < 1000 || value.getYear() > 9998)) throw bad("日期超出支持范围");
        return value;
    }
    private static void positive(long id) { if (id < 1) throw bad("编号必须为正整数"); }
    private static void bounds(int page, int size) {
        if (page < 1 || page > 10_000 || size < 1 || size > 50) throw bad("分页范围为1–10000，每页1–50条");
    }
    private static long offset(int page, int size) { return (long) (page - 1) * size; }
    private static String money(BigDecimal v) { return v == null ? null : v.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
    private static String units(BigDecimal v) { return v == null ? null : v.stripTrailingZeros().toPlainString(); }
    private static BusinessException bad(String message) { return new BusinessException(message); }
    private <T> T snapshot(Supplier<T> read) {
        var tx = new TransactionTemplate(transactions);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(s -> read.get());
    }
}
