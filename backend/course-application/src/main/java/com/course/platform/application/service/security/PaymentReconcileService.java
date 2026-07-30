package com.course.platform.application.service.security;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.entity.PaymentReconcileReport;

import java.time.LocalDate;

public interface PaymentReconcileService {
    PaymentReconcileReport reconcile(LocalDate bizDate);

    IPage<PaymentReconcileReport> page(Integer page, Integer pageSize);
}
