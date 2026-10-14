package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.CoursePrivateContractTerm;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 补授是明确的人工作用，不修改原消费事实、不捏造预存、不产生第二份下一期。 */
class CoursePrivateManualGrantTransactionTest {
    CoursePrivateRenewalTransactionTest f;
    UserAuthorityMapper authorities;
    List<String> permissions=List.of("course-private-contract:review","course-private-term:adjust","course-private-settlement:execute");
    @BeforeEach void setup() throws Exception {
        f=new CoursePrivateRenewalTransactionTest();f.setup();f.f.f.config.getRules().setAllowManualGrant(true);f.snapshot();
        authorities=mock(UserAuthorityMapper.class);when(authorities.findAuthoritiesByUserId(8L)).thenReturn(permissions);
        ReflectionTestUtils.setField(f.service,"access",new CoursePrivateOperationsAccess(f.f.f.f.users,authorities));
    }
    @AfterEach void clear(){f.clear();}
    void auth(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(8L,null,permissions.stream().map(SimpleGrantedAuthority::new).toList()));}
    CoursePrivateContractTerm grant(Long app){auth();return f.service.grantReviewed(app,0,8L,"根据代理合作证明人工补授，保留原未达标事实");}
    @Test void unmetTargetMayReceiveExplicitGrantWithoutInventingConsumptionOrMovingMoney(){f.f.expire();Long app=f.application();var next=grant(app);assertEquals("ACTIVE",next.getStatus());assertEquals("MANUAL_GRANT",next.getQualificationKind());assertEquals("NOT_QUALIFIED",f.source().getQualificationStatus());f.f.f.f.money("0",f.source().getSettledConsumptionAmount());f.f.f.f.money("100",f.f.f.wallet().getBalance());assertEquals(1,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_funding_allocation",Integer.class));assertEquals(next.getId(),f.f.f.events.selectByBizNo("manual_renewal_"+f.sourceId).getTermId());assertEquals(next.getId(),grant(app).getId());}
    @Test void planMustHaveExplicitlyAllowedManualGrant() throws Exception {f.f.f.config.getRules().setAllowManualGrant(false);f.snapshot();f.f.expire();Long app=f.application();assertThrows(BusinessException.class,()->grant(app));assertEquals("PENDING",f.f.f.applications.selectById(app).getStatus());assertEquals(1,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_term",Integer.class));}
    @Test void allThreeCurrentDatabasePermissionsAreRequired(){f.f.expire();Long app=f.application();auth();for(String permission:permissions){when(authorities.findAuthoritiesByUserId(8L)).thenReturn(permissions.stream().filter(v->!v.equals(permission)).toList());assertThrows(BusinessException.class,()->f.service.grantReviewed(app,0,8L,"人工补授依据"));}assertEquals("PENDING",f.f.f.applications.selectById(app).getStatus());}
    @Test void alreadyScheduledNextIsConvertedWithEvidenceWithoutChangingFrozenPriceOrDates(){f.qualify();f.f.f.clock.advance(Duration.ofDays(24));var scheduled=f.renew();Long app=f.application();var order=f.f.f.orderRows.selectById(f.f.f.f.jdbc.queryForObject("SELECT MIN(id) FROM course_order",Long.class));f.f.f.f.tx.executeWithoutResult(tx->{f.f.f.f.service.refund(order,8L);f.f.f.orderRows.markRefunded(order.getId());});var granted=grant(app);assertEquals(scheduled.getId(),granted.getId());assertEquals(scheduled.getStartAt(),granted.getStartAt());assertEquals(scheduled.getEndAt(),granted.getEndAt());assertEquals(scheduled.getEffectiveFixedPrice(),granted.getEffectiveFixedPrice());assertEquals("MANUAL_GRANT",granted.getQualificationKind());f.f.f.clock.advance(Duration.ofDays(6));var active=f.service.activateScheduled(f.sourceId);assertEquals("ACTIVE",active.getStatus());assertEquals("NOT_QUALIFIED",f.source().getQualificationStatus());assertEquals(2,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_term",Integer.class));}
    @Test void manualScheduleActivatesDespiteAutomaticFlagAndDoesNotRevokeOnOldRefund(){f.f.f.clock.advance(Duration.ofDays(24));var next=grant(f.application());ReflectionTestUtils.setField(f.service,"automaticEnabled",false);f.f.f.clock.advance(Duration.ofDays(6));assertEquals(next.getId(),f.service.activateScheduled(f.sourceId).getId());assertEquals("ACTIVE",f.f.f.current().getStatus());assertEquals("NOT_QUALIFIED",f.source().getQualificationStatus());}
    @Test void auditFailureRollsBackWholeGrantAndOriginalSettlement(){f.f.expire();Long app=f.application();f.f.f.f.jdbc.execute("ALTER TABLE course_private_term_event ADD CONSTRAINT reject_manual CHECK(event_type<>'MANUAL_RENEWAL_GRANTED')");assertThrows(RuntimeException.class,()->grant(app));assertEquals("PENDING",f.f.f.applications.selectById(app).getStatus());assertNull(f.source().getFinalizedAt());assertEquals(1,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_term",Integer.class));}
    @Test void concurrentSupplementReviewsStillProduceOneNext() throws Exception {f.f.expire();Long app=f.application();var outcomes=f.f.f.f.concurrent(()->grant(app),()->grant(app));assertEquals(0,outcomes.stream().filter(RuntimeException.class::isInstance).count());assertEquals(2,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_contract_term",Integer.class));assertEquals(1,f.f.f.f.jdbc.queryForObject("SELECT COUNT(*) FROM course_private_term_event WHERE event_type='MANUAL_RENEWAL_GRANTED'",Integer.class));}
    @Test void manualPolicyUsesHumanGrantAndCannotBeAutoGranted() throws Exception {f.f.f.config.getRules().setMode("TIME_PREPAID");f.f.f.config.getRules().setRenewalPolicy("MANUAL");f.f.f.config.getRules().setRenewalAuto(false);f.f.f.config.getRules().setRenewalConsumptionTarget(java.math.BigDecimal.ZERO);f.snapshot();f.f.expire();assertNull(f.renew());var next=grant(f.application());assertEquals("MANUAL_GRANT",next.getQualificationKind());assertEquals("ACTIVE",next.getStatus());assertEquals("NOT_QUALIFIED",f.source().getQualificationStatus());}
    @Test void cancelledScheduleCannotBeReportedAsApprovedGrant(){f.qualify();f.f.f.clock.advance(Duration.ofDays(24));var next=f.renew();f.f.f.f.jdbc.update("UPDATE course_private_contract_term SET status='CANCELLED' WHERE id=?",next.getId());Long app=f.application();assertThrows(BusinessException.class,()->grant(app));assertEquals("PENDING",f.f.f.applications.selectById(app).getStatus());assertNull(f.f.f.events.selectByBizNo("manual_renewal_"+f.sourceId));}
}
