package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CoursePrivatePlanRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** 在一致性快照中核验成交；本金是负债，核验单位成本仅用于估算而不是实际利润。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateAnalyticsService {
    private final CoursePrivateAnalyticsMapper analytics;
    private final CoursePrivateTermOrderMapper orders;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateCostVersionMapper costs;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private static class Group {long periods,orders;BigDecimal revenue=ZERO;}
    private record Lot(BigDecimal amount,LocalDateTime at){}
    private record Age(BigDecimal weightedSeconds,BigDecimal covered,boolean known,boolean mismatch,boolean openingUnknown,boolean sourceUnverified){}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> summary(Long operator,Long platformId,Long planId){
        access.require(operator,"course-private-analytics:read");if(platformId!=null&&platformId<=0||planId!=null&&planId<=0)throw conflict("经营分析筛选参数无效");
        var now=time.now();long cursor=0,activated=0,pending=0,last7=0,last30=0,auto=0,autoEligible=0,upgrades=0,effectiveCount=0,costCovered=0,review=0,legacy=0,pendingOrders=0;
        var activationAgents7=new HashSet<Long>();var activationAgents30=new HashSet<Long>();var upgradedContracts=new HashSet<Long>();var legacyActivatedContracts=new HashSet<Long>();var agents=new HashSet<Long>();var enrolledContracts=new HashSet<Long>();var converted=new HashSet<Long>();var initialActivated=new HashSet<Long>();
        var planGroups=new TreeMap<Long,Group>();var productGroups=new TreeMap<Long,Group>();var channelGroups=new TreeMap<String,Group>();
        BigDecimal revenue=ZERO,estimatedCost=ZERO;
        while(true){var batch=analytics.terms(cursor,platformId,planId);if(batch.isEmpty())break;
            for(var term:batch){cursor=term.getId();agents.add(term.getUserId());enrolledContracts.add(term.getContractId());var byPlan=planGroups.computeIfAbsent(term.getPlanId(),key->new Group());var byProduct=productGroups.computeIfAbsent(term.getPlatformId(),key->new Group());
                var origin=analytics.origin(term.getContractId());String channel=origin==null||origin.getSubmittedBy()==null?"UNKNOWN":origin.getSubmittedBy()==0?"SYSTEM":Objects.equals(origin.getSubmittedBy(),origin.getUserId())?"SELF":"ADMIN";var byChannel=channelGroups.computeIfAbsent(channel,key->new Group());for(var group:List.of(byPlan,byProduct,byChannel))group.periods++;
                if(term.getActivatedAt()!=null&&!term.getActivatedAt().isAfter(now)){if(!term.getActivatedAt().isBefore(now.minusDays(7)))activationAgents7.add(term.getUserId());if(!term.getActivatedAt().isBefore(now.minusDays(30)))activationAgents30.add(term.getUserId());}
                if("LEGACY_PERMANENT".equals(term.getOperationsMode())){legacy++;if(term.getActivatedAt()!=null&&!term.getActivatedAt().isAfter(now))legacyActivatedContracts.add(term.getContractId());continue;}
                if(term.getActivatedAt()!=null){activated++;if(!term.getActivatedAt().isAfter(now)&&!term.getActivatedAt().isBefore(now.minusDays(7)))last7++;if(!term.getActivatedAt().isAfter(now)&&!term.getActivatedAt().isBefore(now.minusDays(30)))last30++;if(term.getRenewalSourceTermId()==null&&!"LEGACY_PERMANENT".equals(term.getOperationsMode()))initialActivated.add(term.getContractId());if("UPGRADE_FUNDS".equals(term.getQualificationKind())){upgrades++;upgradedContracts.add(term.getContractId());}}
                else if(Set.of("PENDING_ACTIVATION","SCHEDULED").contains(term.getStatus()))pending++;
                if(term.getActivatedAt()==null||term.getStartAt()==null)continue;
                var bound=wallets.selectByBinding(term.getUserId(),term.getPlatformId());if(bound==null||!Objects.equals(bound.getContractId(),term.getContractId())){review++;continue;}
                CoursePrivatePlanRequest config;try{config=json.readValue(term.getConfigSnapshot(),CoursePrivatePlanRequest.class);if(config.getRules()==null)throw new IllegalArgumentException();}catch(Exception invalid){review++;continue;}
                var boundary=term.getEndAt();if(term.getSupersededAt()!=null&&(boundary==null||term.getSupersededAt().isBefore(boundary)))boundary=term.getSupersededAt();var deadline=time.settlementDeadline(boundary,config.getRules().getSettlementGraceDays());
                var facts=orders.selectConsumptionFactsReadOnly(term.getId());var assessment=CoursePrivateConsumptionPolicy.assess(term,bound.getId(),facts,now,deadline);if(assessment.reviewRequired())review++;pendingOrders+=assessment.pendingCount();
                if(config.getRules().isRenewalAuto()&&Set.of("PERFORMANCE","NEW_DEPOSIT").contains(config.getRules().getRenewalPolicy())&&boundary!=null&&!now.isBefore(boundary)){autoEligible++;if(analytics.automaticRenewal(term.getId())>0)auto++;}
                var version=versions.selectById(term.getVersionId());var cost=version==null||version.getCostVersionId()==null?null:costs.selectById(version.getCostVersionId());
                boolean verified=cost!=null&&Objects.equals(cost.getPlatformId(),term.getPlatformId())&&cost.getUnitCost()!=null&&cost.getUnitCost().signum()>=0&&cost.getVerifiedBy()!=null&&cost.getVerifiedAt()!=null&&!cost.getVerifiedAt().isAfter(now)&&version.getPublishedAt()!=null&&!cost.getVerifiedAt().isAfter(version.getPublishedAt())&&cost.getEvidence()!=null&&!cost.getEvidence().isBlank();
                for(var row:assessment.rows())if("QUALIFIED".equals(row.status())){effectiveCount++;revenue=revenue.add(row.amount());converted.add(term.getContractId());if(verified){estimatedCost=estimatedCost.add(cost.getUnitCost());costCovered++;}for(var group:List.of(byPlan,byProduct,byChannel)){group.revenue=group.revenue.add(row.amount());group.orders++;}}
            }
        }
        BigDecimal reserve=ZERO,knownAgeCash=ZERO,weightedSeconds=ZERO;boolean allAgesKnown=true;long ledgerMismatch=0,openingUnverified=0,sourceUnverified=0;cursor=0;
        while(true){var batch=analytics.wallets(cursor,platformId,planId);if(batch.isEmpty())break;for(var wallet:batch){cursor=wallet.getId();reserve=reserve.add(wallet.getBalance());var age=age(wallet,now);if(age.mismatch())ledgerMismatch++;if(age.openingUnknown())openingUnverified++;if(age.sourceUnverified())sourceUnverified++;if(age.known()){knownAgeCash=knownAgeCash.add(age.covered());weightedSeconds=weightedSeconds.add(age.weightedSeconds());}else if(wallet.getBalance().signum()>0||age.mismatch())allAgesKnown=false;}}
        converted.retainAll(initialActivated);boolean fullCosts=effectiveCount>0&&costCovered==effectiveCount;
        var result=new LinkedHashMap<String,Object>();result.put("asOf",now);result.put("publishedPlanCount",analytics.publishedPlans(platformId,planId));result.put("enrolledAgentCount",(long)agents.size());result.put("enrolledContractCount",(long)enrolledContracts.size());result.put("activatedPeriodCount",activated);result.put("pendingActivationPeriodCount",pending);result.put("activation7Days",last7);result.put("activation30Days",last30);result.put("activationAgent7Days",(long)activationAgents7.size());result.put("activationAgent30Days",(long)activationAgents30.size());result.put("legacyActivatedContractCount",(long)legacyActivatedContracts.size());result.put("upgradeContractRate",rate(upgradedContracts.size(),enrolledContracts.size()));result.put("firstOrderConvertedContractCount",(long)converted.size());result.put("firstOrderConversionRate",rate(converted.size(),initialActivated.size()));result.put("automaticRenewalCount",auto);result.put("automaticRenewalDuePeriodCount",autoEligible);result.put("automaticRenewalSuccessRate",rate(auto,autoEligible));result.put("upgradeCount",upgrades);result.put("effectiveOrderCount",effectiveCount);result.put("effectiveRevenue",money(revenue));result.put("walletReserve",money(reserve));result.put("walletLedgerMismatchCount",ledgerMismatch);result.put("walletOpeningUnverifiedCount",openingUnverified);result.put("walletSourceUnverifiedCount",sourceUnverified);result.put("walletReconciliationStatus",ledgerMismatch>0?"REVIEW_REQUIRED":openingUnverified>0||sourceUnverified>0?"INSUFFICIENT_EVIDENCE":"BALANCED");result.put("pendingReleaseAmount",money(analytics.pendingRelease(platformId,planId)));result.put("costCoveredOrderCount",costCovered);result.put("verifiedCostEstimate",costCovered>0?money(estimatedCost):null);result.put("estimatedContributionMargin",fullCosts?money(revenue.subtract(estimatedCost)):null);result.put("actualProfit",null);result.put("costStatus",fullCosts?"VERIFIED_ESTIMATE":costCovered>0?"PARTIAL_COVERAGE":"INSUFFICIENT_EVIDENCE");result.put("averageCashAgeDays",allAgesKnown&&reserve.signum()>0?weightedSeconds.divide(reserve.multiply(new BigDecimal("86400")),2,RoundingMode.HALF_UP).toPlainString():null);result.put("cashAgeCoveredAmount",money(knownAgeCash));result.put("cashAgeMethod","FIFO_MODEL_REQUIRES_COMPLETE_LEDGER_NO_REFUND");result.put("reviewRequiredPeriodCount",review);result.put("pendingFulfillmentOrderCount",pendingOrders);result.put("legacyPeriodCount",legacy);result.put("byPlan",groups(planGroups));result.put("byProduct",groups(productGroups));result.put("byChannel",groups(channelGroups));result.put("limitations",List.of("预存本金与待转回金额是资金负债，不能作为营收或利润。","有效成交按原支付及可信履约事实计算，已退款及证据异常的单不计入。","成本使用发布版本绑定的已核验单位成本估算，不等于实际供应商结算或利润。","近7/30天激活代理按拥有者去重；升级率为本筛选内已生效升级的唯一协议数除已签约协议数。","首次转化分母为已激活首期协议，分子为其中至少一笔有效履约的协议。","自动续期成功率分母为已到购买边界并配置自动续期的普通周期；包含尚待结算周期。","资金账龄采用完整账本FIFO模型；退款或未知期初余额不猜测原资金年龄。","旧版缺少周期订单关联的成交未纳入，未知来源渠道单独列示。"));return result;
    }
    private Age age(CourseReservedWallet wallet,LocalDateTime now){
        var lots=new ArrayDeque<Lot>();BigDecimal balance=ZERO;long after=0;boolean any=false,ageKnown=true,mismatch=false,openingUnknown=false,sourceUnverified=false;
        while(true){var batch=analytics.ledgers(wallet.getId(),after);if(batch.isEmpty())break;
            for(var row:batch){after=row.getId();
                if(!Objects.equals(row.getUserId(),wallet.getUserId())||row.getBalanceBefore()==null||row.getBalanceAfter()==null||row.getAmount()==null||row.getAmount().signum()<=0){mismatch=true;ageKnown=false;continue;}
                if(!any){any=true;balance=row.getBalanceBefore();if(balance.signum()!=0){openingUnknown=true;ageKnown=false;}}
                if(row.getBalanceBefore().compareTo(balance)!=0){mismatch=true;ageKnown=false;}
                if(!Boolean.TRUE.equals(row.getVerifiedSource())){sourceUnverified=true;ageKnown=false;}
                if(row.getCreateTime()==null||row.getCreateTime().isAfter(now))ageKnown=false;
                if(Integer.valueOf(1).equals(row.getDirection())){
                    balance=balance.add(row.getAmount());
                    if(!"TRANSFER_IN".equals(row.getBizType()))ageKnown=false;
                    if(ageKnown)lots.add(new Lot(row.getAmount(),row.getCreateTime()));
                }else if(Integer.valueOf(-1).equals(row.getDirection())){
                    balance=balance.subtract(row.getAmount());
                    if(ageKnown){BigDecimal remaining=row.getAmount();while(remaining.signum()>0&&!lots.isEmpty()){var lot=lots.removeFirst();var used=remaining.min(lot.amount());remaining=remaining.subtract(used);if(lot.amount().compareTo(used)>0)lots.addFirst(new Lot(lot.amount().subtract(used),lot.at()));}if(remaining.signum()>0){ageKnown=false;mismatch=true;}}
                }else{mismatch=true;ageKnown=false;}
                if(balance.compareTo(row.getBalanceAfter())!=0){mismatch=true;ageKnown=false;}balance=row.getBalanceAfter();
            }
        }
        // 未记录的历史期初是证据不足；已有流水与账面余额相矛盾才属于明确异常。
        if(!any&&wallet.getBalance().signum()!=0){openingUnknown=true;ageKnown=false;}
        else if(balance.compareTo(wallet.getBalance())!=0){mismatch=true;ageKnown=false;}
        if(!ageKnown)return new Age(ZERO,ZERO,false,mismatch,openingUnknown,sourceUnverified);BigDecimal weighted=ZERO;
        for(var lot:lots)weighted=weighted.add(lot.amount().multiply(BigDecimal.valueOf(Duration.between(lot.at().atZone(time.zone()).toInstant(),now.atZone(time.zone()).toInstant()).getSeconds())));
        return new Age(weighted,balance,true,mismatch,openingUnknown,sourceUnverified);
    }
    private List<Map<String,Object>> groups(Map<?,Group> source){return source.entrySet().stream().map(entry->{var row=new LinkedHashMap<String,Object>();row.put("key",entry.getKey());row.put("periodCount",entry.getValue().periods);row.put("effectiveOrderCount",entry.getValue().orders);row.put("effectiveRevenue",money(entry.getValue().revenue));return (Map<String,Object>)row;}).toList();}
    private String rate(long numerator,long denominator){return denominator==0?null:BigDecimal.valueOf(numerator).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP).toPlainString();}
    private String money(BigDecimal amount){return Objects.requireNonNullElse(amount,ZERO).setScale(2,RoundingMode.UNNECESSARY).toPlainString();}
    private BusinessException conflict(String message){return new BusinessException(ResultCode.CONFLICT,message);}
}
