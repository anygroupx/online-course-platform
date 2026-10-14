package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 分析读取实际原扣款与可信履约，退款立即改变成交但不会撤销后续权益。 */
class CoursePrivateAnalyticsTransactionTest {
    CoursePrivateRenewalTransactionTest f;
    CoursePrivateAnalyticsService service;
    CoursePrivateCostVersionMapper costs;
    @BeforeEach void setup() throws Exception {f=new CoursePrivateRenewalTransactionTest();f.setup();var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(CoursePrivateAnalyticsMapper.class);config.addMapper(CourseWalletReleaseRequestMapper.class);var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();factory.setDataSource(f.f.f.f.jdbc.getDataSource());factory.setConfiguration(config);var mapper=new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(CoursePrivateAnalyticsMapper.class);f.f.f.table(CourseWalletReleaseRequest.class);costs=mock(CoursePrivateCostVersionMapper.class);service=new CoursePrivateAnalyticsService(mapper,f.f.f.termOrders,f.f.f.f.wallets,f.f.f.versions,costs,mock(CoursePrivateOperationsAccess.class),f.f.f.json,new CoursePrivateOperationsTime(f.f.f.clock));}
    @AfterEach void clear(){f.clear();}
    Map<String,Object> read(){return service.summary(8L,9L,null);}
    @Test void prepaidCashIsLiabilityAndNeverRevenue(){var result=read();assertEquals("100.00",result.get("walletReserve"));assertEquals("0.00",result.get("effectiveRevenue"));assertNull(result.get("estimatedContributionMargin"));assertNull(result.get("actualProfit"));}
    @Test void onlyTrustedCompletionCountsAndRefundIsObservedWithoutWorker(){Long id=f.f.buy("analysis");assertEquals("0.00",read().get("effectiveRevenue"));f.f.complete(id);var result=read();assertEquals("7.20",result.get("effectiveRevenue"));assertEquals("92.80",result.get("walletReserve"));var order=f.f.f.orderRows.selectById(id);f.f.f.f.tx.executeWithoutResult(tx->{f.f.f.f.service.refund(order,8L);f.f.f.orderRows.markRefunded(id);});assertEquals("0.00",read().get("effectiveRevenue"));assertEquals("100.00",read().get("walletReserve"));}
    @Test void onlyFrozenVerifiedCostSupportsEstimateAndNeverActualProfit(){f.qualify();f.f.f.f.jdbc.update("UPDATE course_private_plan_version SET cost_version_id=3 WHERE id=1");var cost=new CoursePrivateCostVersion();cost.setId(3L);cost.setPlatformId(9L);cost.setUnitCost(new BigDecimal("2"));cost.setVerifiedBy(8L);cost.setVerifiedAt(f.source().getStartAt().minusDays(1));cost.setEvidence("实际供应商账单");when(costs.selectById(3L)).thenReturn(cost);var result=read();assertEquals("4.00",result.get("verifiedCostEstimate"));assertEquals("10.40",result.get("estimatedContributionMargin"));assertEquals(2L,result.get("costCoveredOrderCount"));assertNull(result.get("actualProfit"));}
    @Test void liveCountsAndPlanFiltersDoNotMultiplyWalletAfterRenewal(){f.qualify();f.f.expire();f.renew();var result=read();assertEquals(2L,result.get("activatedPeriodCount"));assertEquals(1L,result.get("enrolledAgentCount"));assertEquals("85.60",result.get("walletReserve"));assertEquals(1L,result.get("automaticRenewalCount"));assertEquals("0.00",service.summary(8L,9L,999L).get("walletReserve"));}
    @Test void cashAgeUsesAuditedFifoAndUnknownRefundAgeStaysUnknown(){f.f.f.clock.advance(Duration.ofDays(2));assertEquals("2.00",read().get("averageCashAgeDays"));Long id=f.f.buy("age");f.f.complete(id);var order=f.f.f.orderRows.selectById(id);f.f.f.f.tx.executeWithoutResult(tx->f.f.f.f.service.refund(order,8L));assertNull(read().get("averageCashAgeDays"));}
    @Test void legacyConversionRateIsUnknownRatherThanFakeZero(){f.f.f.f.jdbc.update("UPDATE course_private_contract_term SET operations_mode='LEGACY_PERMANENT' WHERE id=?",f.sourceId);assertNull(read().get("firstOrderConversionRate"));assertEquals(1L,read().get("legacyPeriodCount"));}
    @Test void activationAgentsAreDeduplicatedAcrossPeriodsAndLegacySnapshots(){f.qualify();f.f.expire();f.renew();assertEquals(1L,read().get("activationAgent30Days"));f.f.f.f.jdbc.update("UPDATE course_private_contract_term SET operations_mode='LEGACY_PERMANENT'");assertEquals(0L,read().get("activatedPeriodCount"));assertEquals(1L,read().get("legacyActivatedContractCount"));assertEquals(1L,read().get("activationAgent30Days"));}
    @Test void ledgerMismatchIsReportedWithoutRepairingMoney(){f.f.f.f.jdbc.update("UPDATE course_reserved_wallet SET balance=120 WHERE contract_id=?",f.source().getContractId());var result=read();assertEquals(1L,result.get("walletLedgerMismatchCount"));assertEquals("120.00",result.get("walletReserve"));f.f.f.f.money("120",f.f.f.wallet().getBalance());}
    @Test void legitimateRefundIsNotMisreportedAsLedgerMismatch(){Long id=f.f.buy("refund_audit");var order=f.f.f.orderRows.selectById(id);f.f.f.f.tx.executeWithoutResult(tx->f.f.f.f.service.refund(order,8L));assertEquals(0L,read().get("walletLedgerMismatchCount"));assertNull(read().get("averageCashAgeDays"));}
    @Test void invalidFiltersFailBeforeQueries(){assertThrows(BusinessException.class,()->service.summary(8L,-1L,null));assertThrows(BusinessException.class,()->service.summary(8L,null,0L));}
}
