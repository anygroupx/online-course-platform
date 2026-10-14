package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CoursePrivatePlanRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.vo.CoursePricePreview;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 权威周期读取由原事实结算与统一报价提供，响应白名单不暴露内部用户或完整审计JSON。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateRightsReadService {
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateTermEventMapper events;
    private final CoursePrivateTermSettlementMapper settlements;
    private final CoursePlatformMapper products;
    private final CoursePrivateFundingAllocationMapper funding;
    private CoursePrivateTermOrderMapper associatedOrders;
    @org.springframework.beans.factory.annotation.Autowired
    void configureAssociatedOrders(org.springframework.beans.factory.ObjectProvider<CoursePrivateTermOrderMapper> provider){associatedOrders=provider.getIfAvailable();}
    private final CoursePricingService pricing;
    private final CoursePrivateSettlementService settlement;
    private final CoursePrivateRenewalService renewal;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @org.springframework.beans.factory.annotation.Value("${course.private-operations.enabled:false}") private boolean enabled;

    public Map<String,Object> contracts(Long selfId,int page,int pageSize,Long platformId,String status,String userUid) {
        authorize(selfId);CoursePrivatePlanService.page(page,pageSize);
        if(platformId!=null&&platformId<=0||status!=null&&!status.isBlank()&&!Set.of("PENDING_ACTIVATION","ACTIVE","SUSPENDED","EXPIRED","TERMINATED").contains(status))throw conflict("权益查询条件无效");
        Long owner=selfId;
        if(selfId==null&&userUid!=null&&!userUid.isBlank()){try{if(!UUID.fromString(userUid).toString().equalsIgnoreCase(userUid))throw new IllegalArgumentException();}catch(IllegalArgumentException error){throw conflict("代理UUID格式无效");}var user=users.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("uid",userUid));owner=user==null?-1L:user.getId();}
        String filter=status==null||status.isBlank()?null:status;var rows=contracts.selectOperationsPage(owner,platformId,filter,pageSize,(long)(page-1)*pageSize);
        return Map.of("records",rows.stream().map(v->summary(v,selfId==null)).toList(),"total",contracts.countOperations(owner,platformId,filter),"current",page,"size",pageSize);
    }

    public Map<String,Object> periods(int page,int pageSize,Long platformId,String status,String userUid){
        authorize(null);CoursePrivatePlanService.page(page,pageSize);
        if(platformId!=null&&platformId<=0||status!=null&&!status.isBlank()&&!Set.of("PENDING_ACTIVATION","SCHEDULED","ACTIVE","PENDING_SETTLEMENT","QUALIFIED","RENEWED","EXPIRED","CANCELLED","SUPERSEDED","SUSPENDED","TERMINATED").contains(status))throw conflict("周期查询条件无效");
        Long owner=null;if(userUid!=null&&!userUid.isBlank()){try{if(!UUID.fromString(userUid).toString().equalsIgnoreCase(userUid))throw new IllegalArgumentException();}catch(IllegalArgumentException error){throw conflict("代理UUID格式无效");}var user=users.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("uid",userUid));owner=user==null?-1L:user.getId();}
        String filter=status==null||status.isBlank()?null:status;var rows=terms.selectOperationsPage(owner,platformId,filter,pageSize,(long)(page-1)*pageSize);var records=new ArrayList<Map<String,Object>>();
        for(var term:rows){var contract=contracts.selectById(term.getContractId());binding(contract,term);var record=summary(contract,true);record.put("termId",term.getId());record.put("currentTerm",termView(term,contract,null,"QUOTE_REQUIRED"));record.put("fixedPrice",money(term.getEffectiveFixedPrice()));record.put("validFrom",term.getStartAt());record.put("validUntil",term.getEndAt());records.add(record);}
        return Map.of("records",records,"total",terms.countOperations(owner,platformId,filter),"current",page,"size",pageSize);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> detail(Long selfId,Long termId) {
        authorize(selfId);var initial=terms.selectById(termId);if(initial==null||selfId!=null&&!Objects.equals(selfId,initial.getUserId()))throw missing();
        var user=users.selectByIdForUpdate(initial.getUserId());if(user==null)throw missing();var contract=contracts.selectByIdForUpdate(initial.getContractId());var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw conflict("原课程专户不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());
        var term=terms.selectByIdForUpdate(termId);binding(contract,term);
        if(!legacy(term)&&term.getActivatedAt()!=null)settlement.evaluate(termId);
        CoursePricePreview quote=null;String unavailable="ACCOUNT_DISABLED";
        if(Integer.valueOf(1).equals(user.getStatus())){boolean ready=true;try{pricing.requirePlatform(contract.getPlatformId());}catch(BusinessException error){ready=false;unavailable="COURSE_UNAVAILABLE";}if(ready){quote=pricing.preview(contract.getUserId(),contract.getPlatformId());unavailable=quote.unavailableReason();}}
        contract=contracts.selectById(contract.getId());term=terms.selectById(termId);
        var response=new LinkedHashMap<String,Object>();response.put("contract",summary(contract,selfId==null));response.put("term",termView(term,contract,quote,unavailable));response.put("walletBalance",money(wallet.getBalance()));response.put("asOf",time.now());
        var next=terms.selectByRenewalSource(termId);response.put("nextTerm",next==null?null:termView(next,contract,quote,unavailable));
        var history=terms.selectRecentByContract(contract.getId(),100);var current=contract;var currentQuote=quote;var currentUnavailable=unavailable;response.put("history",history.stream().map(v->termView(v,current,currentQuote,currentUnavailable)).toList());response.put("historyMayHaveMore",history.size()==100);
        var latest=settlements.selectLatestByTerm(termId);response.put("latestSettlement",latest==null?null:Map.of("id",latest.getId(),"revision",latest.getRevisionNo(),"type",latest.getEvaluationType(),"result",latest.getResult(),"amount",money(latest.getSettledAmount()),"orderCount",latest.getOrderCount(),"pendingCount",latest.getPendingCount(),"evaluatedAt",latest.getEvaluatedAt()));
        response.put("events",events.selectRecentByTerm(termId,50).stream().map(this::eventView).toList());return response;
    }

    /** 原订单分页先校验拥有者并核验事实，输出只含安全财务关联字段。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> orders(Long selfId,Long termId,int page,int pageSize){
        if(page<1||page>100000||pageSize<1||pageSize>100)throw conflict("订单明细分页参数无效");detail(selfId,termId);if(associatedOrders==null)throw conflict("周期订单读取服务不可用");
        var records=associatedOrders.selectByTermPage(termId,(long)(page-1)*pageSize,pageSize).stream().map(row->{var value=new LinkedHashMap<String,Object>();value.put("orderNo",row.getOrderNo());value.put("orderedAmount",money(row.getOrderedAmount()));value.put("qualifiedAmount",money(row.getQualifiedAmount()));value.put("qualificationStatus",row.getQualificationStatus());value.put("paidAt",row.getPaidAt());value.put("completedAt",row.getCompletedAt());value.put("completionConfirmedAt",row.getCompletionConfirmedAt());value.put("completionBasis",row.getCompletionBasis());value.put("refundedAt",row.getRefundedAt());return value;}).toList();return Map.of("records",records,"total",associatedOrders.countByTerm(termId),"page",page,"pageSize",pageSize);
    }

    private Map<String,Object> summary(CoursePrivateContract contract,boolean admin) {
        var wallet=wallets.selectByBinding(contract.getUserId(),contract.getPlatformId());CoursePricingService.requireBinding(contract,wallet,contract.getUserId(),contract.getPlatformId());var result=new LinkedHashMap<String,Object>();result.put("contractId",contract.getId());result.put("contractVersion",contract.getVersion());result.put("platformId",contract.getPlatformId());var product=products.selectById(contract.getPlatformId());result.put("platformName",product==null?"历史课程商品":product.getDisplayName()!=null?product.getDisplayName():product.getName()!=null?product.getName():"课程商品");result.put("walletId",wallet.getId());result.put("walletBalance",money(wallet.getBalance()));result.put("fixedPrice",money(contract.getFixedPrice()));result.put("initialDepositAmount",money(contract.getMinInitialDeposit()));result.put("contractStatus",contract.getStatus());result.put("operationsMode",Objects.requireNonNullElse(contract.getOperationsMode(),"LEGACY_PERMANENT"));result.put("currentTermId",contract.getCurrentTermId());result.put("validFrom",contract.getValidFrom());result.put("validUntil",contract.getValidUntil());result.put("relationGeneration",contract.getRelationGeneration());
        var current=contract.getCurrentTermId()==null?null:terms.selectById(contract.getCurrentTermId());result.put("currentTerm",current==null?null:termView(current,contract,null,"QUOTE_REQUIRED"));if(admin){var user=users.selectById(contract.getUserId());result.put("userUid",user==null?null:user.getUid());result.put("username",user==null?null:user.getUsername());}return result;
    }
    private Map<String,Object> termView(CoursePrivateContractTerm term,CoursePrivateContract contract,CoursePricePreview quote,String unavailable) {
        boolean legacy=legacy(term);CoursePrivatePlanRequest config=legacy?null:config(term.getConfigSnapshot());var result=new LinkedHashMap<String,Object>();result.put("id",term.getId());result.put("termNo",term.getTermNo());result.put("version",term.getVersion());result.put("planId",term.getPlanId());result.put("planVersionId",term.getVersionId());result.put("planName",legacy?"旧版兼容协议":config.getPlanName());result.put("operationsMode",term.getOperationsMode());result.put("familyCode",legacy?null:config.getFamilyCode());result.put("tierCode",term.getTierCode());result.put("tierRank",term.getTierRank());result.put("status",term.getStatus());result.put("qualificationStatus",term.getQualificationStatus());result.put("qualificationKind",term.getQualificationKind());result.put("fixedPrice",money(term.getEffectiveFixedPrice()));result.put("initialDepositAmount",money(term.getEffectiveInitialDeposit()));result.put("qualificationBasisAmount",legacy&&"LEGACY_UNKNOWN".equals(term.getQualificationKind())?null:money(term.getQualificationBasisAmount()));result.put("qualifiedDepositAmount",legacy?null:money(term.getQualifiedDepositAmount()));result.put("renewalNetDepositAmount",legacy?null:money(term.getRenewalNetDepositAmount()));result.put("settledConsumptionAmount",legacy?null:money(term.getSettledConsumptionAmount()));result.put("settledOrderCount",legacy?null:term.getSettledOrderCount());result.put("reviewRequired",term.getReviewRequired());result.put("startAt",term.getStartAt());result.put("endAt",term.getEndAt());result.put("supersededAt",term.getSupersededAt());result.put("finalizedAt",term.getFinalizedAt());result.put("activatedAt",term.getActivatedAt());result.put("qualifiedAt",term.getQualifiedAt());result.put("renewalSourceTermId",term.getRenewalSourceTermId());result.put("relationGeneration",term.getRelationGeneration());
        LocalDateTime end=term.getEndAt();if(term.getSupersededAt()!=null&&(end==null||term.getSupersededAt().isBefore(end)))end=term.getSupersededAt();result.put("effectiveEndAt",end);result.put("effectiveSettlementDeadlineAt",legacy?term.getSettlementDeadlineAt():time.settlementDeadline(end,config.getRules().getSettlementGraceDays()));result.put("rules",legacy?null:config.getRules());
        BigDecimal target=legacy?null:config.getRules().getRenewalConsumptionTarget();result.put("remainingConsumption",legacy?null:money(target.subtract(Objects.requireNonNullElse(term.getSettledConsumptionAmount(),BigDecimal.ZERO)).max(BigDecimal.ZERO)));
        boolean firstPending="INITIAL_FUNDS".equals(term.getQualificationKind())&&term.getActivatedAt()==null&&Set.of("PENDING_ACTIVATION","SCHEDULED").contains(term.getStatus());
        result.put("remainingInitialDeposit",legacy?null:money(firstPending?term.getEffectiveInitialDeposit().subtract(Objects.requireNonNullElse(term.getQualifiedDepositAmount(),BigDecimal.ZERO)).max(BigDecimal.ZERO):BigDecimal.ZERO));
        boolean current=Objects.equals(contract.getCurrentTermId(),term.getId())&&Objects.equals(contract.getRelationGeneration(),term.getRelationGeneration());boolean can=quote!=null&&quote.canPurchase()&&current&&(legacy||Objects.equals(quote.privateTermId(),term.getId()));result.put("isCurrent",current);result.put("canPurchase",can);result.put("unavailableReason",can?null:"SCHEDULED".equals(term.getStatus())?"SCHEDULED_PENDING_VERIFICATION":current?unavailable:"HISTORICAL_TERM");
        boolean accepting=enabled&&current&&!Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus());result.put("newApplicationsEnabled",enabled);
        boolean renewable=!legacy&&term.getEndAt()!=null&&!"NONE".equals(config.getRules().getRenewalPolicy());
        var from=renewable?time.endAt(term.getEndAt(),-config.getRules().getRenewalBeforeDays()):null;var until=renewable?time.endAt(term.getEndAt(),config.getRules().getRenewalAfterDays()):null;
        result.put("renewalWindowStart",from);result.put("renewalWindowEnd",until);result.put("canRequestRenewal",accepting&&renewable&&!time.now().isBefore(from)&&!time.now().isAfter(until));
        result.put("renewalOffer",renewable?renewal.offer(term):null);result.put("canRequestUpgrade",accepting&&(legacy||term.getActivatedAt()!=null&&config.getFamilyCode()!=null&&!config.getFamilyCode().isBlank()&&!"DISABLED".equals(config.getRules().getUpgradePolicy()))&&terms.selectByRenewalSource(term.getId())==null);
        // 未生效升级的金额只按原差额/全额/存量政策读取，不能把目标N误报为额外欠款。
        if(!legacy&&"UPGRADE_FUNDS".equals(term.getQualificationKind())&&"SCHEDULED".equals(term.getStatus())){
            var origin=terms.selectById(term.getRenewalSourceTermId());binding(contract,origin);var policy=legacy(origin)?config.getRules():config(origin.getConfigSnapshot()).getRules();var wallet=wallets.selectByBinding(term.getUserId(),term.getPlatformId());
            BigDecimal required="DIFFERENCE".equals(policy.getUpgradeDepositPolicy())?term.getEffectiveInitialDeposit().subtract(Objects.requireNonNullElse(origin.getQualificationBasisAmount(),BigDecimal.ZERO)).max(BigDecimal.ZERO):term.getEffectiveInitialDeposit();
            BigDecimal eligible;
            if("EXISTING_BALANCE".equals(policy.getUpgradeDepositPolicy()))eligible=wallet.getBalance();
            else {var facts=funding.selectFundingFacts(wallet.getId(),wallet.getUserId(),term.getFundingStartLedgerId(),null,term.getFundingWindowStart(),null,time.now());eligible=CoursePrivateFundingPolicy.assess(new CoursePrivateFundingPolicy.Window(wallet.getId(),wallet.getUserId(),term.getId(),term.getFundingStartLedgerId(),null,term.getFundingWindowStart(),null,time.now()),facts,required).netDepositAmount();}
            result.put("upgradeDepositPolicy",policy.getUpgradeDepositPolicy());result.put("upgradeRequiredDeposit",money(required));result.put("upgradeEligibleAmount",money(eligible));result.put("remainingUpgradeDeposit",money(required.subtract(eligible).max(BigDecimal.ZERO)));result.put("upgradeQualificationReady",eligible.compareTo(required)>=0);
        }
        return result;
    }
    private Map<String,Object> eventView(CoursePrivateTermEvent event){var view=new LinkedHashMap<String,Object>();view.put("id",event.getId());view.put("type",event.getEventType());view.put("reason",event.getReason());view.put("happenedAt",event.getHappenedAt());view.put("sourceVersionId",event.getSourceVersionId());view.put("targetVersionId",event.getTargetVersionId());var actor=event.getOperatorId()==null||event.getOperatorId()==0?null:users.selectById(event.getOperatorId());view.put("operatorUid",actor==null?null:actor.getUid());view.put("operatorName",event.getOperatorId()==null||event.getOperatorId()==0?"系统":actor==null?"历史操作人":actor.getUsername());return view;}
    private void authorize(Long selfId){if(selfId==null)access.require(SecurityUtils.getCurrentUserId(),"course-private-term:read");else access.self(selfId);}
    private void binding(CoursePrivateContract contract,CoursePrivateContractTerm term){if(term==null||!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),contract.getUserId())||!Objects.equals(term.getPlatformId(),contract.getPlatformId()))throw conflict("历史周期与原资金关系绑定异常");}
    private boolean legacy(CoursePrivateContractTerm term){return "LEGACY_PERMANENT".equals(term.getOperationsMode());}
    private String money(BigDecimal amount){return amount==null?null:amount.setScale(2,java.math.RoundingMode.UNNECESSARY).toPlainString();}
    private CoursePrivatePlanRequest config(String value){try{var request=json.readValue(value,CoursePrivatePlanRequest.class);if(request.getRules()==null)throw new IllegalArgumentException();return request;}catch(Exception error){throw conflict("权益规则快照损坏，需人工核查");}}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
    private BusinessException missing(){return new BusinessException(ResultCode.NOT_FOUND,"本人权益周期或原拥有者不存在");}
}
