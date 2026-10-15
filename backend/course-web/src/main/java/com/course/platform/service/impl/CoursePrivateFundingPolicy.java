package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CoursePrivateFundingFact;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 资格只解释可信资金事实，不冻结本金，不重复使用别的周期已认领资金。 */
public final class CoursePrivateFundingPolicy {
    private CoursePrivateFundingPolicy(){}
    public record Window(Long walletId,Long userId,Long termId,Long startLedgerId,Long endLedgerId,LocalDateTime startAt,LocalDateTime endAt,LocalDateTime asOf){}
    public record Assessment(BigDecimal netDepositAmount,boolean qualified,LocalDateTime qualifiedAt,List<Long> unallocatedLedgerIds){}

    public static Assessment assess(Window window,List<CoursePrivateFundingFact> facts,BigDecimal threshold) {
        threshold=CourseReservedWalletService.money(threshold,true);
        if(window==null||window.walletId()==null||window.userId()==null||window.asOf()==null||window.startLedgerId()!=null&&window.startLedgerId()<0||window.startLedgerId()!=null&&window.endLedgerId()!=null&&window.endLedgerId()<window.startLedgerId()||window.startAt()!=null&&window.endAt()!=null&&window.endAt().isBefore(window.startAt()))throw invalid("资金资格窗口无效");
        var rows=new ArrayList<>(Objects.requireNonNull(facts,"资金事实不能为空"));var identities=new HashSet<Long>();
        for(var fact:rows){
            if(fact==null||fact.getLedgerId()==null||fact.getLedgerId()<=0||fact.getCompletedAt()==null||!Objects.equals(fact.getWalletId(),window.walletId())||!Objects.equals(fact.getUserId(),window.userId())||!Set.of("TRANSFER_IN","TRANSFER_OUT").contains(Objects.toString(fact.getTransferType(),"")))throw invalid("资金事实来源或绑定异常");
            CourseReservedWalletService.money(fact.getAmount(),false);
            if(!identities.add(fact.getLedgerId()))throw invalid("资金事实重复，不能重复累计资格");
            if("TRANSFER_OUT".equals(fact.getTransferType())&&fact.getAllocatedTermId()!=null)throw invalid("转回流水不能作为资格认领来源");
        }
        rows.sort(Comparator.comparing(CoursePrivateFundingFact::getLedgerId));
        BigDecimal net=new BigDecimal("0.00");LocalDateTime qualifiedAt=null;var unallocated=new ArrayList<Long>();
        boolean opened=window.startAt()==null||!window.asOf().isBefore(window.startAt());
        if(opened&&threshold.signum()==0)qualifiedAt=window.startAt()==null?window.asOf():window.startAt();
        for(var fact:rows){
            if(window.startLedgerId()!=null&&fact.getLedgerId()<=window.startLedgerId()||window.endLedgerId()!=null&&fact.getLedgerId()>window.endLedgerId()||window.startAt()!=null&&fact.getCompletedAt().isBefore(window.startAt())||window.endAt()!=null&&!fact.getCompletedAt().isBefore(window.endAt())||fact.getCompletedAt().isAfter(window.asOf()))continue;
            boolean incoming="TRANSFER_IN".equals(fact.getTransferType());
            if(incoming&&fact.getAllocatedTermId()!=null&&!Objects.equals(fact.getAllocatedTermId(),window.termId()))continue;
            BigDecimal before=net;BigDecimal amount=CourseReservedWalletService.money(fact.getAmount(),false);net=incoming?net.add(amount):net.subtract(amount);
            if(incoming&&fact.getAllocatedTermId()==null)unallocated.add(fact.getLedgerId());
            // 下降后重新达标使用最后一次真实交叉时刻，不能沿用早先已经失效的资格时间。
            if(threshold.signum()>0){if(net.compareTo(threshold)<0)qualifiedAt=null;else if(before.compareTo(threshold)<0)qualifiedAt=fact.getCompletedAt();}
        }
        boolean qualified=opened&&(threshold.signum()==0||qualifiedAt!=null&&net.compareTo(threshold)>=0);
        return new Assessment(net,qualified,qualified?qualifiedAt:null,List.copyOf(unallocated));
    }
    private static BusinessException invalid(String value){return new BusinessException(ResultCode.CONFLICT,value);}
}
