package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CoursePrivateOrderFact;
import com.course.platform.domain.entity.CoursePrivateContractTerm;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 有效消费只解释原支付及履约证据，不改价、不补扣、不根据课程日期猜测完成。 */
public final class CoursePrivateConsumptionPolicy {
    private CoursePrivateConsumptionPolicy(){}
    private static final Set<String> EXACT_SOURCES=Set.of("UPSTREAM_CONFIRMED","SELF_OPERATED_COMPLETED","ADMIN_VERIFIED");
    public record Row(Long id,Long orderId,String status,BigDecimal amount,LocalDateTime completedAt,LocalDateTime confirmedAt,String basis,LocalDateTime refundedAt,boolean reviewRequired,String reason){}
    public record Assessment(BigDecimal amount,int orderCount,int pendingCount,boolean finalizable,boolean reviewRequired,String result,LocalDateTime qualifiedAt,List<Row> rows){}

    public static Assessment assess(CoursePrivateContractTerm term,Long walletId,List<CoursePrivateOrderFact> facts,LocalDateTime asOf,LocalDateTime deadline) {
        if(term==null||term.getStartAt()==null||walletId==null||asOf==null||term.getRenewalTarget()==null)throw conflict("消费结算上下文无效");
        var ids=new HashSet<Long>();var rows=new ArrayList<Row>();BigDecimal amount=new BigDecimal("0.00");int count=0,pending=0;boolean review=false;LocalDateTime qualifiedAt=null;
        var ordered=new ArrayList<>(Objects.requireNonNull(facts));ordered.sort(Comparator.comparing(CoursePrivateOrderFact::getOrderId,Comparator.nullsFirst(Long::compareTo)));
        for(var fact:ordered){if(fact==null||fact.getOrderId()==null||!ids.add(fact.getOrderId()))throw conflict("结算订单身份缺失或重复");var row=row(term,walletId,fact,asOf,deadline);rows.add(row);review|=row.reviewRequired();if("PENDING".equals(row.status()))pending++;
            if("QUALIFIED".equals(row.status())){amount=amount.add(row.amount());count++;var proof=row.completedAt()!=null?row.completedAt():row.confirmedAt();if(qualifiedAt==null||proof.isAfter(qualifiedAt))qualifiedAt=proof;}}
        LocalDateTime boundary=term.getEndAt();if(term.getSupersededAt()!=null&&(boundary==null||term.getSupersededAt().isBefore(boundary)))boundary=term.getSupersededAt();
        // 即使本期零单或现有订单都完成，也必须等购买期真正结束才能终局。
        boolean finalizable=boundary!=null&&!asOf.isBefore(boundary)&&(pending==0||deadline!=null&&!asOf.isBefore(deadline));
        boolean qualified=amount.compareTo(term.getRenewalTarget())>=0;
        String result=review?"REVIEW_REQUIRED":qualified?"QUALIFIED":finalizable?"NOT_QUALIFIED":"PENDING";
        return new Assessment(amount,count,pending,finalizable,review,result,qualified?qualifiedAt:null,List.copyOf(rows));
    }

