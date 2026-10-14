package com.course.platform.service.impl;

import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际Spring提交后监听与独立事务，原核验回滚不结算，监听失败不丢已提交证据。 */
class CoursePrivateFactAfterCommitTest {
    CoursePrivateSettlementTransactionTest f;
    AnnotationConfigApplicationContext context;
    CoursePrivateFulfillmentService service;
    Long orderId;
    @BeforeEach void setup() throws Exception {
        f=new CoursePrivateSettlementTransactionTest();f.setup();orderId=f.buy("event");
        context=new AnnotationConfigApplicationContext();context.registerBean(TransactionalEventListenerFactory.class);context.registerBean(CoursePrivateOrderFactListener.class,()->new CoursePrivateOrderFactListener(f.service,f.f.termOrders,f.f.terms,f.f.events,f.f.f.tx.getTransactionManager()));context.refresh();
        var authority=mock(UserAuthorityMapper.class);when(authority.findAuthoritiesByUserId(8L)).thenReturn(List.of("order:update","course-private-settlement:execute"));
        var target=new CoursePrivateFulfillmentService(f.f.orderRows,f.f.termOrders,f.f.events,f.f.f.users,new CoursePrivateOperationsAccess(f.f.f.users,authority),context,f.f.json,new CoursePrivateOperationsTime(f.f.clock));var proxy=new org.springframework.aop.framework.ProxyFactory(target);proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(f.f.f.tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));service=(CoursePrivateFulfillmentService)proxy.getProxy();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(8L,null,List.of(new SimpleGrantedAuthority("order:update"),new SimpleGrantedAuthority("course-private-settlement:execute"))));
    }
    @AfterEach void clear(){context.close();f.clear();}
    void verify(){service.verifyExact(orderId,LocalDateTime.now(f.f.clock),"真实履约核查","供应方回执proof-event",8L);}
    @Test void committedFactTriggersRealIndependentSettlement(){f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);verify();f.f.f.money("7.20",f.f.current().getSettledConsumptionAmount());assertEquals("QUALIFIED",f.f.termOrders.selectByOrderId(orderId).getQualificationStatus());assertEquals(1,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_event WHERE event_type='ORDER_FACT_ADMIN_VERIFIED'",Integer.class));}
    @Test void rollbackDoesNotDeliverSettlement(){assertThrows(IllegalStateException.class,()->f.f.f.tx.executeWithoutResult(tx->{f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);verify();throw new IllegalStateException("测试原核验事务回滚");}));assertNull(f.f.orderRows.selectById(orderId).getFulfilledAt());assertEquals(0,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));assertEquals(0,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_event WHERE event_type='FULFILLMENT_VERIFIED'",Integer.class));}
    @Test void listenerFailureKeepsOriginalEvidenceAndAllowsLaterReconciliation(){f.f.f.jdbc.execute("ALTER TABLE course_private_term_event ADD CONSTRAINT reject_listener CHECK(event_type<>'ORDER_FACT_ADMIN_VERIFIED')");f.f.f.jdbc.update("UPDATE course_order SET order_status=2 WHERE id=?",orderId);assertDoesNotThrow(this::verify);assertNotNull(f.f.orderRows.selectById(orderId).getFulfilledAt());assertEquals(1,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_event WHERE event_type='FULFILLMENT_VERIFIED' AND after_json LIKE '%proof-event%'",Integer.class));assertEquals(0,f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_settlement",Integer.class));f.service.reconcileOrder(orderId,"后台重跑核验");f.f.f.money("7.20",f.f.current().getSettledConsumptionAmount());}
}
