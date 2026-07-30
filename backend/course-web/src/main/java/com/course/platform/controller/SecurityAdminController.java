package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.application.service.security.PaymentReconcileService;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.entity.PaymentReconcileReport;
import com.course.platform.domain.entity.SecurityAuditLog;
import com.course.platform.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "安全运维", description = "审计日志与支付对账")
@RestController
@RequestMapping("/admin/security")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SecurityAdminController {

    private final SecurityAuditService securityAuditService;
    private final PaymentReconcileService paymentReconcileService;

    @GetMapping("/audit-logs")
    @Operation(summary = "分页查询安全审计日志")
    public Result<IPage<SecurityAuditLog>> auditLogs(@RequestParam(required = false) String eventType,
                                                     @RequestParam(required = false) String severity,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        SecurityUtils.requireAdmin();
        return Result.success(securityAuditService.query(eventType, severity, page, pageSize));
    }

    @PostMapping("/reconcile")
    @Operation(summary = "手动触发支付日终对账")
    public Result<PaymentReconcileReport> reconcile(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        SecurityUtils.requireAdmin();
        return Result.success(paymentReconcileService.reconcile(bizDate));
    }

    @GetMapping("/reconcile/reports")
    @Operation(summary = "查询对账报告")
    public Result<IPage<PaymentReconcileReport>> reports(@RequestParam(defaultValue = "1") Integer page,
                                                         @RequestParam(defaultValue = "20") Integer pageSize) {
        SecurityUtils.requireAdmin();
        return Result.success(paymentReconcileService.page(page, pageSize));
    }
}
