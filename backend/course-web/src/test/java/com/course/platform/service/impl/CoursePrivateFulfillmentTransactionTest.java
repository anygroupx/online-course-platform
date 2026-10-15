package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.event.CourseOrderFactsChangedEvent;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 可信事实与观察上界分开，管理员普通状态编辑不能冒充履约证据。 */
class CoursePrivateFulfillmentTransactionTest {
    CoursePrivateSettlementTransactionTest f;
    CoursePrivateFulfillmentService service;
    List<CourseOrderFactsChangedEvent> events=new ArrayList<>();
    UserAuthorityMapper authority;
    Long orderId;
    @BeforeEach void setup() throws Exception {
        f=new CoursePrivateSettlementTransactionTest();f.setup();orderId=f.buy("proof");f.f.f.jdbc.update("UPDATE course_order SET api_provider_id=6,third_order_id='remote-proof',dock_status=1 WHERE id=?",orderId);
        authority=mock(UserAuthorityMapper.class);when(authority.findAuthoritiesByUserId(8L)).thenReturn(List.of("order:update","course-private-settlement:execute"));
        var publisher=mock(org.springframework.context.ApplicationEventPublisher.class);doAnswer(invocation->{events.add(invocation.getArgument(0));return null;}).when(publisher).publishEvent(org.mockito.ArgumentMatchers.any(CourseOrderFactsChangedEvent.class));
        var target=new CoursePrivateFulfillmentService(f.f.orderRows,f.f.termOrders,f.f.events,f.f.f.users,new CoursePrivateOperationsAccess(f.f.f.users,authority),publisher,f.f.json,new CoursePrivateOperationsTime(f.f.clock));var proxy=new org.springframework.aop.framework.ProxyFactory(target);proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(f.f.f.tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));service=(CoursePrivateFulfillmentService)proxy.getProxy();
    }
    @AfterEach void clear(){f.clear();}
    CourseOrder before(){return f.f.orderRows.selectById(orderId);}
    void upstreamCompleted(){var old=before();f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);service.recordUpstream(old,OrderProgressResult.builder().orderStatus(2).build());}
    void reviewer(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(8L,null,List.of(new SimpleGrantedAuthority("order:update"),new SimpleGrantedAuthority("course-private-settlement:execute"))));}
    @Test void upstreamConfirmationLeavesActualTimeUnknownAndQualifiesOnlyProvenWindow(){upstreamCompleted();var order=before();assertNull(order.getFulfilledAt());assertEquals(LocalDateTime.now(f.f.clock),order.getCompletionConfirmedAt());assertEquals("UPSTREAM_STATUS",order.getCompletionConfirmationSource());f.evaluate();f.f.f.money("7.20",f.f.current().getSettledConsumptionAmount());assertNull(f.f.termOrders.selectByOrderId(orderId).getCompletedAt());assertEquals("CONFIRMED_BEFORE_DEADLINE",f.f.termOrders.selectByOrderId(orderId).getCompletionBasis());assertEquals(1,events.size());}
    @Test void repeatedObservationKeepsOriginalUpperBoundRatherThanWorkerTime(){upstreamCompleted();var original=before().getCompletionConfirmedAt();f.f.clock.advance(Duration.ofDays(40));service.recordUpstream(before(),OrderProgressResult.builder().orderStatus(2).build());assertEquals(original,before().getCompletionConfirmedAt());assertEquals(1,events.size());}
    @Test void lateObservationWithoutActualEvidenceDoesNotQualify(){f.f.clock.advance(Duration.ofDays(38));upstreamCompleted();f.evaluate();f.f.f.money("0",f.f.current().getSettledConsumptionAmount());assertNull(before().getFulfilledAt());}
    @Test void verifiedManualEvidencePreservesExactDateAndRequiresTwoCurrentPermissions(){f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);reviewer();var actual=LocalDateTime.now(f.f.clock);service.verifyExact(orderId,actual,"已核对原履约记录","供应方回执编号proof-1",8L);assertEquals(actual,before().getFulfilledAt());assertEquals("ADMIN_VERIFIED",before().getFulfillmentSource());assertEquals(1,events.size());}
    @Test void revokedPermissionAndFutureOrBeforePaymentDatesCannotCreateFacts(){f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);reviewer();assertThrows(BusinessException.class,()->service.verifyExact(orderId,LocalDateTime.now(f.f.clock).plusSeconds(1),"核查","证明",8L));assertThrows(BusinessException.class,()->service.verifyExact(orderId,LocalDateTime.now(f.f.clock).minusSeconds(1),"核查","证明",8L));when(authority.findAuthoritiesByUserId(8L)).thenReturn(List.of("order:update"));assertThrows(BusinessException.class,()->service.verifyExact(orderId,LocalDateTime.now(f.f.clock),"核查","证明",8L));assertNull(before().getFulfilledAt());assertEquals(0,events.size());}
    @Test void refundedAndArchivedOrdersCannotReceiveNewUpstreamConfirmation(){var old=before();f.f.f.jdbc.update("UPDATE course_order SET order_status=2,refunded_at=? WHERE id=?",LocalDateTime.now(f.f.clock),orderId);service.recordUpstream(old,OrderProgressResult.builder().orderStatus(2).build());assertNull(before().getCompletionConfirmedAt());f.f.f.jdbc.update("UPDATE course_order SET refunded_at=NULL,is_deleted=1 WHERE id=?",orderId);service.recordUpstream(old,OrderProgressResult.builder().orderStatus(2).build());assertEquals(0,events.size());}
    @Test void reopenedOrderCannotReuseEarlierCompletionForLateRecompletion(){upstreamCompleted();var old=before();f.f.f.jdbc.update("UPDATE course_order SET order_status=1 WHERE id=?",orderId);service.recordUpstream(old,OrderProgressResult.builder().orderStatus(1).build());assertNull(before().getCompletionConfirmedAt());f.f.clock.advance(Duration.ofDays(38));upstreamCompleted();f.evaluate();f.f.f.money("0",f.f.current().getSettledConsumptionAmount());assertEquals(LocalDateTime.now(f.f.clock),before().getCompletionConfirmedAt());}
    @Test void failedManualAuditRollsBackActualFactInsteadOfLosingEvidence(){f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);f.f.f.jdbc.execute("ALTER TABLE course_private_term_event ADD CONSTRAINT reject_manual_proof CHECK(event_type<>'FULFILLMENT_VERIFIED')");reviewer();assertThrows(BusinessException.class,()->service.verifyExact(orderId,LocalDateTime.now(f.f.clock),"核查","供应方凭据",8L));assertNull(before().getFulfilledAt());assertEquals(0,events.size());}
}
