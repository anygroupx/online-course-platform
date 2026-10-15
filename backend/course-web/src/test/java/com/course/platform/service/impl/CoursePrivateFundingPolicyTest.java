package com.course.platform.service.impl;

import com.course.platform.domain.dto.CoursePrivateFundingFact;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 真实划转事实的资格口径：水位、资金认领及下降后重新达标的准确时刻。 */
class CoursePrivateFundingPolicyTest {
    LocalDateTime start=LocalDateTime.parse("2026-10-14T10:00:00");
    CoursePrivateFundingPolicy.Window window(Long watermark,LocalDateTime end){return new CoursePrivateFundingPolicy.Window(5L,7L,10L,watermark,null,start,end,start.plusDays(30));}
    CoursePrivateFundingFact fact(long id,String type,String amount,int seconds,Long term){var fact=new CoursePrivateFundingFact();fact.setLedgerId(id);fact.setWalletId(5L);fact.setUserId(7L);fact.setTransferType(type);fact.setAmount(new BigDecimal(amount));fact.setCompletedAt(start.plusSeconds(seconds));fact.setAllocatedTermId(term);return fact;}
    @Test void sameSecondHistoricalFundsAreExcludedByLedgerWatermark(){var result=CoursePrivateFundingPolicy.assess(window(2L,null),List.of(fact(2,"TRANSFER_IN","100",0,null),fact(3,"TRANSFER_IN","40",0,null)),new BigDecimal("100"));assertFalse(result.qualified());assertEquals(new BigDecimal("40.00"),result.netDepositAmount());}
    @Test void successfulNewDepositsAccumulateAndSubtractApprovedOut(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(fact(1,"TRANSFER_IN","80",1,null),fact(2,"TRANSFER_OUT","20",2,null),fact(3,"TRANSFER_IN","40",3,null)),new BigDecimal("100"));assertTrue(result.qualified());assertEquals(start.plusSeconds(3),result.qualifiedAt());assertEquals(List.of(1L,3L),result.unallocatedLedgerIds());}
    @Test void fundsAllocatedToAnotherTermCannotQualifyAgain(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(fact(1,"TRANSFER_IN","100",1,9L),fact(2,"TRANSFER_IN","30",2,null)),new BigDecimal("100"));assertFalse(result.qualified());assertEquals(new BigDecimal("30.00"),result.netDepositAmount());}
    @Test void ownAllocationRemainsQualificationEvidenceWithoutBeingClaimedTwice(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(fact(1,"TRANSFER_IN","100",1,10L)),new BigDecimal("100"));assertTrue(result.qualified());assertEquals(List.of(),result.unallocatedLedgerIds());}
    @Test void droppingBelowTargetClearsEarlierQualificationMoment(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(fact(1,"TRANSFER_IN","100",1,null),fact(2,"TRANSFER_OUT","50",2,null),fact(3,"TRANSFER_IN","50",4,null)),new BigDecimal("100"));assertTrue(result.qualified());assertEquals(start.plusSeconds(4),result.qualifiedAt());}
    @Test void netNegativeIsKeptAsFactAndNeverPretendsToMeetThreshold(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(fact(1,"TRANSFER_OUT","20",1,null)),new BigDecimal("100"));assertFalse(result.qualified());assertEquals(new BigDecimal("-20.00"),result.netDepositAmount());assertNull(result.qualifiedAt());}
    @Test void periodEndIsExclusiveAndLaterDepositsCannotBackfillR(){var result=CoursePrivateFundingPolicy.assess(window(0L,start.plusSeconds(2)),List.of(fact(1,"TRANSFER_IN","50",1,null),fact(2,"TRANSFER_IN","50",2,null)),new BigDecimal("100"));assertFalse(result.qualified());assertEquals(new BigDecimal("50.00"),result.netDepositAmount());}
    @Test void futureFactsCannotActivateToday(){var result=CoursePrivateFundingPolicy.assess(new CoursePrivateFundingPolicy.Window(5L,7L,10L,0L,null,start,null,start),List.of(fact(1,"TRANSFER_IN","100",1,null)),new BigDecimal("100"));assertFalse(result.qualified());}
    @Test void duplicateLedgerOrForeignBindingFailsClosed(){var duplicate=fact(1,"TRANSFER_IN","100",1,null);assertThrows(RuntimeException.class,()->CoursePrivateFundingPolicy.assess(window(0L,null),List.of(duplicate,duplicate),new BigDecimal("100")));var foreign=fact(2,"TRANSFER_IN","100",2,null);foreign.setUserId(8L);assertThrows(RuntimeException.class,()->CoursePrivateFundingPolicy.assess(window(0L,null),List.of(foreign),new BigDecimal("100")));}
    @Test void qualificationTimestampComesFromFundingFactsRatherThanWorkerNow(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(fact(1,"TRANSFER_IN","100",1,null)),new BigDecimal("100"));assertEquals(start.plusSeconds(1),result.qualifiedAt());}
    @Test void zeroThresholdQualifiesAtOpeningWithoutInventedTransfer(){var result=CoursePrivateFundingPolicy.assess(window(0L,null),List.of(),BigDecimal.ZERO);assertTrue(result.qualified());assertEquals(start,result.qualifiedAt());assertEquals(List.of(),result.unallocatedLedgerIds());}
}
