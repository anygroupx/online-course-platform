package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** 按原支付事实重算消费及续期资格，逐次结果不可覆盖，不在资金锁内调用外部服务。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateSettlementService {
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateTermOrderMapper orders;
    private final CoursePrivateTermSettlementMapper settlements;
    private final CoursePrivateFundingAllocationMapper funding;
    private final CoursePrivateTermEventMapper events;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    private CoursePrivateOperationsAccess reviewAccess;
    @org.springframework.beans.factory.annotation.Autowired
    void configureReviewAccess(org.springframework.beans.factory.ObjectProvider<CoursePrivateOperationsAccess> provider){reviewAccess=provider.getIfAvailable();}
    public record ReviewResult(Long termId,Long settlementId,Integer termVersion,String result){}
    private record SavedReview(String fingerprint,ReviewResult result,String evidence){}

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateTermSettlement evaluate(Long termId) {
        var initial=terms.selectById(termId);if(initial==null)throw missing();
        if(users.selectByIdForUpdate(initial.getUserId())==null)throw missing();
        var contract=contracts.selectByIdForUpdate(initial.getContractId());var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw conflict("原结算钱包不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());
        var term=terms.selectByIdForUpdate(termId);if(term==null||!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),initial.getUserId()))throw conflict("周期结算绑定异常");
        if("LEGACY_PERMANENT".equals(term.getOperationsMode()))throw conflict("旧版兼容权益不执行现代周期结算");
        if(term.getStartAt()==null)return settlements.selectLatestByTerm(termId);
        var config=config(term.getConfigSnapshot());var rules=config.getRules();
        var boundary=term.getEndAt();if(term.getSupersededAt()!=null&&(boundary==null||term.getSupersededAt().isBefore(boundary)))boundary=term.getSupersededAt();var deadline=time.settlementDeadline(boundary,rules.getSettlementGraceDays());
        var facts=orders.selectConsumptionFacts(termId);var consumptionTerm=new CoursePrivateContractTerm();org.springframework.beans.BeanUtils.copyProperties(term,consumptionTerm);consumptionTerm.setRenewalTarget("PERFORMANCE".equals(rules.getRenewalPolicy())?rules.getRenewalConsumptionTarget():BigDecimal.ZERO);
        var assessment=CoursePrivateConsumptionPolicy.assess(consumptionTerm,wallet.getId(),facts,time.now(),deadline);
        BigDecimal netDeposit=Objects.requireNonNullElse(term.getRenewalNetDepositAmount(),BigDecimal.ZERO);String result=assessment.result();var qualifiedAt=assessment.qualifiedAt();List<CoursePrivateFundingFact> depositFacts=List.of();
        if("NEW_DEPOSIT".equals(rules.getRenewalPolicy())) {
            depositFacts=funding.selectFundingFacts(wallet.getId(),wallet.getUserId(),term.getFundingEndLedgerId(),null,term.getStartAt(),boundary,time.now());
            var deposit=CoursePrivateFundingPolicy.assess(new CoursePrivateFundingPolicy.Window(wallet.getId(),wallet.getUserId(),term.getId(),term.getFundingEndLedgerId(),null,term.getStartAt(),boundary,time.now()),depositFacts,rules.getRenewalDepositAmount());netDeposit=deposit.netDepositAmount();qualifiedAt=deposit.qualifiedAt();
            result=assessment.reviewRequired()?"REVIEW_REQUIRED":deposit.qualified()?"QUALIFIED":assessment.finalizable()?"NOT_QUALIFIED":"PENDING";
        } else if("MANUAL".equals(rules.getRenewalPolicy()))result=assessment.reviewRequired()?"REVIEW_REQUIRED":assessment.finalizable()?"NOT_QUALIFIED":"PENDING";
        var previous=settlements.selectLatestByTerm(termId);
        boolean changed=previous!=null&&(previous.getSettledAmount().compareTo(assessment.amount())!=0||previous.getOrderCount()!=assessment.orderCount());
        boolean finalized=term.getFinalizedAt()!=null;
        // 终局后只修正原净事实并标记复核，不能默默撤回已经执行的价格或给新周期重新计价。
        boolean review=Boolean.TRUE.equals(term.getReviewRequired())||assessment.reviewRequired()||finalized&&changed;
        if(review)result="REVIEW_REQUIRED";
        String evaluationType=assessment.finalizable()?"FINAL":"PREVIEW";
        if(finalized&&changed)evaluationType=facts.stream().anyMatch(v->Boolean.TRUE.equals(v.getRefundExists()))?"REFUND_REVIEW":"FACT_REVIEW";
        else if(finalized&&review&&previous!=null&&Set.of("REFUND_REVIEW","FACT_REVIEW").contains(previous.getEvaluationType()))evaluationType=previous.getEvaluationType();
        String phase=boundary==null||time.now().isBefore(boundary)?"ACTIVE":deadline!=null&&!time.now().isBefore(deadline)?"DEADLINE":"GRACE";
        String fingerprint=digest(Arrays.asList(termId,phase,evaluationType,result,assessment.rows(),assessment.amount(),assessment.orderCount(),assessment.pendingCount(),netDeposit,depositFacts));
        var existing=settlements.selectByFingerprint(termId,fingerprint);if(existing!=null)return existing;
        String before=encode(term);Integer expected=term.getVersion();
        for(var row:assessment.rows()){var projection=new CoursePrivateTermOrder();projection.setId(row.id());projection.setTermId(termId);projection.setQualifiedAmount(row.amount());projection.setQualificationStatus(row.status());projection.setCompletedAt(row.completedAt());projection.setCompletionConfirmedAt(row.confirmedAt());projection.setCompletionBasis(row.basis());projection.setRefundedAt(row.refundedAt());if(orders.reconcile(projection,time.now())!=1)throw conflict("原订单结算投影保存失败");}
        BigDecimal old=Objects.requireNonNullElse(term.getSettledConsumptionAmount(),BigDecimal.ZERO);term.setSettledConsumptionAmount(assessment.amount());term.setSettledOrderCount(assessment.orderCount());term.setRenewalNetDepositAmount(netDeposit);term.setAdjustmentAmount(Objects.requireNonNullElse(term.getAdjustmentAmount(),BigDecimal.ZERO).add(finalized?assessment.amount().subtract(old):BigDecimal.ZERO));term.setQualificationStatus(result);term.setReviewRequired(review);
        if("PERFORMANCE".equals(rules.getRenewalPolicy())||"NEW_DEPOSIT".equals(rules.getRenewalPolicy()))term.setQualifiedAt("QUALIFIED".equals(result)?qualifiedAt:review?term.getQualifiedAt():null);
        if(assessment.finalizable()&&!finalized)term.setFinalizedAt(time.now());
        if(boundary!=null&&!time.now().isBefore(boundary)&&!Set.of("CANCELLED","SUPERSEDED","TERMINATED","RENEWED").contains(term.getStatus()))term.setStatus(!assessment.finalizable()?"PENDING_SETTLEMENT":"QUALIFIED".equals(result)?"QUALIFIED":"EXPIRED");
        if(terms.updateEvaluation(term,expected,time.now())!=1)throw conflict("周期结算版本已变化");term.setVersion(expected+1);
        var value=new CoursePrivateTermSettlement();value.setTermId(termId);value.setRevisionNo(previous==null?1:previous.getRevisionNo()+1);value.setEvaluationType(evaluationType);value.setSettledAmount(assessment.amount());value.setOrderCount(assessment.orderCount());value.setTargetAmount(term.getRenewalTarget());value.setPendingCount(assessment.pendingCount());value.setResult(result);value.setEvaluatedAt(time.now());value.setCreatedAt(time.now());value.setOperationFingerprint(fingerprint);if(settlements.insert(value)!=1)throw conflict("不可变结算结果保存失败");
        var next=terms.selectByRenewalSource(termId);
        if(next!=null&&"SCHEDULED".equals(next.getStatus())&&Set.of("PERFORMANCE","NEW_DEPOSIT").contains(Objects.toString(next.getQualificationKind(),""))){String nextResult="QUALIFIED".equals(result)?"QUALIFIED":review?"REVIEW_REQUIRED":"PENDING";if(!Objects.equals(next.getQualificationStatus(),nextResult)||Boolean.TRUE.equals(next.getReviewRequired())!=review){Integer nextVersion=next.getVersion();next.setQualificationStatus(nextResult);next.setReviewRequired(review);if(terms.updateEvaluation(next,nextVersion,time.now())!=1)throw conflict("下一期排程资格核验冲突");}}
        if(boundary!=null&&!time.now().isBefore(boundary)&&Objects.equals(contract.getCurrentTermId(),termId)&&Set.of("ACTIVE","PENDING_ACTIVATION").contains(contract.getStatus())){Integer version=contract.getVersion();contract.setStatus("EXPIRED");contract.setUpdatedBy(0L);if(contracts.updateOperations(contract,version,time.now())!=1)throw conflict("期末协议状态保存失败");}
        var event=new CoursePrivateTermEvent();event.setContractId(term.getContractId());event.setTermId(termId);event.setEventType("SETTLEMENT_"+evaluationType);event.setBeforeJson(before);event.setAfterJson(encode(Map.of("term",term,"rows",assessment.rows(),"funding",depositFacts,"effectiveDeadline",Objects.toString(deadline,"PERMANENT"))));event.setTargetVersionId(term.getVersionId());event.setReason(review?"原支付事实变化，修正净有效消费并保留已执行权益待运营复核":"按原支付、履约时间及退款事实结算");event.setBizNo("settlement_"+termId+"_"+value.getRevisionNo());event.setHappenedAt(time.now());if(events.insert(event)!=1)throw conflict("结算审计事件保存失败");
        return value;
    }

    /** 人工确认当前净事实，只解除已查明的复核标记，不把退款后的事实改成历史达标。 */
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ReviewResult resolveReview(Long termId,Integer expectedVersion,Long expectedSettlementId,Long operator,String reason,String evidence,String requestKey){
        if(reviewAccess==null)throw conflict("复核权限服务不可用");reviewAccess.require(operator,"course-private-term:adjust");reviewAccess.require(operator,"course-private-settlement:execute");CoursePrivatePlanService.reason(reason);
        if(expectedVersion==null||expectedVersion<0||expectedSettlementId==null||expectedSettlementId<=0||evidence==null||evidence.isBlank()||evidence.length()>2000||requestKey==null||!requestKey.matches("[A-Za-z0-9_-]{1,64}"))throw conflict("复核须提供原版本、结算记录和明确核验依据");
        var initial=terms.selectById(termId);if(initial==null)throw missing();if(users.selectByIdForUpdate(initial.getUserId())==null)throw missing();var contract=contracts.selectByIdForUpdate(initial.getContractId());var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw missing();var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());var term=terms.selectByIdForUpdate(termId);
        String identity="review_"+digest(Arrays.asList(termId,operator,requestKey));String requestFingerprint=digest(Arrays.asList(termId,operator,expectedVersion,expectedSettlementId,reason.trim(),evidence.trim()));var prior=events.selectByBizNo(identity);
        if(prior!=null){try{var saved=json.readValue(prior.getAfterJson(),SavedReview.class);if(!saved.fingerprint().equals(requestFingerprint))throw conflict("复核幂等键内容冲突");return saved.result();}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw conflict("原复核响应损坏，需核查");}}
        if(term==null||!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),contract.getUserId())||!Objects.equals(term.getPlatformId(),contract.getPlatformId())||"LEGACY_PERMANENT".equals(term.getOperationsMode()))throw conflict("复核周期绑定异常或不属于现代结算");
        var previous=settlements.selectLatestByTerm(termId);if(!Objects.equals(term.getVersion(),expectedVersion)||previous==null||!Objects.equals(previous.getId(),expectedSettlementId)||!Boolean.TRUE.equals(term.getReviewRequired())||term.getFinalizedAt()==null)throw conflict("复核版本或结算事实已变化，请刷新原周期");
        evaluate(termId);term=terms.selectByIdForUpdate(termId);var latest=settlements.selectLatestByTerm(termId);if(!Objects.equals(latest.getId(),expectedSettlementId)||!Objects.equals(term.getVersion(),expectedVersion))throw conflict("提交后发现新事实，请重新核对结算记录");
        var rules=config(term.getConfigSnapshot()).getRules();var boundary=term.getEndAt();if(term.getSupersededAt()!=null&&(boundary==null||term.getSupersededAt().isBefore(boundary)))boundary=term.getSupersededAt();var assessment=CoursePrivateConsumptionPolicy.assess(term,wallet.getId(),orders.selectConsumptionFacts(termId),time.now(),time.settlementDeadline(boundary,rules.getSettlementGraceDays()));
        if(assessment.reviewRequired()||!assessment.finalizable())throw conflict("原扣款、退款或履约证据仍有异常或待定，不能解除复核");
        String result=switch(rules.getRenewalPolicy()){case "PERFORMANCE"->assessment.amount().compareTo(rules.getRenewalConsumptionTarget())>=0?"QUALIFIED":"NOT_QUALIFIED";case "NEW_DEPOSIT"->Objects.requireNonNullElse(term.getRenewalNetDepositAmount(),BigDecimal.ZERO).compareTo(rules.getRenewalDepositAmount())>=0?"QUALIFIED":"NOT_QUALIFIED";case "MANUAL"->"NOT_QUALIFIED";default->"QUALIFIED";};
        String before=encode(term);term.setReviewRequired(false);term.setQualificationStatus(result);if(terms.updateEvaluation(term,expectedVersion,time.now())!=1)throw conflict("复核状态保存冲突");
        // 尚未生效的普通续期继续遵守事实资格；已生效下一期与独立补授、升级保持原依据。
        var next=terms.selectByRenewalSource(termId);if(next!=null&&"SCHEDULED".equals(next.getStatus())&&Set.of("PERFORMANCE","NEW_DEPOSIT").contains(Objects.toString(next.getQualificationKind(),""))){Integer version=next.getVersion();next.setReviewRequired(false);next.setQualificationStatus(result);if(terms.updateEvaluation(next,version,time.now())!=1)throw conflict("待生效续期复核保存冲突");}
        var manual=new CoursePrivateTermSettlement();manual.setTermId(termId);manual.setRevisionNo(latest.getRevisionNo()+1);manual.setEvaluationType("MANUAL");manual.setSettledAmount(latest.getSettledAmount());manual.setOrderCount(latest.getOrderCount());manual.setTargetAmount(latest.getTargetAmount());manual.setPendingCount(latest.getPendingCount());manual.setResult(result);manual.setEvaluatedAt(time.now());manual.setCreatedAt(time.now());manual.setOperationFingerprint(requestFingerprint);if(settlements.insert(manual)!=1)throw conflict("人工复核结算记录失败");
        var response=new ReviewResult(termId,manual.getId(),expectedVersion+1,result);var event=new CoursePrivateTermEvent();event.setContractId(contract.getId());event.setTermId(termId);event.setEventType("SETTLEMENT_REVIEW_RESOLVED");event.setBeforeJson(before);event.setAfterJson(encode(new SavedReview(requestFingerprint,response,evidence.trim())));event.setOperatorId(operator);event.setReason(reason.trim());event.setBizNo(identity);event.setHappenedAt(time.now());if(events.insert(event)!=1)throw conflict("人工复核审计失败");return response;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateTermSettlement reconcileOrder(Long orderId,String reason){var association=orders.selectByOrderId(orderId);return association==null?null:evaluate(association.getTermId());}
    private CoursePrivatePlanRequest config(String value){try{var request=json.readValue(value,CoursePrivatePlanRequest.class);if(request.getRules()==null)throw new IllegalArgumentException();return request;}catch(Exception error){throw conflict("结算规则快照损坏，需人工核查");}}
    private String digest(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(Exception error){throw conflict("结算事实摘要生成失败");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw conflict("结算证据保存失败");}}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
    private BusinessException missing(){return new BusinessException(ResultCode.NOT_FOUND,"权益周期或原拥有者不存在");}
}
