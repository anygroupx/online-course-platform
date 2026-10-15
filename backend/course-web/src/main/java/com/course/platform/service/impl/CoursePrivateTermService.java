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
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 周期首开及资格激活只认发布规则和真实资金，复用原唯一专户而不收资格费用。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateTermService {
    private final UserMapper users;
    private final CoursePlatformMapper products;
    private final PlatformCategoryMapper categories;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivatePlanMapper plans;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateApplicationMapper applications;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateFundingAllocationMapper allocations;
    private final CoursePrivateTermEventMapper events;
    private final CoursePrivateTermOrderMapper termOrders;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @Value("${course.private-operations.enabled:false}") private boolean enabled;
    @Value("${course.private-operations.general-checkout-enabled:false}") private boolean generalCheckoutEnabled;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateRenewalService renewal;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateUpgradeService upgrade;

    /** 只在原冻结政策明确允许且权益不可用时提供普通价，不把专户不足当作权益失效。 */
    public CoursePrivateContractTerm ordinaryPolicy(CoursePrivateContract contract,boolean lock){
        if(!generalCheckoutEnabled||contract==null||contract.getCurrentTermId()==null||"LEGACY_PERMANENT".equals(contract.getOperationsMode())||contract.getOperationsMode()==null)return null;
        var term=lock?terms.selectByIdForUpdate(contract.getCurrentTermId()):terms.selectById(contract.getCurrentTermId());
        if(term==null||!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),contract.getUserId())||!Objects.equals(term.getPlatformId(),contract.getPlatformId())||!Objects.equals(term.getRelationGeneration(),contract.getRelationGeneration()))throw conflict("普通价政策绑定异常");
        if(!config(term.getConfigSnapshot()).getRules().isAllowGeneralCheckout())return null;
        if("ACTIVE".equals(contract.getStatus())&&"ACTIVE".equals(term.getStatus())&&term.getActivatedAt()!=null&&time.inWindow(time.now(),term.getStartAt(),term.getEndAt()))return null;
        return term;
    }

    /** 申请事务已锁拥有者；补取协议与原钱包锁保存资格水位，不推测旧门槛已经用过的资金。 */
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long captureFundingWatermark(Long userId,Long platformId) {
        active(users.selectByIdForUpdate(userId));var initial=contracts.selectByBinding(userId,platformId);if(initial==null)throw conflict("原课程协议不存在");
        var contract=contracts.selectByIdForUpdate(initial.getId());var bound=wallets.selectByBinding(userId,platformId);if(bound==null)throw conflict("原课程专户不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,userId,platformId);
        return allocations.selectWalletLedgerWatermark(wallet.getId(),userId);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm enroll(Long applicationId,Integer expectedVersion,Long operatorId,String reason) {
        access.require(operatorId,"course-private-contract:review");CoursePrivatePlanService.reason(reason);
        return approve(applicationId,expectedVersion,operatorId,reason,false);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm enrollAutomatic(Long applicationId,Long userId) {
        access.self(userId);var app=applications.selectById(applicationId);
        if(app==null||!Objects.equals(app.getUserId(),userId))throw missing("权益申请不存在");
        return approve(applicationId,app.getVersion(),userId,"公开套餐按自动开通规则审核",true);
    }

    private CoursePrivateContractTerm approve(Long applicationId,Integer expectedVersion,Long operatorId,String reason,boolean automatic) {
        var initial=applications.selectById(applicationId);if(initial==null)throw missing("权益申请不存在");
        active(users.selectByIdForUpdate(initial.getUserId()));
        var product=products.selectByIdForUpdate(initial.getPlatformId());requireProduct(product);
        var plan=plans.selectByIdForUpdate(initial.getPlanId());if(plan==null)throw missing("运营套餐不存在");
        var existing=contracts.selectByBinding(initial.getUserId(),initial.getPlatformId());
        CoursePrivateContract contract=existing==null?null:contracts.selectByIdForUpdate(existing.getId());
        CourseReservedWallet wallet=null;
        if(contract!=null){var bound=wallets.selectByBinding(contract.getUserId(),contract.getPlatformId());if(bound==null)throw conflict("原专户绑定异常");wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());}
        var app=applications.selectByIdForUpdate(applicationId);if(app==null||!Objects.equals(app.getUserId(),initial.getUserId()))throw conflict("权益申请绑定已变化");
        if("APPROVED".equals(app.getStatus())){var event=events.selectByBizNo("application_"+applicationId+"_approve");if(event==null||event.getTermId()==null)throw conflict("申请审批事件缺失，需人工核查");var result=terms.selectById(event.getTermId());if(result==null)throw conflict("原审批周期缺失");return result;}
        if(!enabled)throw conflict("密价运营功能尚未开启");
        if(!"PENDING".equals(app.getStatus())||expectedVersion==null||!Objects.equals(app.getVersion(),expectedVersion))throw conflict("权益申请状态或版本已变化");
        if(automatic&&(!"AUTO".equals(plan.getApplicationMode())||!Boolean.TRUE.equals(plan.getVisibleToAgents())||!Objects.equals(app.getUserId(),operatorId)))throw conflict("套餐不允许自动开通");
        if(!"PUBLISHED".equals(plan.getStatus())||!Objects.equals(plan.getPlatformId(),app.getPlatformId()))throw conflict("套餐已停用或商品绑定异常");
        var version=versions.selectById(app.getVersionId());
        if(version==null||!Objects.equals(version.getPlanId(),plan.getId())||!Objects.equals(version.getPlatformId(),app.getPlatformId())||version.getPublishedAt()==null||version.getPublishedAt().isAfter(time.now())||!time.inWindow(time.now(),version.getEffectiveFrom(),version.getEffectiveUntil()))throw conflict("申请发布版本不存在或不在销售有效期内");
        var config=config(version.getConfigSnapshot());CoursePrivatePlanRulePolicy.validate(config.getRules());
        if(config.getRules().getFixedPrice().compareTo(version.getFixedPrice())!=0||config.getRules().getInitialDepositAmount().compareTo(version.getInitialDepositAmount())!=0)throw conflict("发布版本与冻结规则不一致");
        if(!Set.of("OPEN","REJOIN").contains(Objects.toString(app.getApplicationType(),"")))throw conflict("该申请需使用续期或升级审核流程");
        if("REJOIN".equals(app.getApplicationType())) {
            if(automatic||contract==null||!"TERMINATED".equals(contract.getStatus())||!Boolean.TRUE.equals(app.getAcknowledgeLegacyChange())||!Objects.equals(app.getSubmittedBy(),app.getUserId()))throw conflict("重新签约须人工审核终止关系及代理本人明确确认");
            if(app.getContractId()!=null&&!Objects.equals(app.getContractId(),contract.getId()))throw conflict("重新签约的原协议绑定异常");
        } else if(contract!=null)throw conflict("已存在专户关系，须明确申请续期、升级或重新签约");
        if(contract==null){
            contract=new CoursePrivateContract();contract.setUserId(app.getUserId());contract.setPlatformId(app.getPlatformId());contract.setFixedPrice(version.getFixedPrice());contract.setMinInitialDeposit(version.getInitialDepositAmount());contract.setOperationsMode(config.getRules().getMode());contract.setRelationGeneration(1);contract.setStatus("PENDING_ACTIVATION");contract.setVersion(0);contract.setCreatedBy(operatorId);contract.setUpdatedBy(operatorId);contract.setCreateTime(time.now());contract.setUpdateTime(time.now());
            if(contracts.insert(contract)!=1)throw conflict("协议创建失败");
            wallet=new CourseReservedWallet();wallet.setUserId(contract.getUserId());wallet.setPlatformId(contract.getPlatformId());wallet.setContractId(contract.getId());wallet.setBalance(new BigDecimal("0.00"));wallet.setVersion(0);wallet.setCreateTime(time.now());wallet.setUpdateTime(time.now());if(wallets.insert(wallet)!=1)throw conflict("课程专户创建失败");
        } else contract.setRelationGeneration(Objects.requireNonNullElse(contract.getRelationGeneration(),1)+1);
        var history=terms.selectByContract(contract.getId());int number=history.stream().mapToInt(v->v.getTermNo()).max().orElse(0)+1;
        var term=new CoursePrivateContractTerm();term.setContractId(contract.getId());term.setUserId(contract.getUserId());term.setPlatformId(contract.getPlatformId());term.setPlanId(plan.getId());term.setVersionId(version.getId());term.setTermNo(number);term.setRelationGeneration(contract.getRelationGeneration());term.setOperationsMode(config.getRules().getMode());term.setTierCode(config.getTierCode());term.setTierRank(config.getTierRank());term.setEffectiveFixedPrice(version.getFixedPrice());term.setEffectiveInitialDeposit(version.getInitialDepositAmount());term.setRenewalTarget("PERFORMANCE".equals(version.getRenewalPolicy())?version.getRenewalConsumptionTarget():version.getRenewalDepositAmount());term.setStatus("PENDING_ACTIVATION");term.setQualificationStatus("PENDING");term.setFundingWindowStart(time.now());term.setFundingStartLedgerId(allocations.selectWalletLedgerWatermark(wallet.getId(),term.getUserId()));term.setQualifiedDepositAmount(BigDecimal.ZERO);term.setSettledConsumptionAmount(BigDecimal.ZERO);term.setSettledOrderCount(0);term.setAdjustmentAmount(BigDecimal.ZERO);term.setReviewRequired(false);term.setConfigSnapshot(version.getConfigSnapshot());term.setVersion(0);term.setCreatedAt(time.now());term.setUpdatedAt(time.now());
        term.setQualificationKind("INITIAL_FUNDS");term.setQualificationBasisAmount(BigDecimal.ZERO);term.setRenewalNetDepositAmount(BigDecimal.ZERO);
        if(terms.insert(term)!=1)throw conflict("权益周期创建失败");
        contract.setCurrentTermId(term.getId());contract.setOperationsMode(term.getOperationsMode());contract.setFixedPrice(term.getEffectiveFixedPrice());contract.setMinInitialDeposit(term.getEffectiveInitialDeposit());contract.setStatus("PENDING_ACTIVATION");contract.setActivatedAt(null);contract.setValidFrom(null);contract.setValidUntil(null);contract.setUpdatedBy(operatorId);saveContract(contract);
        activateInitial(contract,wallet,term);
        if(applications.review(applicationId,app.getVersion(),"APPROVED",operatorId,reason,contract.getId(),time.now())!=1)throw conflict("权益审批保存冲突");
        event(contract,term,"APPLICATION_APPROVED",operatorId,reason,"application_"+applicationId+"_approve",null);
        return term;
    }

    /** 成功资金事务调用本方法，账户锁仍在同一事务内；失效前排程重新核验，不撤销已生效资格。 */
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void afterWalletChanged(Long userId,Long walletId) {
        if(users.selectByIdForUpdate(userId)==null)throw missing("专户拥有者不存在");var initial=wallets.selectById(walletId);if(initial==null||!Objects.equals(initial.getUserId(),userId))throw conflict("资金变更拥有者异常");
        var contract=contracts.selectByIdForUpdate(initial.getContractId());var wallet=wallets.selectByIdForUpdate(walletId);CoursePricingService.requireBinding(contract,wallet,userId,initial.getPlatformId());
        if(legacy(contract)||contract.getCurrentTermId()==null||Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus()))return;
        var term=terms.selectByIdForUpdate(contract.getCurrentTermId());requireTermBinding(contract,term);
        if(term.getRenewalSourceTermId()==null&&Set.of("PENDING_ACTIVATION","SCHEDULED").contains(term.getStatus()))activateInitial(contract,wallet,term);
    }

    /** 报价可读取待生效事实，真实扣款会在账户锁下完成排程激活和第二次核验。 */
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm effectiveTerm(Long userId,Long platformId,boolean lock) {
        if(lock)active(users.selectByIdForUpdate(userId));
        var contract=contracts.selectByBinding(userId,platformId);if(contract==null||legacy(contract))return null;
        if(lock)contract=contracts.selectByIdForUpdate(contract.getId());
        if(Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus()))return null;
        var bound=wallets.selectByBinding(userId,platformId);if(bound==null)throw conflict("课程专户绑定异常");var wallet=lock?wallets.selectByIdForUpdate(bound.getId()):bound;CoursePricingService.requireBinding(contract,wallet,userId,platformId);
        var candidates=terms.selectEffectiveByBinding(userId,platformId,time.now());if(candidates.size()>1)throw conflict("商品存在重叠权益，需人工核查");if(candidates.isEmpty())return null;
        var term=lock?terms.selectByIdForUpdate(candidates.get(0).getId()):candidates.get(0);requireTermBinding(contract,term);
        if("SCHEDULED".equals(term.getStatus())) {
            if(term.getRenewalSourceTermId()!=null) {
                // 自动续期开关不能阻断已经批准的排程；到账、退款和履约资格在期界再次核验。
                if("UPGRADE_FUNDS".equals(term.getQualificationKind())){if(upgrade==null)throw conflict("升级执行服务尚未就绪");term=upgrade.activateScheduled(term.getId());}
                else {if(renewal==null)throw conflict("续期执行服务尚未就绪");term=renewal.activateScheduled(term.getRenewalSourceTermId());}
                if(term==null||!"ACTIVE".equals(term.getStatus()))return null;
            } else {
                if(lock)activateInitial(contract,wallet,term);
                else if(!initialAssessment(wallet,term).qualified())return null;
            }
        }
        // ACTIVE已经取得本期购买权，续期目标PENDING不能把本期购买资格锁死。
        boolean vested="ACTIVE".equals(term.getStatus())&&term.getActivatedAt()!=null;
        boolean scheduled="SCHEDULED".equals(term.getStatus())&&"QUALIFIED".equals(term.getQualificationStatus());
        return (vested||scheduled)&&time.inWindow(time.now(),term.getStartAt(),term.getEndAt())?term:null;
    }

    /** 原订单扣款前核验全部不可变周期绑定，空字段不能把现代权益冒充旧版购买。 */
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateContractTerm requireOrderBinding(CourseOrder order) {
        if(order==null||order.getId()==null||order.getPrivateTermId()==null)throw conflict("现代密价订单必须绑定成交周期");
        active(users.selectByIdForUpdate(order.getUserId()));var contract=contracts.selectByIdForUpdate(order.getPrivateContractId());
        var wallet=wallets.selectByIdForUpdate(order.getWalletId());CoursePricingService.requireBinding(contract,wallet,order.getUserId(),order.getPlatformId());
        var term=terms.selectByIdForUpdate(order.getPrivateTermId());requireTermBinding(contract,term);
        if(!"ACTIVE".equals(contract.getStatus())||!Objects.equals(contract.getCurrentTermId(),term.getId())||!"ACTIVE".equals(term.getStatus())||term.getActivatedAt()==null||!time.inWindow(time.now(),term.getStartAt(),term.getEndAt())||!Objects.equals(order.getPrivateTermVersion(),term.getVersion())||!Objects.equals(order.getPrivatePlanVersionId(),term.getVersionId())||!Objects.equals(order.getPrivateOperationsMode(),term.getOperationsMode())||!"PRIVATE".equals(order.getPriceSource())||!"COURSE_WALLET".equals(order.getPaymentSource())||order.getAmount()==null||order.getAmount().compareTo(term.getEffectiveFixedPrice())!=0)throw conflict("成交周期、规则版本或购买有效期已变化");
        return term;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void bindOrder(CourseOrder order,CoursePrivateContractTerm selected) {
        if(order==null||order.getId()==null)throw conflict("原支付订单不存在");var existing=termOrders.selectByOrderId(order.getId());
        if(existing!=null){if(!Objects.equals(existing.getTermId(),order.getPrivateTermId())||!Objects.equals(existing.getOrderNo(),order.getOrderNo())||existing.getOrderedAmount().compareTo(order.getAmount())!=0)throw conflict("订单周期身份冲突");return;}
        var term=requireOrderBinding(order);if(selected==null||!Objects.equals(selected.getId(),term.getId()))throw conflict("所选成交周期已变化");
        var row=new CoursePrivateTermOrder();row.setTermId(term.getId());row.setContractId(term.getContractId());row.setUserId(term.getUserId());row.setPlatformId(term.getPlatformId());row.setOrderId(order.getId());row.setOrderNo(order.getOrderNo());row.setOrderedAmount(order.getAmount());row.setQualifiedAmount(BigDecimal.ZERO);row.setQualificationStatus("PAID");row.setPaidAt(time.now());row.setLastReconciledAt(time.now());if(termOrders.insert(row)!=1)throw conflict("订单周期关联保存失败");
    }

    private void activateInitial(CoursePrivateContract contract,CourseReservedWallet wallet,CoursePrivateContractTerm term) {
        if(!Set.of("PENDING_ACTIVATION","SCHEDULED").contains(term.getStatus()))return;
        var rules=config(term.getConfigSnapshot()).getRules();var assessment=initialAssessment(wallet,term);String before=encode(term);Integer expected=term.getVersion();
        term.setQualifiedDepositAmount(assessment.netDepositAmount().max(BigDecimal.ZERO));term.setFundingEndLedgerId(allocations.selectWalletLedgerWatermark(wallet.getId(),wallet.getUserId()));
        if(!assessment.qualified()) {
            term.setQualificationStatus("SCHEDULED".equals(term.getStatus())?"NOT_QUALIFIED":"PENDING");term.setQualifiedAt(null);
            saveActivation(term,expected);if("SCHEDULED".equals(term.getStatus()))event(contract,term,"INITIAL_QUALIFICATION_LOST",null,"排程生效前资金资格不足","term_"+term.getId()+"_lost_"+term.getVersion(),before);return;
        }
        LocalDateTime qualifiedAt=assessment.qualifiedAt();
        // 存量政策在首次确认时生效，已确认的延期资格保持原时刻；失效后再达标使用新时刻。
        if("EXISTING_BALANCE".equals(rules.getInitialFundingPolicy())&&"QUALIFIED".equals(term.getQualificationStatus())&&term.getQualifiedAt()!=null)qualifiedAt=term.getQualifiedAt();
        var start=time.startAt(qualifiedAt,rules.getActivationFrom());var end=time.endAt(start,rules.getValidityDays());
        if(terms.countOverlapping(contract.getId(),term.getId(),start,end)>0)throw conflict("权益周期与已有生效期重叠");
        var facts=fundingFacts(wallet,term);
        for(Long ledgerId:assessment.unallocatedLedgerIds()){var fact=facts.stream().filter(v->Objects.equals(v.getLedgerId(),ledgerId)).findFirst().orElseThrow();var allocation=new CoursePrivateFundingAllocation();allocation.setTermId(term.getId());allocation.setWalletId(wallet.getId());allocation.setUserId(wallet.getUserId());allocation.setLedgerId(ledgerId);allocation.setAmount(fact.getAmount());allocation.setCreatedAt(time.now());if(allocations.insert(allocation)!=1)throw conflict("资金资格认领失败");}
        term.setQualifiedAt(qualifiedAt);term.setQualificationBasisAmount(term.getEffectiveInitialDeposit());term.setStartAt(start);term.setEndAt(end);term.setSettlementDeadlineAt(time.settlementDeadline(end,rules.getSettlementGraceDays()));term.setQualificationStatus("QUALIFIED");
        boolean elapsed=end!=null&&!time.now().isBefore(end);boolean active=!elapsed&&!time.now().isBefore(start);term.setStatus(elapsed?"EXPIRED":active?"ACTIVE":"SCHEDULED");term.setActivatedAt(active?time.now():null);saveActivation(term,expected);
        contract.setStatus(elapsed?"EXPIRED":active?"ACTIVE":"PENDING_ACTIVATION");contract.setActivatedAt(term.getActivatedAt());contract.setValidFrom(start);contract.setValidUntil(end);contract.setCurrentTermId(term.getId());saveContract(contract);
        event(contract,term,elapsed?"INITIAL_PERIOD_EXPIRED":active?"ACTIVATED":"INITIAL_QUALIFIED",null,"真实资金满足首次门槛，按发布规则确定生效时间","term_"+term.getId()+"_qualified_"+term.getVersion(),before);
    }

    private CoursePrivateFundingPolicy.Assessment initialAssessment(CourseReservedWallet wallet,CoursePrivateContractTerm term) {
        var rules=config(term.getConfigSnapshot()).getRules();
        if("EXISTING_BALANCE".equals(rules.getInitialFundingPolicy()))return new CoursePrivateFundingPolicy.Assessment(wallet.getBalance(),wallet.getBalance().compareTo(term.getEffectiveInitialDeposit())>=0,time.now(),List.of());
        return CoursePrivateFundingPolicy.assess(new CoursePrivateFundingPolicy.Window(wallet.getId(),wallet.getUserId(),term.getId(),term.getFundingStartLedgerId(),null,term.getFundingWindowStart(),null,time.now()),fundingFacts(wallet,term),term.getEffectiveInitialDeposit());
    }
    private List<CoursePrivateFundingFact> fundingFacts(CourseReservedWallet wallet,CoursePrivateContractTerm term){return allocations.selectFundingFacts(wallet.getId(),wallet.getUserId(),term.getFundingStartLedgerId(),null,term.getFundingWindowStart(),null,time.now());}
    private void saveActivation(CoursePrivateContractTerm term,Integer expected){if(terms.updateActivation(term,expected,time.now())!=1)throw conflict("权益激活版本已变化");term.setVersion(expected+1);term.setUpdatedAt(time.now());}
    private void saveContract(CoursePrivateContract contract){Integer expected=contract.getVersion();if(contracts.updateOperations(contract,expected,time.now())!=1)throw conflict("协议运营版本已变化");contract.setVersion(expected+1);contract.setUpdateTime(time.now());}
    private void requireProduct(CoursePlatform product){if(product==null||!Integer.valueOf(1).equals(product.getStatus()))throw new BusinessException(ResultCode.COURSE_NOT_FOUND);if(product.getCategoryId()!=null){var category=categories.selectById(product.getCategoryId());if(category==null||!Integer.valueOf(1).equals(category.getStatus()))throw new BusinessException(ResultCode.COURSE_NOT_FOUND);}}
    private void requireTermBinding(CoursePrivateContract contract,CoursePrivateContractTerm term){if(term==null||!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),contract.getUserId())||!Objects.equals(term.getPlatformId(),contract.getPlatformId())||!Objects.equals(term.getRelationGeneration(),contract.getRelationGeneration()))throw conflict("权益周期资金关系异常");}
    private CoursePrivatePlanRequest config(String value){try{var request=json.readValue(value,CoursePrivatePlanRequest.class);if(request==null||request.getRules()==null)throw new IllegalArgumentException();return request;}catch(Exception error){throw conflict("权益规则快照损坏，需人工核查");}}
    private boolean legacy(CoursePrivateContract contract){return contract.getOperationsMode()==null||"LEGACY_PERMANENT".equals(contract.getOperationsMode());}
    private void active(User user){if(user==null||!Integer.valueOf(1).equals(user.getStatus()))throw new BusinessException(ResultCode.ACCOUNT_DISABLED);}
    private void event(CoursePrivateContract contract,CoursePrivateContractTerm term,String type,Long operator,String reason,String bizNo,String before){var event=new CoursePrivateTermEvent();event.setContractId(contract.getId());event.setTermId(term.getId());event.setEventType(type);event.setBeforeJson(before);event.setAfterJson(encode(term));event.setTargetVersionId(term.getVersionId());event.setOperatorId(operator);event.setReason(reason);event.setBizNo(bizNo);event.setHappenedAt(time.now());if(events.insert(event)!=1)throw conflict("权益审计事件保存失败");}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw conflict("权益快照保存失败");}}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
    private BusinessException missing(String value){return new BusinessException(ResultCode.NOT_FOUND,value);}
}
