package com.course.platform.task;

import com.course.platform.application.service.security.PaymentReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileTask {

    private final PaymentReconcileService paymentReconcileService;

    @Value("${app.security.reconcile-enabled:true}")
    private boolean enabled;

    /** 每天 01:15 对前一自然日 */
    @Scheduled(cron = "${app.security.reconcile-cron:0 15 1 * * ?}")
    public void runDaily() {
        if (!enabled) {
            return;
        }
        try {
            paymentReconcileService.reconcile(LocalDate.now().minusDays(1));
        } catch (Exception e) {
            log.error("支付日终对账任务失败: {}", e.getMessage(), e);
        }
    }
}
