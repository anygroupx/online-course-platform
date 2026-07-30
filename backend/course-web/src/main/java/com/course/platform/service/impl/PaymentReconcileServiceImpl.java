package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.application.service.security.PaymentReconcileService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.domain.entity.AccountLedger;
import com.course.platform.domain.entity.PaymentOrder;
import com.course.platform.domain.entity.PaymentReconcileReport;
import com.course.platform.infra.persistence.mapper.AccountLedgerMapper;
import com.course.platform.infra.persistence.mapper.PaymentOrderMapper;
import com.course.platform.infra.persistence.mapper.PaymentReconcileReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 支付日终对账：本地 payment_order(PAID) vs account_ledger(PAYMENT 入账)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconcileServiceImpl implements PaymentReconcileService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final AccountLedgerMapper accountLedgerMapper;
    private final PaymentReconcileReportMapper paymentReconcileReportMapper;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentReconcileReport reconcile(LocalDate bizDate) {
        LocalDate date = bizDate == null ? LocalDate.now().minusDays(1) : bizDate;
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        List<PaymentOrder> paidOrders = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getStatus, "PAID")
                .ge(PaymentOrder::getPaidTime, start)
                .le(PaymentOrder::getPaidTime, end));

        List<AccountLedger> credits = accountLedgerMapper.selectList(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getBizType, AccountLedgerServiceImpl.BIZ_PAYMENT)
                .eq(AccountLedger::getDirection, 1)
                .ge(AccountLedger::getCreateTime, start)
                .le(AccountLedger::getCreateTime, end));

        Set<String> ledgerBizNos = credits.stream()
                .map(AccountLedger::getBizNo)
                .collect(Collectors.toCollection(HashSet::new));

        List<String> missingLedger = new ArrayList<>();
        BigDecimal paidAmount = BigDecimal.ZERO;
        for (PaymentOrder order : paidOrders) {
            paidAmount = paidAmount.add(order.getAmount() == null ? BigDecimal.ZERO : order.getAmount());
            String bizNo = String.valueOf(order.getId());
            if (!ledgerBizNos.contains(bizNo)) {
                missingLedger.add(order.getOrderNo() + "(" + bizNo + ")");
            }
        }

        Set<String> paidIds = paidOrders.stream()
                .map(o -> String.valueOf(o.getId()))
                .collect(Collectors.toCollection(HashSet::new));
        List<String> extraLedger = new ArrayList<>();
        BigDecimal ledgerAmount = BigDecimal.ZERO;
        for (AccountLedger ledger : credits) {
            ledgerAmount = ledgerAmount.add(ledger.getAmount() == null ? BigDecimal.ZERO : ledger.getAmount());
            if (!paidIds.contains(ledger.getBizNo())) {
                extraLedger.add(ledger.getBizNo());
            }
        }

        BigDecimal diff = paidAmount.subtract(ledgerAmount);
        String status = (missingLedger.isEmpty() && extraLedger.isEmpty() && diff.compareTo(BigDecimal.ZERO) == 0)
                ? "MATCHED" : "MISMATCH";

        Map<String, Object> detail = new HashMap<>();
        detail.put("missingLedgerOrders", missingLedger);
        detail.put("extraLedgerBizNos", extraLedger);
        detail.put("paidOrderNos", paidOrders.stream().map(PaymentOrder::getOrderNo).toList());

        PaymentReconcileReport report = paymentReconcileReportMapper.selectOne(new LambdaQueryWrapper<PaymentReconcileReport>()
                .eq(PaymentReconcileReport::getBizDate, date)
                .last("LIMIT 1"));
        if (report == null) {
            report = new PaymentReconcileReport();
            report.setBizDate(date);
            report.setCreateTime(LocalDateTime.now());
        }
        report.setStatus(status);
        report.setPaidOrderCount(paidOrders.size());
        report.setPaidOrderAmount(paidAmount);
        report.setLedgerCreditCount(credits.size());
        report.setLedgerCreditAmount(ledgerAmount);
        report.setMissingLedgerCount(missingLedger.size());
        report.setExtraLedgerCount(extraLedger.size());
        report.setAmountDiff(diff);
        try {
            report.setDetailJson(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            report.setDetailJson("{}");
        }

        if (report.getId() == null) {
            try {
                paymentReconcileReportMapper.insert(report);
            } catch (DuplicateKeyException e) {
                PaymentReconcileReport existing = paymentReconcileReportMapper.selectOne(new LambdaQueryWrapper<PaymentReconcileReport>()
                        .eq(PaymentReconcileReport::getBizDate, date)
                        .last("LIMIT 1"));
                if (existing != null) {
                    report.setId(existing.getId());
                    paymentReconcileReportMapper.updateById(report);
                }
            }
        } else {
            paymentReconcileReportMapper.updateById(report);
        }

        String severity = "MATCHED".equals(status) ? "INFO" : "CRITICAL";
        securityAuditService.record("PAYMENT_RECONCILE", severity, null, "system",
                "/admin/security/reconcile", "JOB",
                "支付日终对账 " + date + " => " + status,
                report.getDetailJson());
        log.info("支付对账完成 date={} status={} paid={} ledger={} missing={}",
                date, status, paidOrders.size(), credits.size(), missingLedger.size());
        return report;
    }

    @Override
    public IPage<PaymentReconcileReport> page(Integer page, Integer pageSize) {
        Page<PaymentReconcileReport> p = new Page<>(page == null ? 1 : page, pageSize == null ? 20 : pageSize);
        return paymentReconcileReportMapper.selectPage(p, new LambdaQueryWrapper<PaymentReconcileReport>()
                .orderByDesc(PaymentReconcileReport::getBizDate));
    }
}
