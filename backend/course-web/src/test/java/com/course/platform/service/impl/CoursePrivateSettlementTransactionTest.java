package com.course.platform.service.impl;

import com.course.platform.domain.entity.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/** 消费结算真实连接原订单、扣款及退款，验证终局、幂等、归档追溯和晚退款复核。 */
class CoursePrivateSettlementTransactionTest {
    CoursePrivateTermLifecycleTransactionTest f;
    CoursePrivateSettlementService service;
    com.course.platform.application.service.order.CourseOrderService orders;
    @BeforeEach void setup() throws Exception {
        f=new CoursePrivateTermLifecycleTransactionTest();f.setup();f.config.getRules().setMode("PERFORMANCE_RENEWAL");f.config.getRules().setRenewalPolicy("PERFORMANCE");f.config.getRules().setRenewalConsumptionTarget(new BigDecimal("14.40"));f.seed();f.enroll();f.deposit("100","a");orders=f.orderService();
        var target=new CoursePrivateSettlementService(f.f.users,f.f.contracts,f.f.wallets,f.terms,f.termOrders,f.settlements,f.allocations,f.events,f.json,new CoursePrivateOperationsTime(f.clock));
        var proxy=new org.springframework.aop.framework.ProxyFactory(target);proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(f.f.tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));service=(CoursePrivateSettlementService)proxy.getProxy();
    }
    @AfterEach void clear(){f.clear();}
    Long buy(String key){f.auth(7);return orders.createOrder(f.orderRequest(key),7L);}
    void complete(Long id){f.f.jdbc.update("UPDATE course_order SET order_status=2,fulfilled_at=?,fulfillment_source='SELF_OPERATED_COMPLETED' WHERE id=?",LocalDateTime.now(f.clock),id);}
    CoursePrivateTermSettlement evaluate(){return service.evaluate(f.current().getId());}
    void expire(){f.clock.advance(Duration.ofDays(30));}
    @Test void activeEmptyPeriodNeverFinalizes(){var row=evaluate();assertEquals("PREVIEW",row.getEvaluationType());assertEquals("PENDING",row.getResult());assertNull(f.current().getFinalizedAt());assertEquals("ACTIVE",f.current().getStatus());}
    @Test void pendingRenewalTargetDoesNotPreventCurrentPurchases(){Long first=buy("first");evaluate();assertEquals("PENDING",f.current().getQualificationStatus());Long second=buy("second");assertNotEquals(first,second);assertEquals(2,f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_order",Integer.class));f.f.money("85.60",f.wallet().getBalance());}
    @Test void exactCompletedOrdersQualifyAndFinalizeOnlyAfterEnd(){Long first=buy("first"),second=buy("second");complete(first);complete(second);var early=evaluate();assertEquals("QUALIFIED",early.getResult());assertEquals("PREVIEW",early.getEvaluationType());assertNull(f.current().getFinalizedAt());expire();var closed=evaluate();assertEquals("FINAL",closed.getEvaluationType());assertEquals("QUALIFIED",closed.getResult());f.f.money("14.40",f.current().getSettledConsumptionAmount());assertEquals(2,f.current().getSettledOrderCount());assertNotNull(f.current().getFinalizedAt());}
    @Test void unchangedReevaluationHasOneImmutableRevision(){buy("first");var first=evaluate();var again=evaluate();assertEquals(first.getId(),again.getId());assertEquals(1,f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));}
    @Test void incompleteOrdersEnterGraceThenFinalFailureWithoutExtendingPrice(){buy("first");expire();var grace=evaluate();assertEquals("PENDING",grace.getResult());assertEquals("PENDING_SETTLEMENT",f.current().getStatus());assertNull(f.current().getFinalizedAt());assertThrows(com.course.platform.common.exception.BusinessException.class,()->buy("expired"));f.clock.advance(Duration.ofDays(7));assertEquals("NOT_QUALIFIED",evaluate().getResult());assertEquals("EXPIRED",f.current().getStatus());}
    @Test void realCompletionWithinGraceCountsOriginalCycle(){Long first=buy("first"),second=buy("second");expire();evaluate();f.clock.advance(Duration.ofDays(3));complete(first);complete(second);assertEquals("QUALIFIED",evaluate().getResult());f.f.money("14.40",f.current().getSettledConsumptionAmount());}
    @Test void archivedFulfilledOrderRemainsPartOfSettlement(){Long first=buy("first"),second=buy("second");complete(first);complete(second);f.f.jdbc.update("UPDATE course_order SET is_deleted=1 WHERE id=?",first);expire();assertEquals("QUALIFIED",evaluate().getResult());assertEquals(2,f.current().getSettledOrderCount());}
    @Test void finalLateRefundAdjustsOriginAndMarksReviewWithoutChangingExecutedNext(){Long first=buy("first"),second=buy("second");complete(first);complete(second);expire();evaluate();var origin=f.current();Long originalId=origin.getId();var originalFinal=origin.getFinalizedAt();
        var next=new CoursePrivateContractTerm();org.springframework.beans.BeanUtils.copyProperties(origin,next);next.setId(null);next.setTermNo(2);next.setRenewalSourceTermId(originalId);next.setStartAt(origin.getEndAt());next.setEndAt(origin.getEndAt().plusDays(30));next.setSettlementDeadlineAt(next.getEndAt().plusDays(7));next.setStatus("ACTIVE");next.setVersion(0);next.setFinalizedAt(null);next.setSettledConsumptionAmount(BigDecimal.ZERO);next.setSettledOrderCount(0);f.terms.insert(next);f.f.jdbc.update("UPDATE course_private_contract SET current_term_id=?,status='ACTIVE',valid_from=?,valid_until=? WHERE id=?",next.getId(),next.getStartAt(),next.getEndAt(),next.getContractId());
        var order=f.orderRows.selectById(first);f.f.tx.executeWithoutResult(tx->{f.f.service.refund(order,8L);assertEquals(1,f.orderRows.markRefunded(first));});service.reconcileOrder(first,"原支付全额退款");
        var old=f.terms.selectById(originalId);f.f.money("7.20",old.getSettledConsumptionAmount());assertTrue(old.getReviewRequired());assertEquals("REVIEW_REQUIRED",old.getQualificationStatus());assertEquals(originalFinal,old.getFinalizedAt());assertEquals("ACTIVE",f.terms.selectById(next.getId()).getStatus());assertEquals(next.getId(),f.f.contracts.selectByBinding(7L,9L).getCurrentTermId());f.f.money("92.80",f.wallet().getBalance());var reviewed=f.settlements.selectLatestByTerm(originalId);assertEquals("REFUND_REVIEW",reviewed.getEvaluationType());assertEquals(reviewed.getId(),service.reconcileOrder(first,"重复事实核验").getId());assertEquals(2,f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement WHERE term_id=?",Integer.class,originalId));}
    @Test void projectionOrAuditFailureRollsBackAllSettlementWrites(){Long id=buy("first");complete(id);f.f.jdbc.execute("ALTER TABLE course_private_term_event ADD CONSTRAINT reject_settlement_event CHECK(event_type NOT LIKE 'SETTLEMENT%')");assertThrows(RuntimeException.class,this::evaluate);assertEquals(0,f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));assertEquals("PAID",f.termOrders.selectByOrderId(id).getQualificationStatus());f.f.money("0",f.current().getSettledConsumptionAmount());}
    @Test void concurrentReconciliationsCreateOneRevision() throws Exception {Long id=buy("first");complete(id);var termId=f.current().getId();var results=f.f.concurrent(()->service.evaluate(termId),()->service.evaluate(termId));assertEquals(0,results.stream().filter(RuntimeException.class::isInstance).count());assertEquals(1,f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));}
}
