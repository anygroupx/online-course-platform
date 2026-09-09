package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.projectcenter.ProjectReportingService;
import com.course.platform.application.service.projectclient.ProjectApiKeyService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.projectcenter.ProjectReportTypes.*;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.ProjectReportMapper;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ProjectReportingServiceImpl implements ProjectReportingService {
    private final ProjectReportMapper reports;
    private final ProjectApiKeyService keys;
    private final PlatformTransactionManager transactions;

    @Override
    public OwnerReport owner(Caller caller) {
        return snapshot(() -> {
            keys.recheck(caller, false, true);
            var window = window();
            var calls = reports.calls(caller.ownerId(), window.from(), window.through());
            var actions = reports.actions(caller.ownerId(), window.from(), window.through());
            return new OwnerReport(window, calls, actions, calls.actionKinds() > actions.size(),
                    reports.clients(caller.ownerId()), reports.tickets(caller.ownerId()),
                    funding(reports.localFunding(caller.ownerId())));
        });
    }

    @Override
    public IPage<ProjectBalance> projects(Caller caller, int page, int pageSize) {
        return snapshot(() -> {
            keys.recheck(caller, false, true);
            if (page < 1 || page > 10_000 || pageSize < 1 || pageSize > 50)
                throw new BusinessException("统计分页超出范围：page 1–10000，pageSize 1–50");
            var rows = reports.projects(caller.ownerId(), (long) (page - 1) * pageSize, pageSize);
            return new Page<ProjectBalance>(page, pageSize, reports.clients(caller.ownerId()).projects())
                    .setRecords(rows.stream().map(r -> new ProjectBalance(r.projectId(), r.title(), r.customers(),
                            r.active(), units(r.activeUnits()), units(r.suspendedUnits()), money(r.refundBudget()))).toList());
        });
    }

    @Override
    public SystemReport system() {
        SecurityUtils.requireAuthority("api-provider:update");
        SecurityUtils.requireAuthority("payment:reconcile");
        return snapshot(() -> {
            keys.recheck(keys.web(), false, true);
            var window = window();
            var counts = reports.systemCounts();
            return new SystemReport(window, counts.publishedProjects(), counts.activeUpstreamBindings(),
                    counts.activeUpstreamOwners(), counts.activeLocalOwners(),
                    reports.calls(null, window.from(), window.through()), reports.clients(null), reports.tickets(null),
                    funding(reports.upstreamFunding()), funding(reports.localFunding(null)));
        });
    }

    private static Window window() {
        var end = ServiceTime.now();
        return new Window(end.minusHours(24), end, "Asia/Shanghai");
    }

    private static Funding funding(ProjectReportMapper.FundingRow row) {
        return new Funding(row.settledOperations(), money(row.debited()), money(row.returned()),
                money(row.debited().subtract(row.returned())), row.unresolvedOperations());
    }

    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
    private static String units(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private <T> T snapshot(Supplier<T> read) {
        var transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> read.get());
    }
}
