package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** 升级使用同一原钱包及下一期身份，资金门槛与原消费续期分开，不重新计价历史订单。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateUpgradeService {
    private final UserMapper users;
    private final CoursePlatformMapper products;
    private final PlatformCategoryMapper categories;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivatePlanMapper plans;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateApplicationMapper applications;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateFundingAllocationMapper funding;
    private final CoursePrivateTermEventMapper events;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @Autowired private CoursePrivateSettlementService settlement;
    @Value("${course.private-operations.enabled:false}") private boolean enabled;
    @Value("${course.private-operations.auto-upgrade-enabled:false}") private boolean automaticEnabled;

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm review(Long applicationId,Integer expectedVersion,Long operator,String reason) {
        return approve(applicationId,expectedVersion,operator,reason,false);
    }

    private record Candidate(CoursePrivatePlan plan,CoursePrivatePlanVersion version,CoursePrivatePlanRequest config){}
    /** 只执行原冻结规则明确允许的下一期升档；公开档位有多个同级可行候选时保留人工选择。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm upgradeAutomatic(Long sourceId) {
        if(!enabled||!automaticEnabled)return null;
        var source=terms.selectById(sourceId);if(source==null||source.getEndAt()==null||source.getActivatedAt()==null||"LEGACY_PERMANENT".equals(source.getOperationsMode()))return null;
        var original=config(source.getConfigSnapshot());var policy=original.getRules();
        if(!policy.isUpgradeAuto()||!"NEXT_TERM".equals(policy.getUpgradePolicy())||original.getFamilyCode()==null||original.getFamilyCode().isBlank())return null;
        active(users.selectByIdForUpdate(source.getUserId()));source=terms.selectById(sourceId);var contract=contracts.selectByBinding(source.getUserId(),source.getPlatformId());
        if(contract==null||!Objects.equals(contract.getCurrentTermId(),sourceId)||!Objects.equals(contract.getRelationGeneration(),source.getRelationGeneration())||Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus())||Boolean.TRUE.equals(source.getReviewRequired())||terms.selectByRenewalSource(sourceId)!=null||time.now().isAfter(time.endAt(source.getEndAt(),policy.getRenewalAfterDays())))return null;
        var wallet=wallets.selectByBinding(source.getUserId(),source.getPlatformId());CoursePricingService.requireBinding(contract,wallet,source.getUserId(),source.getPlatformId());
        var list=plans.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CoursePrivatePlan>().eq("platform_id",source.getPlatformId()).eq("status","PUBLISHED").eq("visible_to_agents",true).orderByAsc("id").last("LIMIT 1001"));if(list.size()>1000)throw conflict("自动升级候选超过单次核验上限，需运营明确档位");
        var eligible=new TreeMap<Integer,List<Candidate>>();
        for(var plan:list){var version=versions.selectById(plan.getCurrentVersionId());if(version==null||version.getPublishedAt()==null||version.getPublishedAt().isAfter(time.now())||!time.inWindow(time.now(),version.getEffectiveFrom(),version.getEffectiveUntil()))continue;var target=config(version.getConfigSnapshot());
            if(!Objects.equals(target.getFamilyCode(),original.getFamilyCode())||target.getTierRank()<=Objects.requireNonNullElse(source.getTierRank(),0)||target.getTierCode()==null||target.getTierCode().isBlank()||target.getRules().getActivationFrom()!=null&&target.getRules().getActivationFrom().isAfter(source.getEndAt()))continue;
            var projected=new CoursePrivateContractTerm();projected.setEffectiveInitialDeposit(version.getInitialDepositAmount());projected.setFundingStartLedgerId(Objects.requireNonNullElse(source.getFundingEndLedgerId(),0L));projected.setFundingWindowStart(source.getStartAt());
            if(assessment(wallet,source,projected,policy).qualified())eligible.computeIfAbsent(target.getTierRank(),unused->new ArrayList<>()).add(new Candidate(plan,version,target));
        }
        if(eligible.isEmpty())return null;var closest=eligible.firstEntry().getValue();if(closest.size()!=1)throw conflict("同级存在多套可行公开套餐，自动升级保留人工选择");var choice=closest.get(0);
        String key="system_upgrade_"+sourceId+"_"+choice.version().getId();String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(List.of("AUTO_UPGRADE",sourceId,source.getRelationGeneration(),choice.version().getId()))));}catch(Exception error){throw conflict("自动升级身份生成失败");}
        var app=applications.selectByRequestKey(source.getUserId(),key);
        if(app!=null&&(!fingerprint.equals(app.getOperationFingerprint())||!"UPGRADE".equals(app.getApplicationType())||!Objects.equals(app.getSourceTermId(),sourceId)||!Objects.equals(app.getVersionId(),choice.version().getId())))throw conflict("自动升级身份与原申请冲突，不能冒认其他申请");
        if(app==null){app=new CoursePrivateApplication();app.setUserId(source.getUserId());app.setPlatformId(source.getPlatformId());app.setPlanId(choice.plan().getId());app.setVersionId(choice.version().getId());app.setContractId(source.getContractId());app.setSourceTermId(sourceId);app.setApplicationType("UPGRADE");app.setApplicationReason("冻结套餐允许自动下一期升级，真实新增资金已达到公开档位条件");app.setEffectivePolicy("NEXT_TERM");app.setAcknowledgeLegacyChange(false);app.setStatus("PENDING");app.setSubmittedAt(time.now());app.setFundingStartLedgerId(funding.selectWalletLedgerWatermark(wallet.getId(),wallet.getUserId()));app.setRequestKey(key);app.setOperationFingerprint(fingerprint);app.setVersion(0);if(applications.insert(app)!=1)throw conflict("自动升级申请身份保存失败");}
        return approve(app.getId(),app.getVersion(),0L,"依据冻结的自动升级政策及真实资金认领排定下一期",true);
    }

    private CoursePrivateContractTerm approve(Long applicationId,Integer expectedVersion,Long operator,String reason,boolean automatic) {
        if(!automatic)access.require(operator,"course-private-contract:review");CoursePrivatePlanService.reason(reason);
        var initial=applications.selectById(applicationId);if(initial==null||!"UPGRADE".equals(initial.getApplicationType())||initial.getSourceTermId()==null||initial.getContractId()==null)throw conflict("升级申请不存在");
        active(users.selectByIdForUpdate(initial.getUserId()));requireProduct(products.selectByIdForUpdate(initial.getPlatformId()));
        var plan=plans.selectByIdForUpdate(initial.getPlanId());if(plan==null)throw conflict("目标套餐不存在");
        var contract=contracts.selectByIdForUpdate(initial.getContractId());var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw conflict("原课程专户不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());
        var source=terms.selectByIdForUpdate(initial.getSourceTermId());binding(contract,source);
        var app=applications.selectByIdForUpdate(applicationId);if(app==null||!Objects.equals(app.getUserId(),initial.getUserId())||!Objects.equals(app.getSourceTermId(),source.getId())||!Objects.equals(app.getContractId(),contract.getId()))throw conflict("升级申请绑定已变化");
        if("APPROVED".equals(app.getStatus())){var proof=events.selectByBizNo("application_"+applicationId+"_approve");if(proof==null||proof.getTermId()==null)throw conflict("原升级审批依据缺失");var original=terms.selectById(proof.getTermId());if(original==null)throw conflict("原升级结果缺失");return original;}
        if(!enabled||!"PENDING".equals(app.getStatus())||expectedVersion==null||!Objects.equals(app.getVersion(),expectedVersion))throw conflict("升级申请状态、版本或功能开关已变化");
        requireSource(contract,source);
        if(!"PUBLISHED".equals(plan.getStatus())||!Objects.equals(plan.getPlatformId(),source.getPlatformId()))throw conflict("目标套餐已停售或不属于当前商品");
        var version=versions.selectById(app.getVersionId());if(version==null||!Objects.equals(version.getPlanId(),plan.getId())||!Objects.equals(version.getPlatformId(),source.getPlatformId())||version.getPublishedAt()==null||version.getPublishedAt().isAfter(time.now())||!time.inWindow(time.now(),version.getEffectiveFrom(),version.getEffectiveUntil()))throw conflict("目标发布版本不存在或不在销售有效期");
        var target=config(version.getConfigSnapshot());CoursePrivatePlanRulePolicy.validate(target.getRules());if(target.getRules().getFixedPrice().compareTo(version.getFixedPrice())!=0||target.getRules().getInitialDepositAmount().compareTo(version.getInitialDepositAmount())!=0)throw conflict("升级发布版本与冻结规则不一致");
        boolean legacy="LEGACY_PERMANENT".equals(source.getOperationsMode());var original=legacy?null:config(source.getConfigSnapshot());var policy=legacy?target.getRules():original.getRules();
        boolean immediate=Set.of("IMMEDIATE","REVIEW_IMMEDIATE").contains(app.getEffectivePolicy());
        if(automatic&&(!automaticEnabled||legacy||!policy.isUpgradeAuto()||!"NEXT_TERM".equals(policy.getUpgradePolicy())||immediate||!Boolean.TRUE.equals(plan.getVisibleToAgents())||!Objects.equals(plan.getCurrentVersionId(),version.getId())))throw conflict("冻结自动升级授权或公开目标版本已变化");
        if(!immediate&&!"NEXT_TERM".equals(app.getEffectivePolicy())||"DISABLED".equals(policy.getUpgradePolicy())||immediate&&!"REVIEW_IMMEDIATE".equals(policy.getUpgradePolicy())||!immediate&&source.getEndAt()==null)throw conflict("冻结升级政策不允许本次生效方式");
        if(immediate){access.require(operator,"course-private-term:adjust");access.require(operator,"course-private-settlement:execute");}
        if(legacy){if(!immediate||!Boolean.TRUE.equals(app.getAcknowledgeLegacyChange())||!Objects.equals(app.getSubmittedBy(),app.getUserId())||app.getFundingStartLedgerId()==null)throw conflict("旧协议转换必须代理本人明确确认、独立即时审核及申请时资金水位");}
        else if(original.getFamilyCode()==null||original.getFamilyCode().isBlank()||!Objects.equals(original.getFamilyCode(),target.getFamilyCode())||target.getTierRank()<=Objects.requireNonNullElse(source.getTierRank(),0)||target.getTierCode()==null||target.getTierCode().isBlank())throw conflict("升级必须是同商品同家族的更高档位");
        if(terms.selectByRenewalSource(source.getId())!=null)throw conflict("来源周期已确定下一期，不能再次升级或覆盖已批准权益");
        var start=immediate?time.now():source.getEndAt();if(target.getRules().getActivationFrom()!=null&&target.getRules().getActivationFrom().isAfter(start))throw conflict("目标允许生效时间尚未到达本次升级起点");
        var end=time.endAt(start,target.getRules().getValidityDays());if(terms.countOverlapping(contract.getId(),immediate?source.getId():null,start,end)>0)throw conflict("升级权益与已有周期重叠");
        var next=new CoursePrivateContractTerm();next.setContractId(contract.getId());next.setUserId(contract.getUserId());next.setPlatformId(contract.getPlatformId());next.setPlanId(plan.getId());next.setVersionId(version.getId());next.setTermNo(terms.selectByContract(contract.getId()).stream().mapToInt(CoursePrivateContractTerm::getTermNo).max().orElse(0)+1);next.setRelationGeneration(source.getRelationGeneration());next.setOperationsMode(target.getRules().getMode());next.setTierCode(target.getTierCode());next.setTierRank(target.getTierRank());next.setEffectiveFixedPrice(version.getFixedPrice());next.setEffectiveInitialDeposit(version.getInitialDepositAmount());next.setRenewalTarget("PERFORMANCE".equals(target.getRules().getRenewalPolicy())?target.getRules().getRenewalConsumptionTarget():target.getRules().getRenewalDepositAmount());next.setRenewalSourceTermId(source.getId());next.setQualificationKind("UPGRADE_FUNDS");next.setStatus("SCHEDULED");next.setQualificationStatus("QUALIFIED");next.setStartAt(start);next.setEndAt(end);next.setSettlementDeadlineAt(time.settlementDeadline(end,target.getRules().getSettlementGraceDays()));next.setFundingWindowStart(legacy?app.getSubmittedAt():source.getStartAt());next.setFundingStartLedgerId(legacy?app.getFundingStartLedgerId():Objects.requireNonNullElse(source.getFundingEndLedgerId(),0L));next.setQualifiedDepositAmount(BigDecimal.ZERO);next.setQualificationBasisAmount(BigDecimal.ZERO);next.setRenewalNetDepositAmount(BigDecimal.ZERO);next.setSettledConsumptionAmount(BigDecimal.ZERO);next.setSettledOrderCount(0);next.setAdjustmentAmount(BigDecimal.ZERO);next.setReviewRequired(false);next.setConfigSnapshot(version.getConfigSnapshot());next.setVersion(0);next.setCreatedAt(time.now());next.setUpdatedAt(time.now());
        var assessment=assessment(wallet,source,next,policy);if(!assessment.qualified())throw conflict("升级资金资格不足，原钱包余额不能冒充新增差额");
        next.setQualifiedDepositAmount(assessment.netDepositAmount().max(BigDecimal.ZERO));next.setQualificationBasisAmount(Objects.requireNonNullElse(source.getQualificationBasisAmount(),BigDecimal.ZERO).max(next.getEffectiveInitialDeposit()));next.setQualifiedAt(assessment.qualifiedAt());
        if(terms.insert(next)!=1)throw conflict("升级周期保存失败");claim(wallet,next,assessment);
        audit(next,immediate?"UPGRADE_IMMEDIATE_APPROVED":"UPGRADE_SCHEDULED",operator,reason,"upgrade_"+applicationId,encode(source));
        if(!time.now().isBefore(start))next=activate(contract,wallet,source,next,policy,operator,reason);
        if(applications.review(applicationId,expectedVersion,"APPROVED",operator,reason,contract.getId(),time.now())!=1)throw conflict("升级审批保存冲突");
        audit(next,"APPLICATION_APPROVED",operator,reason,"application_"+applicationId+"_approve",null);return next;
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm activateScheduled(Long termId) {
        var initial=terms.selectById(termId);if(initial==null||!"UPGRADE_FUNDS".equals(initial.getQualificationKind()))return null;
        active(users.selectByIdForUpdate(initial.getUserId()));var contract=contracts.selectByIdForUpdate(initial.getContractId());var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw conflict("原升级专户不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());
        var source=terms.selectByIdForUpdate(initial.getRenewalSourceTermId());var next=terms.selectByIdForUpdate(termId);binding(contract,source);binding(contract,next);
        if(Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus()))return null;
        if(!"SCHEDULED".equals(next.getStatus()))return Set.of("ACTIVE","EXPIRED","SUPERSEDED","RENEWED").contains(next.getStatus())?next:null;
        if(time.now().isBefore(next.getStartAt()))return next;
        requireSource(contract,source);var policy="LEGACY_PERMANENT".equals(source.getOperationsMode())?config(next.getConfigSnapshot()).getRules():config(source.getConfigSnapshot()).getRules();
        return activate(contract,wallet,source,next,policy,0L,"升级排程到期，按原资金来源重新核验");
    }

    private CoursePrivateContractTerm activate(CoursePrivateContract contract,CourseReservedWallet wallet,CoursePrivateContractTerm source,CoursePrivateContractTerm next,CoursePrivatePlanRules policy,Long operator,String reason) {
        var proof=assessment(wallet,source,next,policy);if(!proof.qualified()){Integer expected=next.getVersion();next.setQualificationStatus("NOT_QUALIFIED");next.setQualifiedDepositAmount(proof.netDepositAmount().max(BigDecimal.ZERO));if(terms.updateEvaluation(next,expected,time.now())!=1)throw conflict("升级排程资格保存冲突");return null;}
        // 重叠计数包含本次SCHEDULED自身，只允许这一份；原周期会在同事务设置截断/结束状态。
        claim(wallet,next,proof);if(terms.countOverlapping(contract.getId(),source.getId(),next.getStartAt(),next.getEndAt())>1)throw conflict("升级生效窗口发生重叠");
        String before=encode(source);Integer sourceVersion=source.getVersion();boolean immediate=!Objects.equals(next.getStartAt(),source.getEndAt());source.setStatus(immediate?"SUPERSEDED":"RENEWED");if(immediate)source.setSupersededAt(next.getStartAt());if(terms.updateEvaluation(source,sourceVersion,time.now())!=1)throw conflict("原周期升级边界保存冲突");
        // 按冻结起点关闭资格水位，迟到激活不能吞掉已属于新周期且未认领的真实转入。
        Integer version=next.getVersion();boolean elapsed=next.getEndAt()!=null&&!time.now().isBefore(next.getEndAt());next.setStatus(elapsed?"EXPIRED":"ACTIVE");next.setActivatedAt(elapsed?null:time.now());next.setQualificationStatus("QUALIFIED");next.setQualifiedAt(proof.qualifiedAt());next.setQualifiedDepositAmount(proof.netDepositAmount().max(BigDecimal.ZERO));next.setQualificationBasisAmount(Objects.requireNonNullElse(source.getQualificationBasisAmount(),BigDecimal.ZERO).max(next.getEffectiveInitialDeposit()));next.setFundingEndLedgerId(funding.selectTransferWatermarkBefore(wallet.getId(),wallet.getUserId(),next.getStartAt()));if(terms.updateActivation(next,version,time.now())!=1)throw conflict("升级生效版本已变化");next.setVersion(version+1);
        contract.setCurrentTermId(next.getId());contract.setOperationsMode(next.getOperationsMode());contract.setFixedPrice(next.getEffectiveFixedPrice());contract.setMinInitialDeposit(next.getEffectiveInitialDeposit());contract.setActivatedAt(next.getActivatedAt());contract.setValidFrom(next.getStartAt());contract.setValidUntil(next.getEndAt());contract.setStatus(elapsed?"EXPIRED":"ACTIVE");contract.setUpdatedBy(operator);if(contracts.updateOperations(contract,contract.getVersion(),time.now())!=1)throw conflict("升级协议指针保存冲突");
        audit(next,elapsed?"UPGRADE_PERIOD_ELAPSED":"UPGRADE_ACTIVATED",operator,reason,"upgrade_activate_"+next.getId(),before);
        if(immediate&&!"LEGACY_PERMANENT".equals(source.getOperationsMode()))settlement.evaluate(source.getId());return next;
    }

    private CoursePrivateFundingPolicy.Assessment assessment(CourseReservedWallet wallet,CoursePrivateContractTerm source,CoursePrivateContractTerm next,CoursePrivatePlanRules policy) {
        BigDecimal required="DIFFERENCE".equals(policy.getUpgradeDepositPolicy())?next.getEffectiveInitialDeposit().subtract(Objects.requireNonNullElse(source.getQualificationBasisAmount(),BigDecimal.ZERO)).max(BigDecimal.ZERO):next.getEffectiveInitialDeposit();
        if("EXISTING_BALANCE".equals(policy.getUpgradeDepositPolicy()))return new CoursePrivateFundingPolicy.Assessment(wallet.getBalance(),wallet.getBalance().compareTo(required)>=0,time.now(),List.of());
        var facts=funding.selectFundingFacts(wallet.getId(),wallet.getUserId(),next.getFundingStartLedgerId(),null,next.getFundingWindowStart(),null,time.now());
        return CoursePrivateFundingPolicy.assess(new CoursePrivateFundingPolicy.Window(wallet.getId(),wallet.getUserId(),next.getId(),next.getFundingStartLedgerId(),null,next.getFundingWindowStart(),null,time.now()),facts,required);
    }
    private void claim(CourseReservedWallet wallet,CoursePrivateContractTerm next,CoursePrivateFundingPolicy.Assessment proof){var facts=funding.selectFundingFacts(wallet.getId(),wallet.getUserId(),next.getFundingStartLedgerId(),null,next.getFundingWindowStart(),null,time.now());for(Long id:proof.unallocatedLedgerIds()){var fact=facts.stream().filter(v->Objects.equals(v.getLedgerId(),id)).findFirst().orElseThrow();var row=new CoursePrivateFundingAllocation();row.setTermId(next.getId());row.setWalletId(wallet.getId());row.setUserId(wallet.getUserId());row.setLedgerId(id);row.setAmount(fact.getAmount());row.setCreatedAt(time.now());if(funding.insert(row)!=1)throw conflict("升级资金资格认领失败");}}
    private void requireSource(CoursePrivateContract contract,CoursePrivateContractTerm source){if(Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus())||!Objects.equals(contract.getCurrentTermId(),source.getId())||Set.of("CANCELLED","SUPERSEDED","SUSPENDED","TERMINATED","RENEWED").contains(source.getStatus())||!"LEGACY_PERMANENT".equals(source.getOperationsMode())&&source.getActivatedAt()==null)throw conflict("原周期已变更或未取得可升级权益");}
    private void binding(CoursePrivateContract contract,CoursePrivateContractTerm term){if(term==null||!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),contract.getUserId())||!Objects.equals(term.getPlatformId(),contract.getPlatformId())||!Objects.equals(term.getRelationGeneration(),contract.getRelationGeneration()))throw conflict("升级周期关系绑定异常");}
    private void requireProduct(CoursePlatform product){if(product==null||!Integer.valueOf(1).equals(product.getStatus()))throw new BusinessException(ResultCode.COURSE_NOT_FOUND);if(product.getCategoryId()!=null){var category=categories.selectById(product.getCategoryId());if(category==null||!Integer.valueOf(1).equals(category.getStatus()))throw new BusinessException(ResultCode.COURSE_NOT_FOUND);}}
    private void audit(CoursePrivateContractTerm term,String type,Long operator,String reason,String identity,String before){var event=new CoursePrivateTermEvent();var origin=term.getRenewalSourceTermId()==null?null:terms.selectById(term.getRenewalSourceTermId());if(origin!=null){event.setSourceTermId(origin.getId());event.setSourceVersionId(origin.getVersionId());}event.setContractId(term.getContractId());event.setTermId(term.getId());event.setEventType(type);event.setBeforeJson(before);event.setAfterJson(encode(term));event.setTargetVersionId(term.getVersionId());event.setOperatorId(operator);event.setReason(reason);event.setBizNo(identity);event.setHappenedAt(time.now());if(events.insert(event)!=1)throw conflict("升级审计事件保存失败");}
    private CoursePrivatePlanRequest config(String value){try{var request=json.readValue(value,CoursePrivatePlanRequest.class);if(request.getRules()==null)throw new IllegalArgumentException();return request;}catch(Exception error){throw conflict("升级规则快照损坏，需人工核查");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw conflict("升级依据序列化失败");}}
    private void active(User user){if(user==null||!Integer.valueOf(1).equals(user.getStatus()))throw new BusinessException(ResultCode.ACCOUNT_DISABLED);}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
}
