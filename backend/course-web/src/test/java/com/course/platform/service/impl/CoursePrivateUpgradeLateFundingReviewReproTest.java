package com.course.platform.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 独立审查复现：迟到存量升级不应吞掉下一期开始后的未认领转入。 */
class CoursePrivateUpgradeLateFundingReviewReproTest {
    @Test void lateExistingBalanceUpgradeKeepsNewPeriodFundingForRenewal() throws Exception {
        var u = new CoursePrivateUpgradeTransactionTest();
        try {
            u.setup();
            u.f.f.f.config.getRules().setUpgradeDepositPolicy("EXISTING_BALANCE");
            u.f.snapshot();
            u.targetConfig.getRules().setRenewalPolicy("NEW_DEPOSIT");
            u.targetConfig.getRules().setRenewalConsumptionTarget(BigDecimal.ZERO);
            u.targetConfig.getRules().setRenewalDepositAmount(new BigDecimal("50"));
            u.f.f.f.f.jdbc.update("UPDATE course_private_plan_version SET renewal_policy='NEW_DEPOSIT',renewal_consumption_target=0,renewal_deposit_amount=50,config_snapshot=? WHERE id=2", u.f.f.f.json.writeValueAsString(u.targetConfig));
            u.deposit();
            var next = u.review(u.application("NEXT_TERM"));
            assertEquals("SCHEDULED", next.getStatus());
            var boundary = next.getStartAt();
            u.f.f.f.clock.advance(Duration.ofDays(31));
            u.f.f.f.deposit("50", "new_period_before_late_upgrade");
            assertEquals(2, u.f.f.f.allocations.selectUnallocatedTransferIn(u.f.f.f.wallet().getId(), 7L, null, null, null, null).size());
            var active = u.service.activateScheduled(next.getId());
            assertEquals("ACTIVE", active.getStatus());
            assertEquals(boundary, active.getStartAt());
            // 存量政策不认领新资金；该50元在新周期内成功转入，应参与本期新增预存续期。
            u.f.f.service.evaluate(active.getId());
            assertEquals("50.00", u.f.f.f.terms.selectById(active.getId()).getRenewalNetDepositAmount().setScale(2).toPlainString());
        } finally {
            u.clear();
        }
    }
}
