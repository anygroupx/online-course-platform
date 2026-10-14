package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** 续期只延续原资金关系；同一来源周期只有一个下一期，排程资格在真正生效时重新核验。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateRenewalService {
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateApplicationMapper applications;
    private final CoursePrivateFundingAllocationMapper funding;
    private final CoursePrivateTermEventMapper events;
    private final CoursePrivateSettlementService settlement;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @Value("${course.private-operations.enabled:false}") private boolean enabled;
    @Value("${course.private-operations.auto-renewal-enabled:false}") private boolean automaticEnabled;

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm renewAutomatic(Long sourceId) {
        if(!enabled||!automaticEnabled)return null;
        return renew(sourceId,null,0L,"按冻结的自动续期规则执行",true,false,false);
    }

    /** 已人工审批的排程不依赖自动续期开关；这里只激活已有排程，不能偷偷新建权益。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm activateScheduled(Long sourceId) {
        return renew(sourceId,null,0L,"期界到达，重新核验已经批准的续期排程",false,true,false);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm review(Long applicationId,Integer expectedVersion,Long operator,String reason) {
        return approve(applicationId,expectedVersion,operator,reason,false);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm grantReviewed(Long applicationId,Integer expectedVersion,Long operator,String reason) {
        access.require(operator,"course-private-term:adjust");
        access.require(operator,"course-private-settlement:execute");
        return approve(applicationId,expectedVersion,operator,reason,true);
    }

    private CoursePrivateContractTerm approve(Long applicationId,Integer expectedVersion,Long operator,String reason,boolean manualGrant) {
        access.require(operator,"course-private-contract:review");CoursePrivatePlanService.reason(reason);
        var initial=applications.selectById(applicationId);
        if(initial==null||!"RENEW".equals(initial.getApplicationType())||initial.getSourceTermId()==null)throw conflict("续期申请不存在");
        // 拥有者锁先于协议、钱包和申请；自动任务与人工审核在同一个账户锁下互斥。
        var user=users.selectByIdForUpdate(initial.getUserId());requireActive(user);
        var contract=contracts.selectByIdForUpdate(initial.getContractId());
        var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());
        if(bound==null)throw conflict("原续期专户不存在");
        CoursePricingService.requireBinding(contract,wallets.selectByIdForUpdate(bound.getId()),initial.getUserId(),initial.getPlatformId());
        var app=applications.selectByIdForUpdate(applicationId);
        if(app==null||!Objects.equals(app.getSourceTermId(),initial.getSourceTermId())||!Objects.equals(app.getUserId(),initial.getUserId())||!Objects.equals(app.getContractId(),contract.getId()))throw conflict("续期申请绑定已变化");
        if("APPROVED".equals(app.getStatus())) {
            var event=events.selectByBizNo("application_"+applicationId+"_approve");
            if(event==null||event.getTermId()==null)throw conflict("原续期审批证据缺失");
            var original=terms.selectById(event.getTermId());if(original==null)throw conflict("原续期结果缺失");return original;
        }
        if(!enabled||!"PENDING".equals(app.getStatus())||expectedVersion==null||!Objects.equals(expectedVersion,app.getVersion())||!"NEXT_TERM".equals(app.getEffectivePolicy()))throw conflict("续期申请状态、版本或功能开关已变化");
        var next=renew(app.getSourceTermId(),app.getVersionId(),operator,reason,false,false,manualGrant);
        if(next==null)throw conflict("续期资格尚未满足或尚未达到版本确定时间，申请保留待审核");
        if(applications.review(applicationId,expectedVersion,"APPROVED",operator,reason,contract.getId(),time.now())!=1)throw conflict("续期审批保存冲突");
        audit(next,"APPLICATION_APPROVED",operator,reason,"application_"+applicationId+"_approve",null);
        return next;
    }

    private CoursePrivateContractTerm renew(Long sourceId,Long requestedVersion,Long operator,String reason,boolean automatic,boolean existingOnly,boolean manualGrant) {
        var initial=terms.selectById(sourceId);if(initial==null)return null;
        requireActive(users.selectByIdForUpdate(initial.getUserId()));
        var contract=contracts.selectByIdForUpdate(initial.getContractId());
        var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw conflict("原续期专户不存在");
        var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());
        var source=terms.selectByIdForUpdate(sourceId);
        if(source==null||!Objects.equals(source.getContractId(),contract.getId())||!Objects.equals(source.getUserId(),contract.getUserId())||!Objects.equals(source.getPlatformId(),contract.getPlatformId()))throw conflict("原周期绑定异常");
        if(!Objects.equals(source.getRelationGeneration(),contract.getRelationGeneration())||Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus())||Set.of("CANCELLED","SUPERSEDED","SUSPENDED","TERMINATED").contains(source.getStatus())||source.getActivatedAt()==null||source.getEndAt()==null||"LEGACY_PERMANENT".equals(source.getOperationsMode()))return null;
        var config=config(source.getConfigSnapshot());var rules=config.getRules();CoursePrivatePlanRulePolicy.validate(rules);
        if(manualGrant&&!rules.isAllowManualGrant())throw conflict("来源周期冻结规则未允许人工补授");
        if(!Set.of("PERFORMANCE","NEW_DEPOSIT","MANUAL").contains(rules.getRenewalPolicy())||automatic&&!rules.isRenewalAuto())return null;
        var next=terms.selectByRenewalSource(sourceId);
        boolean grantedExisting=next!=null&&"MANUAL_GRANT".equals(next.getQualificationKind());
        if(grantedExisting){var proof=events.selectByBizNo("manual_renewal_"+sourceId);if(proof==null||!Objects.equals(proof.getTermId(),next.getId())||!Objects.equals(proof.getTargetVersionId(),next.getVersionId()))throw conflict("人工补授排程缺少原始不可变审批依据");}
        boolean waived=manualGrant||grantedExisting;
        if("MANUAL".equals(rules.getRenewalPolicy())&&!waived)return null;
        if(next!=null) {
            if(!Objects.equals(next.getContractId(),source.getContractId())||!Objects.equals(next.getRelationGeneration(),source.getRelationGeneration())||!grantedExisting&&!Objects.equals(next.getQualificationKind(),rules.getRenewalPolicy()))throw conflict("同一来源已有升级或其他下一期，不能重复授予续期");
            if(requestedVersion!=null&&!Objects.equals(requestedVersion,next.getVersionId()))throw conflict("已经冻结的下一期与申请版本不一致");
            if(Set.of("CANCELLED","TERMINATED","SUSPENDED").contains(next.getStatus()))throw conflict("原下一期已取消、终止或暂停，不能冒充成功续期");
            if(!"SCHEDULED".equals(next.getStatus()))return next;
        } else if(existingOnly||!Objects.equals(contract.getCurrentTermId(),sourceId))return null;
        var opens=time.endAt(source.getEndAt(),-rules.getRenewalBeforeDays());
        var closes=time.endAt(source.getEndAt(),rules.getRenewalAfterDays());
        if(source.getSettlementDeadlineAt()!=null&&source.getSettlementDeadlineAt().isAfter(closes))closes=source.getSettlementDeadlineAt();
        if(next==null&&(time.now().isBefore(opens)||time.now().isAfter(closes)))return null;

        // 消费可以提前达标，但只有原期终局后才能真正取得下一期购买权。
        settlement.evaluate(sourceId);source=terms.selectByIdForUpdate(sourceId);contract=contracts.selectByIdForUpdate(contract.getId());
        if(!waived&&(!"QUALIFIED".equals(source.getQualificationStatus())||Boolean.TRUE.equals(source.getReviewRequired())))return null;
        boolean atBoundary=!time.now().isBefore(source.getEndAt());
        if(atBoundary&&!waived&&source.getFinalizedAt()==null)return null;
        if(next==null&&"LATEST_PUBLISHED".equals(rules.getRenewalPricePolicy())&&!atBoundary)return null;
        var selection=next==null?selectVersion(source,config):new VersionSelection(versions.selectById(next.getVersionId()),null);
        var chosen=selection.version();
        if(chosen==null)throw conflict("续期冻结版本缺失");
        if(requestedVersion!=null&&!Objects.equals(requestedVersion,chosen.getId()))throw conflict("续期版本与代理确认的版本不一致，需重新确认");
        var chosenConfig=config(chosen.getConfigSnapshot());CoursePrivatePlanRulePolicy.validate(chosenConfig.getRules());
        if(!Objects.equals(chosen.getPlanId(),source.getPlanId())||!Objects.equals(chosen.getPlatformId(),source.getPlatformId())||chosen.getPublishedAt()==null||chosen.getPublishedAt().isAfter(time.now())||chosenConfig.getRules().getFixedPrice().compareTo(chosen.getFixedPrice())!=0||chosenConfig.getRules().getInitialDepositAmount().compareTo(chosen.getInitialDepositAmount())!=0)throw conflict("续期发布版本与冻结快照不一致");
        if(chosenConfig.getRules().getValidityDays()==null)throw conflict("周期续期不能隐式转换为永久权益");
        if(!waived)claimDeposit(source,wallet,rules);
        if(next==null) {
            next=new CoursePrivateContractTerm();org.springframework.beans.BeanUtils.copyProperties(source,next);
            next.setId(null);next.setTermNo(terms.selectByContract(contract.getId()).stream().mapToInt(CoursePrivateContractTerm::getTermNo).max().orElse(0)+1);
            next.setVersionId(chosen.getId());next.setConfigSnapshot(chosen.getConfigSnapshot());next.setEffectiveFixedPrice(chosen.getFixedPrice());next.setEffectiveInitialDeposit(chosen.getInitialDepositAmount());
            next.setOperationsMode(chosenConfig.getRules().getMode());next.setTierCode(chosenConfig.getTierCode());next.setTierRank(chosenConfig.getTierRank());
            next.setRenewalTarget("PERFORMANCE".equals(chosenConfig.getRules().getRenewalPolicy())?chosenConfig.getRules().getRenewalConsumptionTarget():chosenConfig.getRules().getRenewalDepositAmount());
            next.setRenewalSourceTermId(sourceId);next.setQualificationKind(waived?"MANUAL_GRANT":rules.getRenewalPolicy());next.setQualificationStatus("QUALIFIED");next.setStatus("SCHEDULED");next.setQualifiedAt(waived?time.now():source.getQualifiedAt());
            next.setStartAt(source.getEndAt());next.setEndAt(time.endAt(next.getStartAt(),chosenConfig.getRules().getValidityDays()));next.setSettlementDeadlineAt(time.settlementDeadline(next.getEndAt(),chosenConfig.getRules().getSettlementGraceDays()));
            next.setFundingWindowStart(next.getStartAt());next.setFundingWindowEnd(next.getEndAt());next.setFundingStartLedgerId(null);next.setFundingEndLedgerId(null);
            next.setQualifiedDepositAmount(BigDecimal.ZERO);next.setRenewalNetDepositAmount(BigDecimal.ZERO);next.setSettledConsumptionAmount(BigDecimal.ZERO);next.setSettledOrderCount(0);next.setAdjustmentAmount(BigDecimal.ZERO);
            next.setActivatedAt(null);next.setFinalizedAt(null);next.setSupersededAt(null);next.setReviewRequired(false);next.setVersion(0);next.setCreatedAt(time.now());next.setUpdatedAt(time.now());
            if(terms.countOverlapping(contract.getId(),null,next.getStartAt(),next.getEndAt())>0)throw conflict("下一期与已有权益重叠");
            if(terms.insert(next)!=1)throw conflict("下一期创建失败");
            if(manualGrant)audit(next,"MANUAL_RENEWAL_GRANTED",operator,reason,"manual_renewal_"+sourceId,encode(source));
            if(selection.fallbackReason()!=null)audit(next,"SAME_VERSION_FALLBACK",operator,selection.fallbackReason(),"renewal_version_fallback_"+sourceId,encode(source));
            audit(next,"RENEWAL_CREATED",operator,reason,"renewal_"+sourceId,encode(source));
        } else {
            next=terms.selectByIdForUpdate(next.getId());
            if(manualGrant&&!grantedExisting){String prior=encode(next);if(terms.grantScheduled(next.getId(),sourceId,next.getVersion(),time.now())!=1)throw conflict("人工补授排程版本已变化");next=terms.selectByIdForUpdate(next.getId());audit(next,"MANUAL_RENEWAL_GRANTED",operator,reason,"manual_renewal_"+sourceId,prior);}
        }
        if(!atBoundary)return next;
        if(!Objects.equals(contract.getCurrentTermId(),sourceId))throw conflict("续期生效前当前周期已变化");
        if(terms.countOverlapping(contract.getId(),next.getId(),next.getStartAt(),next.getEndAt())>0)throw conflict("续期生效存在权益重叠");
        String before=encode(next);Integer nextVersion=next.getVersion();
        boolean elapsed=!time.now().isBefore(next.getEndAt());
        next.setStatus(elapsed?"EXPIRED":"ACTIVE");next.setActivatedAt(elapsed?null:time.now());if(!waived)next.setQualifiedAt(source.getQualifiedAt());next.setQualificationStatus("QUALIFIED");
        next.setFundingEndLedgerId(funding.selectTransferWatermarkBefore(wallet.getId(),wallet.getUserId(),next.getStartAt()));
        if(terms.updateActivation(next,nextVersion,time.now())!=1)throw conflict("续期激活版本冲突");next.setVersion(nextVersion+1);
        Integer sourceVersion=source.getVersion();source.setStatus("RENEWED");if(terms.updateEvaluation(source,sourceVersion,time.now())!=1)throw conflict("原期续期状态保存冲突");
        contract.setCurrentTermId(next.getId());contract.setOperationsMode(next.getOperationsMode());contract.setFixedPrice(next.getEffectiveFixedPrice());contract.setMinInitialDeposit(next.getEffectiveInitialDeposit());contract.setValidFrom(next.getStartAt());contract.setValidUntil(next.getEndAt());contract.setActivatedAt(next.getActivatedAt());contract.setStatus(elapsed?"EXPIRED":"ACTIVE");contract.setUpdatedBy(operator);
        if(contracts.updateOperations(contract,contract.getVersion(),time.now())!=1)throw conflict("续期协议指针保存冲突");
        audit(next,elapsed?"RENEWAL_PERIOD_ELAPSED":"RENEWAL_ACTIVATED",operator,reason,"renewal_activate_"+sourceId,before);
        return next;
    }

    private record VersionSelection(CoursePrivatePlanVersion version,String fallbackReason){}
    /** 本人权益页面使用与执行相同的版本选择，停售套餐仍能展示原合同约定的续期提议。 */
    Map<String,Object> offer(CoursePrivateContractTerm source) {
        var choice=selectVersion(source,config(source.getConfigSnapshot()));if(choice.version()==null)throw conflict("原续期发布版本缺失");var request=config(choice.version().getConfigSnapshot());
        return Map.of("planId",source.getPlanId(),"planVersionId",choice.version().getId(),"platformId",source.getPlatformId(),"planName",request.getPlanName(),"rules",request.getRules());
    }
    private VersionSelection selectVersion(CoursePrivateContractTerm source,CoursePrivatePlanRequest sourceConfig) {
        var original=versions.selectById(source.getVersionId());
        if(!"LATEST_PUBLISHED".equals(sourceConfig.getRules().getRenewalPricePolicy()))return new VersionSelection(original,null);
        var latest=versions.selectLatestAt(source.getPlanId(),source.getEndAt());if(latest==null)return new VersionSelection(original,"下一期起点没有已发布且销售有效的候选，沿用原冻结版本");
        var config=config(latest.getConfigSnapshot());
        // 续期不承担档位、模式转换；这些变更必须经过明确的升级申请。
        if(!Objects.equals(config.getRules().getMode(),sourceConfig.getRules().getMode())||!Objects.equals(config.getFamilyCode(),sourceConfig.getFamilyCode())||!Objects.equals(config.getTierCode(),sourceConfig.getTierCode())||!Objects.equals(config.getTierRank(),sourceConfig.getTierRank())||config.getRules().getValidityDays()==null)return new VersionSelection(original,"候选版本改变模式、档位或家族，续期沿用原版本并等待独立升级确认");
        return new VersionSelection(latest,null);
    }

    private void claimDeposit(CoursePrivateContractTerm source,CourseReservedWallet wallet,CoursePrivatePlanRules rules) {
        if(!"NEW_DEPOSIT".equals(rules.getRenewalPolicy()))return;
        var facts=funding.selectFundingFacts(wallet.getId(),wallet.getUserId(),source.getFundingEndLedgerId(),null,source.getStartAt(),source.getEndAt(),time.now());
        var result=CoursePrivateFundingPolicy.assess(new CoursePrivateFundingPolicy.Window(wallet.getId(),wallet.getUserId(),source.getId(),source.getFundingEndLedgerId(),null,source.getStartAt(),source.getEndAt(),time.now()),facts,rules.getRenewalDepositAmount());
        if(!result.qualified())throw conflict("续期净新增预存资格已变化");
        for(Long id:result.unallocatedLedgerIds()) {
            var fact=facts.stream().filter(v->Objects.equals(id,v.getLedgerId())).findFirst().orElseThrow();
            var claim=new CoursePrivateFundingAllocation();claim.setTermId(source.getId());claim.setWalletId(wallet.getId());claim.setUserId(wallet.getUserId());claim.setLedgerId(id);claim.setAmount(fact.getAmount());claim.setCreatedAt(time.now());
            if(funding.insert(claim)!=1)throw conflict("续期资金资格认领失败");
        }
    }
    private void audit(CoursePrivateContractTerm term,String type,Long operator,String reason,String identity,String before) {
        var event=new CoursePrivateTermEvent();var origin=term.getRenewalSourceTermId()==null?null:terms.selectById(term.getRenewalSourceTermId());if(origin!=null){event.setSourceTermId(origin.getId());event.setSourceVersionId(origin.getVersionId());}event.setContractId(term.getContractId());event.setTermId(term.getId());event.setEventType(type);event.setBeforeJson(before);event.setAfterJson(encode(term));event.setTargetVersionId(term.getVersionId());event.setOperatorId(operator);event.setReason(reason);event.setBizNo(identity);event.setHappenedAt(time.now());
        if(events.insert(event)!=1)throw conflict("续期审计事件保存失败");
    }
    private CoursePrivatePlanRequest config(String value){try{var request=json.readValue(value,CoursePrivatePlanRequest.class);if(request.getRules()==null)throw new IllegalArgumentException();return request;}catch(Exception error){throw conflict("续期规则快照损坏，需人工核查");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw conflict("续期证据序列化失败");}}
    private void requireActive(User user){if(user==null||!Integer.valueOf(1).equals(user.getStatus()))throw new BusinessException(ResultCode.ACCOUNT_DISABLED);}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
}
