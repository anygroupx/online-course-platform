package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 全局暂停、恢复、终止不移动本金，不改变已冻结价格或购买期限，重签仍走独立申请。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateLifecycleControlService {
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateTermEventMapper events;
    private final CoursePrivateSettlementService settlement;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    public record StateResult(Long contractId,Integer version,String status,LocalDateTime changedAt){}
    private record SavedResult(String fingerprint,StateResult result){}

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public StateResult change(Long contractId,Integer expectedVersion,Long operator,String action,String reason,String requestKey) {
        access.require(operator,"course-private-term:adjust");CoursePrivatePlanService.reason(reason);
        if(expectedVersion==null||expectedVersion<0||!Set.of("SUSPEND","RESUME","TERMINATE").contains(Objects.toString(action,""))||requestKey==null||!requestKey.matches("[A-Za-z0-9_-]{1,64}"))throw conflict("生命周期操作参数无效");
        var initial=contracts.selectById(contractId);if(initial==null)throw missing();
        if(users.selectByIdForUpdate(initial.getUserId())==null)throw missing();var contract=contracts.selectByIdForUpdate(contractId);var bound=wallets.selectByBinding(initial.getUserId(),initial.getPlatformId());if(bound==null)throw conflict("原课程专户不存在");var wallet=wallets.selectByIdForUpdate(bound.getId());CoursePricingService.requireBinding(contract,wallet,initial.getUserId(),initial.getPlatformId());
        String identity="state_"+digest(List.of(contractId,operator,requestKey));String fingerprint=digest(List.of(contractId,operator,expectedVersion,action,reason.trim()));var prior=events.selectByBizNo(identity);
        if(prior!=null){var saved=readSaved(prior.getAfterJson());if(!saved.fingerprint().equals(fingerprint))throw conflict("生命周期幂等键内容冲突");return saved.result();}
        if(!Objects.equals(contract.getVersion(),expectedVersion))throw conflict("协议状态版本已变化，请刷新后重试");
        if("TERMINATED".equals(contract.getStatus()))throw conflict("已终止关系不能恢复，须明确重新签约");
        String before=encode(contract);var current=contract.getCurrentTermId()==null?null:terms.selectByIdForUpdate(contract.getCurrentTermId());
        if(current!=null)binding(contract,current);
        switch(action){
            case "SUSPEND" -> {if("SUSPENDED".equals(contract.getStatus()))throw conflict("协议已经暂停，请核对原请求");contract.setStatus("SUSPENDED");}
            case "RESUME" -> {if(!"SUSPENDED".equals(contract.getStatus()))throw conflict("只有暂停关系可恢复");contract.setStatus(resumeStatus(contract,current));}
            case "TERMINATE" -> {
                contract.setStatus("TERMINATED");
                for(var snapshot:terms.selectByContract(contractId)){
                    if(!Objects.equals(snapshot.getRelationGeneration(),contract.getRelationGeneration()))continue;
                    var term=terms.selectByIdForUpdate(snapshot.getId());binding(contract,term);Integer version=term.getVersion();
                    if(term.getActivatedAt()==null&&Set.of("PENDING_ACTIVATION","SCHEDULED").contains(term.getStatus()))term.setStatus("CANCELLED");
                    else if("ACTIVE".equals(term.getStatus())){term.setStatus("TERMINATED");if(term.getStartAt()!=null&&(term.getEndAt()==null||time.now().isBefore(term.getEndAt())))term.setSupersededAt(time.now());}
                    else continue;
                    if(terms.updateEvaluation(term,version,time.now())!=1)throw conflict("终止关联周期版本冲突");
                }
            }
            default -> throw conflict("未知生命周期操作");
        }
        contract.setUpdatedBy(operator);if(contracts.updateOperations(contract,expectedVersion,time.now())!=1)throw conflict("协议状态保存冲突");
        var result=new StateResult(contractId,expectedVersion+1,contract.getStatus(),time.now());var event=new CoursePrivateTermEvent();event.setContractId(contractId);event.setTermId(contract.getCurrentTermId());event.setEventType(switch(action){case "SUSPEND"->"CONTRACT_SUSPENDED";case "RESUME"->"CONTRACT_RESUMED";default->"CONTRACT_TERMINATED";});event.setBeforeJson(before);event.setAfterJson(encode(new SavedResult(fingerprint,result)));event.setOperatorId(operator);event.setReason(reason.trim());event.setBizNo(identity);event.setHappenedAt(time.now());if(events.insert(event)!=1)throw conflict("生命周期审计保存失败");
        // 已终止的原付款仍须按真实截断边界结算，钱包及历史退款身份完整保留。
        if("TERMINATE".equals(action)&&current!=null&&current.getActivatedAt()!=null&&!"LEGACY_PERMANENT".equals(current.getOperationsMode()))settlement.evaluate(current.getId());
        return result;
    }
    private String resumeStatus(CoursePrivateContract contract,CoursePrivateContractTerm term){
        if(term==null||"LEGACY_PERMANENT".equals(term.getOperationsMode())){if(contract.getValidUntil()!=null&&!time.now().isBefore(contract.getValidUntil()))return "EXPIRED";return contract.getActivatedAt()!=null||term!=null&&"ACTIVE".equals(term.getStatus())?"ACTIVE":"PENDING_ACTIVATION";}
        if(Set.of("PENDING_ACTIVATION","SCHEDULED").contains(term.getStatus()))return "PENDING_ACTIVATION";
        return "ACTIVE".equals(term.getStatus())&&term.getActivatedAt()!=null&&time.inWindow(time.now(),term.getStartAt(),term.getEndAt())?"ACTIVE":"EXPIRED";
    }
    private void binding(CoursePrivateContract contract,CoursePrivateContractTerm term){if(!Objects.equals(term.getContractId(),contract.getId())||!Objects.equals(term.getUserId(),contract.getUserId())||!Objects.equals(term.getPlatformId(),contract.getPlatformId())||!Objects.equals(term.getRelationGeneration(),contract.getRelationGeneration()))throw conflict("生命周期周期绑定异常");}
    private SavedResult readSaved(String value){try{return json.readValue(value,SavedResult.class);}catch(Exception error){throw conflict("原生命周期响应快照损坏，需核查");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw conflict("生命周期证据序列化失败");}}
    private String digest(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(Exception error){throw conflict("生命周期身份生成失败");}}
    private BusinessException missing(){return new BusinessException(ResultCode.NOT_FOUND,"原课程资金关系或拥有者不存在");}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
}
