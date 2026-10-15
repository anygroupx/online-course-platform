package com.course.platform.service.impl;

import com.course.platform.domain.dto.CoursePrivatePlanRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/** 运营规则必须是白名单配置，预存与有效消费不能混为一个门槛。 */
class CoursePrivatePlanRulePolicyTest {
    @Test void manualGrantMustReferToFinitePeriodWithRenewalPolicy(){var value=rules("TIME_PREPAID");value.setAllowManualGrant(true);assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));value.setRenewalPolicy("MANUAL");assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));value.setMode("PERMANENT_PREPAID");value.setValidityDays(null);value.setRenewalPolicy("NONE");assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));}
    CoursePrivatePlanRules rules(String mode){
        var value=new CoursePrivatePlanRules();value.setMode(mode);value.setFixedPrice(new BigDecimal("8.50"));
        value.setInitialDepositAmount(new BigDecimal("500"));
        if(!"PERMANENT_PREPAID".equals(mode))value.setValidityDays(30);
        return value;
    }
    @Test void permanentHasNoArtificialExpirationAndAcceptsZeroInitialThreshold(){
        var value=rules("PERMANENT_PREPAID");value.setInitialDepositAmount(BigDecimal.ZERO);
        assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));
        value.setValidityDays(30);assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));
    }
    @ParameterizedTest @ValueSource(ints={30,90}) void finitePeriodsKeepContinuousDuration(int days){
        var value=rules("TIME_PREPAID");value.setValidityDays(days);CoursePrivatePlanRulePolicy.validate(value);
        var time=new CoursePrivateOperationsTime(Clock.fixed(Instant.parse("2026-10-14T04:20:00Z"),ZoneId.of("Asia/Shanghai")));
        assertEquals(LocalDateTime.parse("2026-10-14T12:20:00").plusDays(days),time.endAt(time.now(),days));
    }
    @Test void performanceRequiresPositiveConsumptionAndDoesNotTreatDepositAsConsumption(){
        var value=rules("PERFORMANCE_RENEWAL");value.setRenewalPolicy("PERFORMANCE");
        assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));
        value.setRenewalConsumptionTarget(new BigDecimal("3000"));assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));
        value.setRenewalDepositAmount(new BigDecimal("500"));assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));
    }
    @Test void tieredRulesComposeFinitePeriodAndExplicitRenewalPolicy(){
        var value=rules("TIERED_PRICING");value.setRenewalPolicy("NEW_DEPOSIT");value.setRenewalDepositAmount(new BigDecimal("600"));
        assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));value.setValidityDays(null);
        assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));
    }
    @Test void tieredDimensionAlsoComposesPermanentPrepaidWithoutArtificialPeriod(){
        var value=rules("TIERED_PRICING");value.setValidityDays(null);value.setRenewalPolicy("NONE");
        assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));
    }
    @Test void automaticUpgradeCannotBypassRequiredImmediateReview(){
        var value=rules("TIERED_PRICING");value.setUpgradeAuto(true);value.setUpgradePolicy("REVIEW_IMMEDIATE");
        assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));
        value.setUpgradePolicy("NEXT_TERM");assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));
    }
    @ParameterizedTest @ValueSource(strings={"LIMITED_PRICE_QUOTA","PERFORMANCE_REBATE","PAID_MEMBERSHIP","LEGACY_PERMANENT","expression(price*0.9)"})
    void unsupportedModesCannotBePublished(String mode){assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(rules(mode)));}
    @ParameterizedTest @ValueSource(strings={"0","-1","8.501","10000000000.00"})
    void priceAmountDomainCannotBeBypassed(String amount){var value=rules("TIME_PREPAID");value.setFixedPrice(new BigDecimal(amount));assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));}
    @Test void fundingRenewalRequiresRealPositiveNewDepositTarget(){var value=rules("TIME_PREPAID");value.setRenewalPolicy("NEW_DEPOSIT");assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));value.setRenewalDepositAmount(new BigDecimal("100"));assertDoesNotThrow(()->CoursePrivatePlanRulePolicy.validate(value));}
    @Test void unknownPoliciesAndReversedSaleBoundsFailClosed(){var value=rules("TIME_PREPAID");value.setRenewalPricePolicy("arbitrary-script");assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));value.setRenewalPricePolicy("SAME_VERSION");value.setEffectiveFrom(LocalDateTime.parse("2026-11-01T00:00:00"));value.setEffectiveUntil(value.getEffectiveFrom());assertThrows(RuntimeException.class,()->CoursePrivatePlanRulePolicy.validate(value));}
    @Test void delayedActivationUsesLaterMomentAndObservationDoesNotChangePurchaseEnd(){
        var time=new CoursePrivateOperationsTime(Clock.fixed(Instant.parse("2026-10-14T04:20:00.999Z"),ZoneId.of("Asia/Shanghai")));
        var later=LocalDateTime.parse("2026-10-16T10:00:00");assertEquals(later,time.startAt(time.now(),later));
        var end=time.endAt(later,30);assertTrue(time.inWindow(end.minusSeconds(1),later,end));assertFalse(time.inWindow(end,later,end));
        assertEquals(end.plusDays(7),time.settlementDeadline(end,7));assertFalse(time.inWindow(end.plusDays(1),later,end));
    }
    @Test void durationsRemainExactAcrossDifferentServerTimeZones(){
        var time=new CoursePrivateOperationsTime(Clock.fixed(Instant.parse("2026-03-07T17:00:00Z"),ZoneId.of("America/New_York")));
        var start=time.now();var end=time.endAt(start,1);
        assertEquals(Duration.ofDays(1),Duration.between(start.atZone(time.zone()).toInstant(),end.atZone(time.zone()).toInstant()));
    }
}
