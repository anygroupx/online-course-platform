package com.course.platform.service.impl;

import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 后台兜底使用实际周期事务，重复实例不产生重复结算，默认开关不访问未迁移表。 */
class CoursePrivateOperationsWorkerTest {
    private static CoursePrivateOperationsWorker fast(CoursePrivateSettlementTransactionTest f) {
        var worker=new CoursePrivateOperationsWorker(f.f.terms,f.f.f.wallets,f.f.service,f.service,new CoursePrivateOperationsTime(f.f.clock));
        org.springframework.test.util.ReflectionTestUtils.setField(worker,"enabled",true);
        return worker;
    }
    @Test void twoWorkersAndManualRetryUseOneNextPeriod() throws Exception {var f=new CoursePrivateRenewalTransactionTest();try{f.setup();f.qualify();f.f.expire();var batch=mock(CoursePrivateContractTermMapper.class);when(batch.selectReconciliationBatch(0L,100)).thenReturn(List.of(f.source()));var time=new CoursePrivateOperationsTime(f.f.f.clock);var first=new CoursePrivateOperationsWorker(batch,f.f.f.f.wallets,f.f.f.service,f.f.service,time);var second=new CoursePrivateOperationsWorker(batch,f.f.f.f.wallets,f.f.f.service,f.f.service,time);for(var worker:List.of(first,second)){org.springframework.test.util.ReflectionTestUtils.setField(worker,"enabled",true);org.springframework.test.util.ReflectionTestUtils.setField(worker,"renewal",f.service);}var outcomes=f.f.f.f.concurrent(first::sweep,second::sweep);assertEquals(0,outcomes.stream().filter(RuntimeException.class::isInstance).count());assertEquals(2,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_term",Integer.class));var next=f.renew();Long app=f.application();f.f.f.auth(8);assertEquals(next.getId(),f.service.review(app,0,8L,"运营核验自动续期结果").getId());assertEquals(1,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_term WHERE renewal_source_term_id=?",Integer.class,f.sourceId));}finally{f.clear();}}
    @Test void disabledWorkerDoesNotReadTables(){var terms=mock(CoursePrivateContractTermMapper.class);var worker=new CoursePrivateOperationsWorker(terms,mock(CourseReservedWalletMapper.class),mock(CoursePrivateTermService.class),mock(CoursePrivateSettlementService.class),new CoursePrivateOperationsTime(java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai"))));worker.reconcile();worker.sweep();verifyNoInteractions(terms);}
    @Test void twoWorkersReconcileSameExpiredPeriodOnce() throws Exception {var f=new CoursePrivateSettlementTransactionTest();try{f.setup();var id=f.buy("worker");f.complete(id);f.f.clock.advance(Duration.ofDays(30));var batch=mock(CoursePrivateContractTermMapper.class);when(batch.selectReconciliationBatch(0L,100)).thenReturn(List.of(f.f.current()));var time=new CoursePrivateOperationsTime(f.f.clock);var first=new CoursePrivateOperationsWorker(batch,f.f.f.wallets,f.f.service,f.service,time);var second=new CoursePrivateOperationsWorker(batch,f.f.f.wallets,f.f.service,f.service,time);org.springframework.test.util.ReflectionTestUtils.setField(first,"enabled",true);org.springframework.test.util.ReflectionTestUtils.setField(second,"enabled",true);var outcomes=f.f.f.concurrent(first::sweep,second::sweep);assertEquals(0,outcomes.stream().filter(RuntimeException.class::isInstance).count());assertEquals(1,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));assertNotNull(f.f.current().getFinalizedAt());}finally{f.clear();}}

    /** 快档不能碰已经结算过、且远未到期的健康周期——这是本次修复的核心回归。
     *  结算指纹会去重，所以不能只看结算行数；必须直接观测 evaluate 是否被调用。 */
    @Test void liveActiveTermIsNotReevaluatedByFastTick() throws Exception {
        var f=new CoursePrivateSettlementTransactionTest();
        try{
            f.setup();                                  // ACTIVE，end_at 在 30 天后
            f.evaluate();                               // 已有首次 PREVIEW 结算
            assertEquals(1,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));
            var settlement=mock(CoursePrivateSettlementService.class);
            var worker=new CoursePrivateOperationsWorker(f.f.terms,f.f.f.wallets,f.f.service,settlement,new CoursePrivateOperationsTime(f.f.clock));
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"enabled",true);
            worker.reconcile();
            verify(settlement,never()).evaluate(anyLong());
            assertEquals(1,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));
        }finally{f.clear();}
    }

    /** 从未结算的已生效周期由快档补首次结算；到期后再由到期分支终局。 */
    @Test void termIsEvaluatedOnTickAfterEndAt() throws Exception {
        var f=new CoursePrivateSettlementTransactionTest();
        try{
            f.setup();
            assertEquals(0,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));
            var worker=fast(f);
            worker.reconcile();
            assertEquals(1,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class),"首次结算应由快档补齐");
            assertNull(f.f.current().getFinalizedAt(),"未到期不得提前终局");
            f.expire();
            worker.reconcile();
            assertNotNull(f.f.current().getFinalizedAt(),"到期后应终局");
        }finally{f.clear();}
    }

    /** 待生效排程到达开始时刻后，由快档激活为 ACTIVE。 */
    @Test void scheduledTermActivatesOnFastTick() throws Exception {
        var g=new CoursePrivateTermLifecycleTransactionTest();
        try{
            g.setup();
            g.config.getRules().setActivationFrom(LocalDateTime.now(g.clock).plusDays(2));
            g.seed();g.enroll();g.deposit("100","s");
            assertEquals("SCHEDULED",g.current().getStatus());
            assertEquals(0,g.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));
            var worker=new CoursePrivateOperationsWorker(g.terms,g.f.wallets,g.service,mock(CoursePrivateSettlementService.class),new CoursePrivateOperationsTime(g.clock));
            org.springframework.test.util.ReflectionTestUtils.setField(worker,"enabled",true);
            worker.reconcile();
            assertEquals("SCHEDULED",g.current().getStatus(),"未到开始时刻不得激活");
            g.clock.advance(Duration.ofDays(2));
            worker.reconcile();
            assertEquals("ACTIVE",g.current().getStatus(),"到达开始时刻应由快档激活");
        }finally{g.clear();}
    }
}
