package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 旧入口兼容投影与原修改同事务，追加版本而不重写旧成交依据，不修改任何本金。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateLegacySyncService {
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivatePlanMapper plans;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateTermEventMapper events;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @Value("${course.private-operations.legacy-sync-enabled:false}") private boolean enabled;

    @Transactional(rollbackFor=Exception.class)
    public void sync(Long contractId,Long operator,String reason){
        if(!enabled)return;var initial=contracts.selectById(contractId);if(initial==null)throw conflict("兼容协议不存在");if(users.selectByIdForUpdate(initial.getUserId())==null)throw conflict("兼容拥有者不存在");var contract=contracts.selectByIdForUpdate(contractId);if(contract==null||contract.getOperationsMode()!=null&&!"LEGACY_PERMANENT".equals(contract.getOperationsMode()))throw conflict("现代协议不能同步为旧权益");
        var bound=wallets.selectByBinding(contract.getUserId(),contract.getPlatformId());if(bound==null)throw conflict("原专户不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,contract.getUserId(),contract.getPlatformId());
        var previous=contract.getCurrentTermId()==null?null:terms.selectByIdForUpdate(contract.getCurrentTermId());
        if(previous!=null){if(!Objects.equals(previous.getContractId(),contractId)||!Objects.equals(previous.getUserId(),contract.getUserId())||!Objects.equals(previous.getPlatformId(),contract.getPlatformId())||!"LEGACY_PERMANENT".equals(previous.getOperationsMode()))throw conflict("旧权益投影绑定异常");if(same(previous,contract))return;}
        String beforeSnapshot=previous==null?null:encode(previous);
        // 先使用唯一代码upsert取得记录锁，再当前读；空键FOR UPDATE的间隙锁会使并发首建互锁。
        plans.ensureLegacy(contract.getPlatformId(),operator,time.now());var plan=plans.selectByCodeForUpdate(contract.getPlatformId(),"__LEGACY_PERMANENT");if(plan==null)throw conflict("隐藏兼容套餐未保存");plan=plans.selectByIdForUpdate(plan.getId());if(!"LEGACY_PERMANENT".equals(plan.getMode())||Boolean.TRUE.equals(plan.getVisibleToAgents()))throw conflict("隐藏兼容套餐身份异常");
        var latest=versions.latestVersionNoForUpdate(plan.getId());int versionNo=latest==null?1:latest+1;String snapshot=encode(contract);
        var version=new CoursePrivatePlanVersion();version.setPlanId(plan.getId());version.setPlatformId(contract.getPlatformId());version.setVersionNo(versionNo);version.setFixedPrice(contract.getFixedPrice());version.setInitialDepositAmount(contract.getMinInitialDeposit());version.setInitialFundingPolicy("EXISTING_BALANCE");version.setRenewalPolicy("NONE");version.setRenewalDepositAmount(java.math.BigDecimal.ZERO);version.setRenewalConsumptionTarget(java.math.BigDecimal.ZERO);version.setSettlementGraceDays(0);version.setRenewalAuto(false);version.setRenewalPricePolicy("SAME_VERSION");version.setRenewalBeforeDays(0);version.setRenewalAfterDays(0);version.setUpgradePolicy("DISABLED");version.setUpgradeDepositPolicy("DIFFERENCE");version.setAllowGeneralCheckout(false);version.setPublishedAt(time.now());version.setCreatedAt(time.now());version.setCreatedBy(operator);version.setConfigSnapshot(snapshot);if(versions.insert(version)!=1)throw conflict("兼容版本未保存");
        var term=new CoursePrivateContractTerm();term.setContractId(contractId);term.setUserId(contract.getUserId());term.setPlatformId(contract.getPlatformId());term.setPlanId(plan.getId());term.setVersionId(version.getId());term.setTermNo(Objects.requireNonNullElse(terms.latestTermNoForUpdate(contractId),0)+1);term.setRelationGeneration(Objects.requireNonNullElse(contract.getRelationGeneration(),1));term.setOperationsMode("LEGACY_PERMANENT");term.setEffectiveFixedPrice(contract.getFixedPrice());term.setEffectiveInitialDeposit(contract.getMinInitialDeposit());term.setRenewalTarget(java.math.BigDecimal.ZERO);term.setStatus(contract.getStatus());term.setQualificationStatus(contract.getActivatedAt()==null?"PENDING":"QUALIFIED");term.setQualificationKind("LEGACY_UNKNOWN");term.setStartAt(contract.getValidFrom());term.setEndAt(contract.getValidUntil());term.setSettlementDeadlineAt(contract.getValidUntil());term.setActivatedAt(contract.getActivatedAt());term.setQualifiedAt(contract.getActivatedAt());term.setReviewRequired(false);term.setConfigSnapshot(snapshot);term.setVersion(0);term.setCreatedAt(time.now());term.setUpdatedAt(time.now());if(terms.insert(term)!=1)throw conflict("兼容周期未保存");
        if(previous!=null){Integer expected=previous.getVersion();previous.setStatus(previous.getActivatedAt()==null?"CANCELLED":"SUPERSEDED");previous.setSupersededAt(time.now());if(terms.updateEvaluation(previous,expected,time.now())!=1)throw conflict("兼容原周期版本冲突");}
        // 原旧接口已经递增一次配置版本；投影指针CAS不会额外改变响应版本或旧审计时间。
        if(contracts.syncLegacyPointer(contractId,contract.getVersion(),term.getId())!=1)throw conflict("旧协议投影指针冲突");
        var event=new CoursePrivateTermEvent();event.setContractId(contractId);event.setTermId(term.getId());event.setSourceTermId(previous==null?null:previous.getId());event.setEventType("LEGACY_CONFIG_SYNCED");event.setBeforeJson(beforeSnapshot);event.setAfterJson(snapshot);event.setOperatorId(operator);event.setReason(reason);event.setBizNo("legacy_sync_"+contractId+"_"+contract.getVersion());event.setHappenedAt(time.now());if(events.insert(event)!=1)throw conflict("兼容同步审计失败");
    }
    private boolean same(CoursePrivateContractTerm term,CoursePrivateContract contract){return term.getEffectiveFixedPrice()!=null&&term.getEffectiveFixedPrice().compareTo(contract.getFixedPrice())==0&&term.getEffectiveInitialDeposit()!=null&&term.getEffectiveInitialDeposit().compareTo(contract.getMinInitialDeposit())==0&&Objects.equals(term.getStatus(),contract.getStatus())&&Objects.equals(term.getActivatedAt(),contract.getActivatedAt())&&Objects.equals(term.getStartAt(),contract.getValidFrom())&&Objects.equals(term.getEndAt(),contract.getValidUntil());}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception invalid){throw conflict("兼容证据序列化失败");}}
    private BusinessException conflict(String message){return new BusinessException(ResultCode.CONFLICT,message);}
}
