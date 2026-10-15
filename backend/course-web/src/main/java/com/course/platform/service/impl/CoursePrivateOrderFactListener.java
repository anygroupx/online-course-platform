package com.course.platform.service.impl;

import com.course.platform.domain.entity.CoursePrivateTermEvent;
import com.course.platform.domain.event.CourseOrderFactsChangedEvent;
import com.course.platform.infra.persistence.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.*;
import org.springframework.transaction.support.TransactionTemplate;

/** 原事实提交后开独立事务核验；失败不把已经完成的退款响应伪装成资金失败。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoursePrivateOrderFactListener {
    private final CoursePrivateSettlementService settlement;
    private final CoursePrivateTermOrderMapper associations;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateTermEventMapper events;
    private final PlatformTransactionManager transactions;
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(CourseOrderFactsChangedEvent fact) {
        var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try{tx.executeWithoutResult(status->{var association=associations.selectByOrderId(fact.orderId());if(association==null)return;settlement.reconcileOrder(fact.orderId(),fact.reason());String biz="order_fact_"+fact.eventId();if(events.selectByBizNo(biz)!=null)return;var term=terms.selectById(association.getTermId());var event=new CoursePrivateTermEvent();event.setContractId(association.getContractId());event.setTermId(association.getTermId());event.setEventType("ORDER_FACT_"+fact.source());event.setBeforeJson(fact.beforeJson());event.setAfterJson(fact.afterJson());event.setOperatorId(fact.operatorId());event.setReason(fact.reason());event.setBizNo(biz);event.setTargetVersionId(term.getVersionId());event.setHappenedAt(fact.happenedAt());if(events.insert(event)!=1)throw new IllegalStateException("订单事实审计写入失败");});}
        catch(RuntimeException error){log.warn("周期事实核验暂未完成，将由定时对账补齐：orderId={}, 原因={}",fact.orderId(),error.getClass().getSimpleName());}
    }
}
