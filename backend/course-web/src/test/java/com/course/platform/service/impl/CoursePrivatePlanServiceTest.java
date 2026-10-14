package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 发布必须使用真实成本和独立审批，不得把普通售价当成本。 */
class CoursePrivatePlanServiceTest {
    CoursePrivatePlanMapper plans=mock(CoursePrivatePlanMapper.class);
    CoursePrivatePlanVersionMapper versions=mock(CoursePrivatePlanVersionMapper.class);
    CoursePrivateCostVersionMapper costs=mock(CoursePrivateCostVersionMapper.class);
    CoursePrivateRiskApprovalMapper risks=mock(CoursePrivateRiskApprovalMapper.class);
    CoursePlatformMapper platforms=mock(CoursePlatformMapper.class);
    PlatformCategoryMapper categories=mock(PlatformCategoryMapper.class);
    CoursePrivateOperationsAccess access=mock(CoursePrivateOperationsAccess.class);
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    CoursePrivateOperationsTime time=new CoursePrivateOperationsTime(Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneId.of("Asia/Shanghai")));
    CoursePrivatePlanService service;
    CoursePrivatePlan plan;
    CoursePrivateCostVersion cost;
    @BeforeEach void setup() throws Exception {
        service=new CoursePrivatePlanService(plans,versions,costs,risks,platforms,categories,access,json,time);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",true);
        var request=new CoursePrivatePlanRequest();request.setPlatformId(9L);request.setPlanCode("A");request.setPlanName("长期预存");request.setReason("配置");
        var rules=new CoursePrivatePlanRules();rules.setMode("PERMANENT_PREPAID");rules.setFixedPrice(new BigDecimal("7.20"));request.setRules(rules);
        plan=new CoursePrivatePlan();plan.setId(3L);plan.setPlatformId(9L);plan.setDraftVersion(1);plan.setUpdatedBy(1L);plan.setDraftConfigJson(json.writeValueAsString(request));
        when(plans.selectById(3L)).thenReturn(plan);when(plans.selectByIdForUpdate(3L)).thenReturn(plan);when(plans.updateById(any(CoursePrivatePlan.class))).thenReturn(1);
        var platform=new CoursePlatform();platform.setId(9L);platform.setStatus(1);when(platforms.selectById(9L)).thenReturn(platform);when(platforms.selectByIdForUpdate(9L)).thenReturn(platform);
        cost=new CoursePrivateCostVersion();cost.setId(4L);cost.setUnitCost(new BigDecimal("5.00"));cost.setVerifiedBy(2L);
        when(versions.insert(any(CoursePrivatePlanVersion.class))).thenAnswer(i->{i.<CoursePrivatePlanVersion>getArgument(0).setId(5L);return 1;});
    }
    @Test void agentOrdinaryReferenceUsesActualUnifiedRateWithoutExposingWallet(){var users=mock(UserMapper.class);var user=new User();user.setId(7L);user.setStatus(1);user.setUid("00000000-0000-4000-8000-000000000007");user.setRate(new BigDecimal("1.50"));when(users.selectOne(any())).thenReturn(user);var product=platforms.selectById(9L);product.setBasePrice(new BigDecimal("10"));product.setRateType("MULTIPLY");var pricing=new CoursePricingService(users,platforms,categories,mock(CoursePrivateContractMapper.class),mock(CourseReservedWalletMapper.class),Clock.systemUTC());org.springframework.test.util.ReflectionTestUtils.setField(service,"referenceUsers",users);org.springframework.test.util.ReflectionTestUtils.setField(service,"referencePricing",pricing);var result=service.ordinaryReference(3L,1L,user.getUid());assertEquals("15.00",result.get("ordinaryPrice"));assertEquals("7.20",result.get("fixedPrice"));assertFalse(result.containsKey("userId"));assertFalse(result.containsKey("balance"));assertThrows(BusinessException.class,()->service.ordinaryReference(3L,1L,"7"));}
    @Test void impactOnlyReportsAggregateScope(){when(plans.countRelatedContracts(3L)).thenReturn(4L);when(plans.countRelatedPeriods(3L)).thenReturn(8L);when(plans.countActiveContracts(eq(3L),any())).thenReturn(2L);var impact=(java.util.Map<?,?>)service.detail(3L,1L).get("impact");assertEquals(4L,impact.get("relatedContracts"));assertEquals(8L,impact.get("relatedPeriods"));assertEquals(2L,impact.get("activeContracts"));}
    @Test void modernDraftCannotOccupyReservedLegacyCode() throws Exception {var request=json.readValue(plan.getDraftConfigJson(),CoursePrivatePlanRequest.class);request.setPlanCode("__LEGACY_PERMANENT");assertThrows(BusinessException.class,()->service.save(null,request,1L));verify(plans,never()).insert(any(CoursePrivatePlan.class));}
    @Test void unknownCostBlocksPublicationWithoutInventingProfit() {
        assertThrows(BusinessException.class,()->service.publish(3L,1,1L,"发布"));verify(versions,never()).insert(any(CoursePrivatePlanVersion.class));
    }
    @Test void verifiedPositiveMarginPublishesImmutableRules() {
        when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);
        var result=service.publish(3L,1,1L,"发布");assertEquals(new BigDecimal("7.20"),result.getFixedPrice());assertEquals(4L,result.getCostVersionId());assertTrue(result.getConfigSnapshot().contains("长期预存"));assertEquals("PUBLISHED",plan.getStatus());
    }
    @Test void staleDraftBlocksPublication() { assertThrows(BusinessException.class,()->service.publish(3L,0,1L,"发布"));verifyNoInteractions(costs,versions); }
    @Test void lowMarginRequiresIndependentApproval() {
        cost.setUnitCost(new BigDecimal("7.20"));when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);
        assertThrows(BusinessException.class,()->service.publish(3L,1,1L,"发布"));verify(versions,never()).insert(any(CoursePrivatePlanVersion.class));
    }
    @Test void draftEditorCannotApproveOwnRisk() {
        when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);
        assertThrows(BusinessException.class,()->service.approveRisk(3L,1,1L,"风险说明"));verify(risks,never()).insert(any(CoursePrivateRiskApproval.class));
    }
    @Test void approvalForSameDraftCostAllowsPublication() {
        cost.setUnitCost(new BigDecimal("8.00"));when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);
        var approval=new CoursePrivateRiskApproval();approval.setId(6L);approval.setReviewedBy(2L);when(risks.selectApproved(3L,1,4L)).thenReturn(approval);
        assertEquals(6L,service.publish(3L,1,1L,"已审批").getRiskApprovalId());
    }
    @Test void ownRiskCannotBeReusedForPublication() {
        cost.setUnitCost(new BigDecimal("8.00"));when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);
        var approval=new CoursePrivateRiskApproval();approval.setReviewedBy(1L);when(risks.selectApproved(3L,1,4L)).thenReturn(approval);
        assertThrows(BusinessException.class,()->service.publish(3L,1,1L,"发布"));
    }
    @Test void disabledFeatureBlocksNewPublication() { org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",false);assertThrows(BusinessException.class,()->service.publish(3L,1,1L,"发布"));verifyNoInteractions(plans); }
    @Test void changedCostVersionCannotPublishFromStaleAssessment(){when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);assertThrows(BusinessException.class,()->service.publish(3L,1,99L,null,1L,"发布"));verify(versions,never()).insert(any(CoursePrivatePlanVersion.class));}
    @Test void changedCostInvalidatesPriorRiskEvenWithSamePrice(){cost.setUnitCost(new BigDecimal("8"));cost.setId(9L);when(costs.selectLatestVerified(eq(9L),any())).thenReturn(cost);var old=new CoursePrivateRiskApproval();old.setReviewedBy(2L);when(risks.selectApproved(3L,1,4L)).thenReturn(old);assertThrows(BusinessException.class,()->service.publish(3L,1,1L,"发布"));verify(versions,never()).insert(any(CoursePrivatePlanVersion.class));}
    @Test void disabledCategoryCannotPublishFreshRights(){platforms.selectById(9L).setCategoryId(8L);var category=new PlatformCategory();category.setStatus(0);when(categories.selectById(8L)).thenReturn(category);assertThrows(BusinessException.class,()->service.publish(3L,1,1L,"发布"));verify(versions,never()).insert(any(CoursePrivatePlanVersion.class));}
    @Test void migratedLegacyPlanHasReadableDetailWithoutInventingModernRules(){plan.setMode("LEGACY_PERMANENT");plan.setDraftConfigJson(null);var result=assertDoesNotThrow(()->service.detail(3L,1L));assertEquals(true,result.get("readOnly"));assertNull(result.get("draftRules"));verifyNoInteractions(costs);}
    @Test void legacyCompatibilityPlanCannotBeConvertedByDraftUpdate(){plan.setMode("LEGACY_PERMANENT");plan.setPlanCode("__LEGACY_PERMANENT");var command=service.decode(plan.getDraftConfigJson());command.setPlanCode("__LEGACY_PERMANENT");command.setExpectedDraftVersion(1);assertThrows(BusinessException.class,()->service.save(3L,command,1L));verify(plans,never()).updateDraft(any(),any(),any());}
}
