package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 全局生命周期与原资金账户分离，暂停不延长期限，终止保留余额和可追溯原期。 */
class CoursePrivateLifecycleControlTransactionTest {
    CoursePrivateRenewalTransactionTest f;
    CoursePrivateLifecycleControlService service;
    @BeforeEach void setup() throws Exception {f=new CoursePrivateRenewalTransactionTest();f.setup();var authority=mock(UserAuthorityMapper.class);when(authority.findAuthoritiesByUserId(8L)).thenReturn(List.of("course-private-term:adjust"));var target=new CoursePrivateLifecycleControlService(f.f.f.f.users,f.f.f.f.contracts,f.f.f.f.wallets,f.f.f.terms,f.f.f.events,f.f.service,new CoursePrivateOperationsAccess(f.f.f.f.users,authority),f.f.f.json,new CoursePrivateOperationsTime(f.f.f.clock));var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(f.f.f.f.tx.getTransactionManager(),new AnnotationTransactionAttributeSource()));service=(CoursePrivateLifecycleControlService)proxy.getProxy();}
    @AfterEach void clear(){f.clear();}
    void auth(){org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(8L,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("course-private-term:adjust"))));}
    int version(){return f.f.f.f.contracts.selectByBinding(7L,9L).getVersion();}
    CoursePrivateLifecycleControlService.StateResult change(String action,int expected,String key){auth();return service.change(f.source().getContractId(),expected,8L,action,"根据合作状态调整，保留原金额和有效期",key);}
    @Test void pauseBlocksPurchasesWithoutChangingMoneyOrPeriod(){var before=f.source();change("SUSPEND",version(),"pause");assertThrows(BusinessException.class,()->f.f.buy("paused"));assertEquals(before.getStartAt(),f.source().getStartAt());assertEquals(before.getEndAt(),f.source().getEndAt());f.f.f.f.money("100",f.f.f.wallet().getBalance());assertEquals("SUSPENDED",f.f.f.f.contracts.selectByBinding(7L,9L).getStatus());}
    @Test void resumeUsesRemainingPeriodAndNeverRestartsFullDuration(){var end=f.source().getEndAt();change("SUSPEND",version(),"pause");f.f.f.clock.advance(Duration.ofDays(3));change("RESUME",version(),"resume");assertEquals(end,f.source().getEndAt());assertNotNull(f.f.buy("resumed"));f.f.f.f.money("92.80",f.f.f.wallet().getBalance());}
    @Test void resumeAfterExpirationDoesNotRestoreExpiredPrice(){change("SUSPEND",version(),"pause");f.f.f.clock.advance(Duration.ofDays(31));assertEquals("EXPIRED",change("RESUME",version(),"expired_resume").status());assertThrows(BusinessException.class,()->f.f.buy("expired"));}
    @Test void terminationCancelsPendingNextAndKeepsBalanceAndOriginalPeriod(){f.qualify();f.f.f.clock.advance(Duration.ofDays(24));var next=f.renew();var end=f.source().getEndAt();change("TERMINATE",version(),"terminate");assertEquals("TERMINATED",f.f.f.f.contracts.selectByBinding(7L,9L).getStatus());assertEquals("CANCELLED",f.f.f.terms.selectById(next.getId()).getStatus());assertEquals("TERMINATED",f.source().getStatus());assertEquals(end,f.source().getEndAt());assertNotNull(f.source().getSupersededAt());f.f.f.f.money("85.60",f.f.f.wallet().getBalance());assertThrows(BusinessException.class,()->change("RESUME",version(),"cannot_resume"));}
    @Test void idempotentStateRetryReturnsOriginalResultAfterLaterChange(){int before=version();var first=change("SUSPEND",before,"one");change("RESUME",version(),"two");assertEquals(first,change("SUSPEND",before,"one"));assertEquals("ACTIVE",f.f.f.f.contracts.selectByBinding(7L,9L).getStatus());assertThrows(BusinessException.class,()->change("TERMINATE",before,"one"));}
    @Test void staleVersionOrForgedActorCannotChangeLifecycle(){int old=version();change("SUSPEND",old,"pause");assertThrows(BusinessException.class,()->change("RESUME",old,"stale"));f.f.f.auth(7);assertThrows(BusinessException.class,()->service.change(f.source().getContractId(),version(),8L,"TERMINATE","伪造主体","forged"));assertEquals("SUSPENDED",f.f.f.f.contracts.selectByBinding(7L,9L).getStatus());}
    @Test void failedAuditRollsBackTerminationAndAllPendingPeriods(){f.qualify();f.f.f.clock.advance(Duration.ofDays(24));var next=f.renew();f.f.f.f.jdbc.execute("ALTER TABLE course_private_term_event ADD CONSTRAINT reject_state CHECK(event_type<>'CONTRACT_TERMINATED')");assertThrows(RuntimeException.class,()->change("TERMINATE",version(),"failed"));assertEquals("ACTIVE",f.f.f.f.contracts.selectByBinding(7L,9L).getStatus());assertEquals("SCHEDULED",f.f.f.terms.selectById(next.getId()).getStatus());assertNull(f.source().getSupersededAt());}
    @Test void concurrentRepeatedTerminationHasOneAudit() throws Exception {int expected=version();var results=f.f.f.f.concurrent(()->change("TERMINATE",expected,"same"),()->change("TERMINATE",expected,"same"));assertEquals(0,results.stream().filter(RuntimeException.class::isInstance).count());assertEquals(1,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_event WHERE event_type='CONTRACT_TERMINATED'",Integer.class));}
}
