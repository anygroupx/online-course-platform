package com.course.platform.service.impl;

import com.course.platform.domain.entity.CoursePrivateContractTerm;
import com.course.platform.infra.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/** 事件之外的有界周期对账，多实例依靠账户锁及不可变结算指纹，不发送外部消息。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoursePrivateOperationsWorker {
    private final CoursePrivateContractTermMapper terms;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivateTermService lifecycle;
    private final CoursePrivateSettlementService settlement;
    private final CoursePrivateOperationsTime time;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateRenewalService renewal;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateUpgradeService upgrade;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateNotificationService notifications;
    @Value("${course.private-operations.worker-enabled:false}") private boolean enabled;
    private long cursor=0;

    /** 快档：只处理确实可能改变状态或从未结算的周期，避免每 5 秒重算全表。 */
    @Scheduled(fixedDelayString="${course.private-operations.worker-delay-ms:5000}")
    public synchronized void reconcile() {
        if(!enabled)return;var now=time.now();
        process(terms.selectScheduledDue(now,100));
        process(terms.selectUnevaluatedActive(now,100));
        process(terms.selectDue(now,100));
        if(notifications!=null)notifications.reconcileBatch();
    }

    /** 慢档：有界轮转扫描兜底事实事件，不能依赖上游更新时间冒充业务时间或漏掉历史退款。 */
    @Scheduled(fixedDelayString="${course.private-operations.sweep-delay-ms:300000}")
    public synchronized void sweep() {
        if(!enabled)return;
        var batch=terms.selectReconciliationBatch(cursor,100);
        if(batch.isEmpty()){cursor=0;return;}
        for(var term:batch){handle(term);cursor=term.getId();}
    }

    private void process(List<CoursePrivateContractTerm> batch){for(var term:batch)handle(term);}

    private void handle(CoursePrivateContractTerm term) {
        for(int attempt=0;attempt<3;attempt++){try{
            if("SCHEDULED".equals(term.getStatus())&&term.getStartAt()!=null&&!time.now().isBefore(term.getStartAt())){
                if(term.getRenewalSourceTermId()==null){var wallet=wallets.selectByBinding(term.getUserId(),term.getPlatformId());if(wallet!=null)lifecycle.afterWalletChanged(term.getUserId(),wallet.getId());}
                else if("UPGRADE_FUNDS".equals(term.getQualificationKind())&&upgrade!=null)upgrade.activateScheduled(term.getId());
                else if(renewal!=null)renewal.activateScheduled(term.getRenewalSourceTermId());
            }
            // 未取得本期资格的排程不能作为已经开始的消费周期推进；来源期仍持续结算复核。
            var latest=terms.selectById(term.getId());if(latest==null)latest=term;
            if(latest.getActivatedAt()!=null&&latest.getStartAt()!=null&&!time.now().isBefore(latest.getStartAt()))settlement.evaluate(latest.getId());
            // 已明确同意且资金达标的下一档先排定；普通续期仍复用同一来源唯一身份。
            if(upgrade!=null)upgrade.upgradeAutomatic(term.getId());
            var next=terms.selectByRenewalSource(term.getId());if(renewal!=null&&(next==null||!"UPGRADE_FUNDS".equals(next.getQualificationKind())))renewal.renewAutomatic(term.getId());
            break;}
            catch(org.springframework.dao.TransientDataAccessException transientFailure){if(attempt==2)log.warn("周期对账并发冲突已达重试上限：termId={}",term.getId());else try{Thread.sleep(10L<<(attempt));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}}
            catch(RuntimeException invalid){log.warn("周期对账需核查，保留原事实：termId={}, 原因={}",term.getId(),invalid.getClass().getSimpleName());break;}}
    }
}
