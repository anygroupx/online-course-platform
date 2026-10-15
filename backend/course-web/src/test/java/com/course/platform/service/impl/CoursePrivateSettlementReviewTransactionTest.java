package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 人工只确认当前净事实并保留已授予权益，不回填消费、重置原终局或移动本金。 */
class CoursePrivateSettlementReviewTransactionTest {
    CoursePrivateRenewalTransactionTest f;
    @BeforeEach void setup() throws Exception {f=new CoursePrivateRenewalTransactionTest();f.setup();f.qualify();f.f.expire();f.renew();var access=mock(CoursePrivateOperationsAccess.class);org.springframework.test.util.ReflectionTestUtils.setField(f.f.service,"reviewAccess",access);refund();}
    @AfterEach void clear(){f.clear();}
    void refund(){Long id=f.f.f.f.jdbc.queryForObject("SELECT MIN(id) FROM course_order",Long.class);var row=f.f.f.orderRows.selectById(id);f.f.f.f.tx.executeWithoutResult(tx->{f.f.f.f.service.refund(row,8L);f.f.f.orderRows.markRefunded(id);});f.f.service.evaluate(f.sourceId);}
    com.course.platform.domain.entity.CoursePrivateTermSettlement latest(){return f.f.f.settlements.selectLatestByTerm(f.sourceId);}
    CoursePrivateSettlementService.ReviewResult close(String key){return f.f.service.resolveReview(f.sourceId,f.source().getVersion(),latest().getId(),8L,"确认退款净事实，保留已执行续期","原全额退款流水及原履约回执已核对",key);}
    @Test void closeKeepsCurrentMoneyOriginalFinalizationAndGrantedNext(){var next=f.f.f.terms.selectByRenewalSource(f.sourceId);var finalAt=f.source().getFinalizedAt();var amount=f.f.f.wallet().getBalance();var before=latest().getId();var response=close("close");assertEquals("NOT_QUALIFIED",response.result());assertEquals("MANUAL",latest().getEvaluationType());assertNotEquals(before,latest().getId());assertFalse(f.source().getReviewRequired());assertEquals(finalAt,f.source().getFinalizedAt());assertEquals(amount,f.f.f.wallet().getBalance());assertEquals("ACTIVE",f.f.f.terms.selectById(next.getId()).getStatus());f.f.service.evaluate(f.sourceId);assertFalse(f.source().getReviewRequired());f.f.buy("still_granted");}
    @Test void originalUnknownResponseCanRetryAfterReviewFlagCleared(){int version=f.source().getVersion();Long settlement=latest().getId();var original=close("retry");var repeated=f.f.service.resolveReview(f.sourceId,version,settlement,8L,"确认退款净事实，保留已执行续期","原全额退款流水及原履约回执已核对","retry");assertEquals(original,repeated);assertThrows(BusinessException.class,()->f.f.service.resolveReview(f.sourceId,version,settlement,8L,"改了原因","原全额退款流水及原履约回执已核对","retry"));}
    @Test void staleSnapshotOrMissingEvidenceDoesNotClearReview(){assertThrows(BusinessException.class,()->f.f.service.resolveReview(f.sourceId,f.source().getVersion()-1,latest().getId(),8L,"核对退款","证据","stale"));assertThrows(BusinessException.class,()->f.f.service.resolveReview(f.sourceId,f.source().getVersion(),latest().getId(),8L,"核对退款","","no_evidence"));assertTrue(f.source().getReviewRequired());}
    @Test void auditFailureRollsBackManualSettlementAndFlag(){var original=latest().getId();f.f.f.f.jdbc.execute("ALTER TABLE course_private_term_event ADD CONSTRAINT reject_review_close CHECK(event_type<>'SETTLEMENT_REVIEW_RESOLVED')");assertThrows(RuntimeException.class,()->close("failure"));assertTrue(f.source().getReviewRequired());assertEquals(original,latest().getId());}
    @Test void currentDatabasePermissionRevocationAndActorMismatchAreRejected(){var authorities=mock(com.course.platform.infra.persistence.mapper.UserAuthorityMapper.class);when(authorities.findAuthoritiesByUserId(8L)).thenReturn(List.of("course-private-term:adjust"));org.springframework.test.util.ReflectionTestUtils.setField(f.f.service,"reviewAccess",new CoursePrivateOperationsAccess(f.f.f.f.users,authorities));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(8L,null,List.of(new SimpleGrantedAuthority("course-private-term:adjust"),new SimpleGrantedAuthority("course-private-settlement:execute"))));assertThrows(BusinessException.class,()->close("revoked"));assertTrue(f.source().getReviewRequired());when(authorities.findAuthoritiesByUserId(8L)).thenReturn(List.of("course-private-term:adjust","course-private-settlement:execute"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,null,List.of(new SimpleGrantedAuthority("course-private-term:adjust"),new SimpleGrantedAuthority("course-private-settlement:execute"))));assertThrows(BusinessException.class,()->close("forged_actor"));assertTrue(f.source().getReviewRequired());}
    @Test void actualAnomaliesCannotBeSilentlyAcknowledged(){f.f.f.f.jdbc.update("UPDATE course_order SET amount=8 WHERE id=(SELECT MAX(id) FROM course_order)");f.f.service.evaluate(f.sourceId);assertThrows(BusinessException.class,()->close("anomaly"));assertTrue(f.source().getReviewRequired());}
}
