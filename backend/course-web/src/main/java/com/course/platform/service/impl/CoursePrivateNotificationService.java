package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** 站内通知不移动本金；周期阶段唯一身份与收件箱发布失败重试各自持久化。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoursePrivateNotificationService {
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateNotificationMapper notices;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @Autowired private PlatformTransactionManager transactionManager;
    @Value("${course.private-operations.notifications-enabled:false}") private boolean enabled;
    private long observationCursor=0;

    @Transactional(rollbackFor=Exception.class)
    public void observe(Long termId) {
        if(!enabled)return;
        var initial=terms.selectById(termId);if(initial==null)return;
        // 遵循资金领域的拥有者、协议、专户、周期锁顺序，避免与激活发生交叉锁。
        if(users.selectByIdForUpdate(initial.getUserId())==null)return;
        var contract=contracts.selectByIdForUpdate(initial.getContractId());if(contract==null)return;
        var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)return;
        wallets.selectByIdForUpdate(bound.getId());var term=terms.selectByIdForUpdate(termId);
        if(term==null||!Objects.equals(contract.getCurrentTermId(),termId)||!Objects.equals(contract.getRelationGeneration(),term.getRelationGeneration())||!Objects.equals(contract.getUserId(),term.getUserId())||!Objects.equals(contract.getPlatformId(),term.getPlatformId()))return;
        if(term.getActivatedAt()==null||Set.of("CANCELLED","SCHEDULED","PENDING_ACTIVATION").contains(term.getStatus()))return;
        if(Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus()))return;
        var now=time.now();var end=term.getSupersededAt()==null?term.getEndAt():term.getSupersededAt();
        if(end!=null&&!now.isBefore(end)){add(term,"EXPIRED","密价周期已结束","购买期限已结束，余额保留在原专户；续期或重新签约后按新周期使用。");return;}
        String activation=term.getRenewalSourceTermId()==null?"ACTIVATED":switch(Objects.toString(term.getQualificationKind(),"")){case "UPGRADE_FUNDS"->"UPGRADE_SUCCESS";case "MANUAL_GRANT"->"MANUAL_GRANT_SUCCESS";default->"RENEWAL_SUCCESS";};
        if(!"LEGACY_PERMANENT".equals(term.getOperationsMode()))add(term,activation,"密价权益已生效","当前周期已生效，请按本期冻结价格与购买期限使用。");
        // 迟到任务只生成当前提醒窗口，避免补发三条已过时的倒计时。
        if(end!=null&&!now.isBefore(end.minusDays(7))){String stage=now.isBefore(end.minusDays(3))?"EXPIRING_7":now.isBefore(end.minusDays(1))?"EXPIRING_3":"EXPIRING_1";add(term,stage,"密价周期即将结束","请查看本期结算进度与续期条件；结算宽限不会延长购买期限。");}
    }
    private void add(CoursePrivateContractTerm term,String stage,String title,String body){
        if(notices.selectByStage(term.getId(),stage)!=null)return;
        var payload=new LinkedHashMap<String,Object>();payload.put("title",title);payload.put("body",body);payload.put("termId",term.getId());try{var config=json.readValue(term.getConfigSnapshot(),com.course.platform.domain.dto.CoursePrivatePlanRequest.class);payload.put("planName",config.getPlanName());}catch(Exception unknown){payload.put("planName","原协议");}payload.put("planId",term.getPlanId());payload.put("termNo",term.getTermNo());payload.put("price",term.getEffectiveFixedPrice()==null?null:term.getEffectiveFixedPrice().toPlainString());payload.put("endAt",term.getEndAt());payload.put("settlementDeadlineAt",term.getSettlementDeadlineAt());payload.put("settledConsumptionAmount","LEGACY_PERMANENT".equals(term.getOperationsMode())||term.getSettledConsumptionAmount()==null?null:term.getSettledConsumptionAmount().toPlainString());payload.put("renewalTarget","LEGACY_PERMANENT".equals(term.getOperationsMode())||term.getRenewalTarget()==null?null:term.getRenewalTarget().toPlainString());payload.put("remainingConsumption","LEGACY_PERMANENT".equals(term.getOperationsMode())||term.getSettledConsumptionAmount()==null||term.getRenewalTarget()==null?null:term.getRenewalTarget().subtract(term.getSettledConsumptionAmount()).max(java.math.BigDecimal.ZERO).toPlainString());payload.put("progressAsOf",term.getUpdatedAt());payload.put("actionUrl","/course-private-operations");
        try{var row=new CoursePrivateNotification();row.setTermId(term.getId());row.setUserId(term.getUserId());row.setStage(stage);row.setPayloadJson(json.writeValueAsString(payload));row.setStatus("PENDING");row.setAvailableAt(time.now());row.setAttempts(0);row.setVersion(0);if(notices.insert(row)!=1)throw new IllegalStateException("通知记录未保存");}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new BusinessException(ResultCode.CONFLICT,"通知内容序列化失败");}
    }
    /** 独立轮转所有当前周期，每个观察单独事务，不依赖现代结算批次才能提醒旧协议。 */
    public synchronized void reconcileBatch(){
        if(!enabled)return;var batch=notices.selectObservationBatch(observationCursor,100);var tx=new TransactionTemplate(transactionManager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        if(batch.isEmpty())observationCursor=0;
        for(Long id:batch){try{tx.executeWithoutResult(status->observe(id));}catch(RuntimeException failure){log.warn("周期通知观察失败，稍后轮转重试：termId={}",id);}observationCursor=id;}
        deliverBatch();
    }
    public int deliverBatch(){
        if(!enabled)return 0;int delivered=0;
        var tx=new TransactionTemplate(transactionManager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for(var candidate:notices.selectReady(time.now(),10,100)){
            try{Boolean changed=tx.execute(status->{var row=notices.selectByIdForUpdate(candidate.getId());if(row==null||"DELIVERED".equals(row.getStatus())||row.getAvailableAt().isAfter(time.now()))return false;return notices.updateDelivery(row.getId(),row.getVersion(),"DELIVERED",time.now(),time.now())==1;});if(Boolean.TRUE.equals(changed))delivered++;}
            catch(RuntimeException failure){
                // 发布事务先回滚，再单独记录失败；一个坏记录不阻塞其他人的通知。
                try{tx.executeWithoutResult(status->{var row=notices.selectByIdForUpdate(candidate.getId());if(row!=null&&!"DELIVERED".equals(row.getStatus()))notices.updateDelivery(row.getId(),row.getVersion(),"FAILED",null,time.now().plusMinutes(Math.min(60,1L<<Math.min(6,row.getAttempts()))));});}catch(RuntimeException persistenceFailure){log.warn("站内通知失败记录未保存：id={}",candidate.getId());}
                log.warn("站内通知发布失败，等待重试：id={}",candidate.getId());
            }
        }return delivered;
    }
    public Map<String,Object> inbox(Long userId,int page,int pageSize){access.self(userId);int size=Math.max(1,Math.min(100,pageSize));int current=Math.max(1,Math.min(100000,page));var rows=notices.selectInbox(userId,(current-1)*size,size).stream().map(row->{var value=new LinkedHashMap<String,Object>();value.put("id",row.getId());value.put("stage",row.getStage());value.put("deliveredAt",row.getDeliveredAt());value.put("readAt",row.getReadAt());try{value.put("payload",json.readValue(row.getPayloadJson(),Map.class));}catch(Exception invalid){value.put("payload",Map.of("title","通知内容需核查","body","请在权益中心查看当前周期。"));}return value;}).toList();return Map.of("records",rows,"total",notices.countInbox(userId),"unread",notices.countUnread(userId),"page",current,"pageSize",size);}
    @Transactional(rollbackFor=Exception.class)
    public void markRead(Long userId,Long id){access.self(userId);var row=notices.selectById(id);if(row==null||!Objects.equals(row.getUserId(),userId)||!"DELIVERED".equals(row.getStatus()))throw new BusinessException(ResultCode.NOT_FOUND,"本人通知不存在");notices.markRead(id,userId,time.now());}
}