    private static Row row(CoursePrivateContractTerm term,Long walletId,CoursePrivateOrderFact fact,LocalDateTime asOf,LocalDateTime deadline) {
        boolean bound=Objects.equals(term.getId(),fact.getTermId())&&Objects.equals(term.getContractId(),fact.getContractId())&&Objects.equals(term.getUserId(),fact.getUserId())&&Objects.equals(term.getPlatformId(),fact.getPlatformId())&&Objects.equals(term.getUserId(),fact.getOrderUserId())&&Objects.equals(term.getPlatformId(),fact.getOrderPlatformId())&&Objects.equals(term.getContractId(),fact.getOrderContractId())&&Objects.equals(walletId,fact.getOrderWalletId())&&Objects.equals(term.getId(),fact.getOrderTermId())&&Objects.equals(term.getVersionId(),fact.getOrderPlanVersionId())&&Objects.equals(term.getOperationsMode(),fact.getOrderOperationsMode())&&"PRIVATE".equals(fact.getOrderPriceSource())&&"COURSE_WALLET".equals(fact.getOrderPaymentSource());
        if(!bound||fact.getOrderedAmount()==null||fact.getOrderAmount()==null||fact.getOrderedAmount().compareTo(fact.getOrderAmount())!=0||fact.getOrderedAmount().compareTo(term.getEffectiveFixedPrice())!=0||!Boolean.TRUE.equals(fact.getOriginalDebitExists()))return excluded(fact,true,"原周期、订单快照或扣款证据不一致");
        CourseReservedWalletService.money(fact.getOrderedAmount(),false);
        if(Boolean.TRUE.equals(fact.getRefundExists())) {
            if(fact.getRefundAmount()==null||fact.getRefundAmount().compareTo(fact.getOrderedAmount())!=0)return excluded(fact,true,"退款金额与原全额支付不一致，本期不支持部分退款资格");
            return new Row(fact.getId(),fact.getOrderId(),"REFUNDED",new BigDecimal("0.00"),fact.getFulfilledAt(),fact.getCompletionConfirmedAt(),"REFUND",fact.getOrderRefundedAt(),false,"原支付已全额退款");
        }
        if(fact.getOrderRefundedAt()!=null)return excluded(fact,true,"退款标记缺少原钱包退款流水");
        if(fact.getPaidAt()==null||fact.getPaidAt().isBefore(term.getStartAt())||term.getEndAt()!=null&&!fact.getPaidAt().isBefore(term.getEndAt())||fact.getPaidAt().isAfter(asOf))return excluded(fact,false,"不属于原购买窗口");
        if(fact.getOrderStatus()!=null&&Set.of(3,4,6).contains(fact.getOrderStatus()))return excluded(fact,false,"取消、失败或无效订单不参与资格");
        if(!Integer.valueOf(2).equals(fact.getOrderStatus()))return pending(fact,"原订单尚未完成有效履约");
        var exact=fact.getFulfilledAt();
        if(exact!=null&&EXACT_SOURCES.contains(Objects.toString(fact.getFulfillmentSource(),""))) {
            if(exact.isBefore(fact.getPaidAt())||exact.isAfter(asOf))return excluded(fact,true,"完成事实早于支付或晚于当前可信时间");
            if(fact.getCompletionConfirmedAt()!=null&&"UPSTREAM_STATUS".equals(fact.getCompletionConfirmationSource())&&exact.isAfter(fact.getCompletionConfirmedAt()))return excluded(fact,true,"实际完成与先前上游完成确认相矛盾，需复核原证据");
            if(deadline!=null&&exact.isAfter(deadline))return excluded(fact,false,"实际完成晚于原结算截止");
            return new Row(fact.getId(),fact.getOrderId(),"QUALIFIED",fact.getOrderedAmount(),exact,fact.getCompletionConfirmedAt(),"EXACT_FULFILLMENT",null,false,"实际完成时间在截止内");
        }
        var confirmed=fact.getCompletionConfirmedAt();
        // 截止前已确认完成是时间上界证据；实际时间仍保持未知，禁止把观察时刻写入completedAt。
        if(confirmed!=null&&"UPSTREAM_STATUS".equals(fact.getCompletionConfirmationSource())&&!confirmed.isBefore(fact.getPaidAt())&&!confirmed.isAfter(asOf)&&(deadline==null||!confirmed.isAfter(deadline)))return new Row(fact.getId(),fact.getOrderId(),"QUALIFIED",fact.getOrderedAmount(),null,confirmed,"CONFIRMED_BEFORE_DEADLINE",null,false,"上游已在截止前确认为完成，实际完成时间未知");
        if(deadline!=null&&!asOf.isBefore(deadline))return excluded(fact,false,"完成时间缺少截止内证据，需补充核验");
        return pending(fact,"完成状态缺少可信时间证据");
    }
    private static Row excluded(CoursePrivateOrderFact fact,boolean review,String reason){return new Row(fact.getId(),fact.getOrderId(),"EXCLUDED",new BigDecimal("0.00"),fact.getFulfilledAt(),fact.getCompletionConfirmedAt(),"EXCLUDED",fact.getOrderRefundedAt(),review,reason);}
    private static Row pending(CoursePrivateOrderFact fact,String reason){return new Row(fact.getId(),fact.getOrderId(),"PENDING",new BigDecimal("0.00"),fact.getFulfilledAt(),fact.getCompletionConfirmedAt(),"UNKNOWN",null,false,reason);}
    private static BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
}
