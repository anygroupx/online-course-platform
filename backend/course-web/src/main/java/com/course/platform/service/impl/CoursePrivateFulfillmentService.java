package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.event.CourseOrderFactsChangedEvent;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 履约事实与观察上界独立保存，提交后再触发资金域结算，不在上游请求内锁账户。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateFulfillmentService {
    private final CourseOrderMapper orders;
    private final CoursePrivateTermOrderMapper associations;
    private final CoursePrivateTermEventMapper audit;
    private final UserMapper users;
    private final CoursePrivateOperationsAccess access;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;

    @Transactional(rollbackFor=Exception.class)
    public void recordUpstream(CourseOrder before,OrderProgressResult result) {
        if(before==null||before.getPrivateTermId()==null||result==null)return;
        int changed;
        if(Integer.valueOf(2).equals(result.getOrderStatus())) {
            if(before.getApiProviderId()==null||before.getThirdOrderId()==null||before.getThirdOrderId().isBlank())return;
            var actual=result.getActualFulfilledAt();if(actual!=null)validateTime(before.getId(),actual);
            changed=orders.recordUpstreamCompletion(before.getId(),before.getApiProviderId(),before.getThirdOrderId(),time.now(),actual,!Integer.valueOf(2).equals(before.getOrderStatus()));
        } else changed=orders.invalidateCompletion(before.getId(),result.getOrderStatus());
        if(changed>0)publish(before,orders.selectHistoricalById(before.getId()),"UPSTREAM_STATUS",null,"精确绑定上游订单状态核验");
    }

    @Transactional(rollbackFor=Exception.class)
    public void verifyExact(Long orderId,LocalDateTime actualAt,String reason,String evidence,Long operatorId) {
        access.require(operatorId,"order:update");access.require(operatorId,"course-private-settlement:execute");CoursePrivatePlanService.reason(reason);
        if(evidence==null||evidence.isBlank()||evidence.length()>2000)throw conflict("必须填写不超过2000字的实际履约核验依据");
        var initial=orders.selectHistoricalById(orderId);if(initial==null||initial.getPrivateTermId()==null)throw conflict("仅能核验有原成交周期的课程订单");
        if(users.selectByIdForUpdate(initial.getUserId())==null)throw conflict("原拥有者不存在");var before=orders.selectHistoricalByIdForUpdate(orderId);validateTime(orderId,actualAt);
        if(!Integer.valueOf(2).equals(before.getOrderStatus())||before.getRefundedAt()!=null)throw conflict("订单尚未完整履约或已退款，不能确认有效完成");
        if(Objects.equals(before.getFulfilledAt(),actualAt)&&"ADMIN_VERIFIED".equals(before.getFulfillmentSource()))return;
        if(orders.recordVerifiedCompletion(orderId,actualAt)!=1)throw conflict("履约核验状态已变化");var after=orders.selectHistoricalById(orderId);
        try{var payload=new LinkedHashMap<String,Object>();payload.put("facts",json.readTree(encode(after)));payload.put("evidence",evidence);String eventId=UUID.randomUUID().toString().replace("-","");String afterJson=json.writeValueAsString(payload);
            // 人工核验已先锁原账户，证据与原事实同事务保存；监听器失败也不能丢失核验依据。
            var event=new com.course.platform.domain.entity.CoursePrivateTermEvent();event.setContractId(before.getPrivateContractId());event.setTermId(before.getPrivateTermId());event.setEventType("FULFILLMENT_VERIFIED");event.setBeforeJson(encode(before));event.setAfterJson(afterJson);event.setOperatorId(operatorId);event.setReason(reason);event.setBizNo("verified_order_"+eventId);event.setTargetVersionId(before.getPrivatePlanVersionId());event.setHappenedAt(time.now());if(audit.insert(event)!=1)throw conflict("人工核验审计保存失败");
            publisher.publishEvent(new CourseOrderFactsChangedEvent(orderId,eventId,"ADMIN_VERIFIED",operatorId,reason,encode(before),afterJson,time.now()));}catch(Exception error){throw conflict("履约核验依据保存失败");}
    }

    /** 自营实际完成写入成功后调用，时间已在原原子状态更新中保存。 */
    public void recordNativeCompletion(CourseOrder before,Long operatorId,String reason) {
        if(before==null||before.getPrivateTermId()==null)return;var after=orders.selectHistoricalById(before.getId());
        if(after!=null&&Integer.valueOf(2).equals(after.getOrderStatus())&&after.getFulfilledAt()!=null&&"SELF_OPERATED_COMPLETED".equals(after.getFulfillmentSource()))publish(before,after,"SELF_OPERATED_COMPLETED",operatorId,reason);
    }
    private void validateTime(Long orderId,LocalDateTime actualAt){var association=associations.selectByOrderId(orderId);if(actualAt==null||actualAt.getNano()!=0||association==null||actualAt.isBefore(association.getPaidAt())||actualAt.isAfter(time.now()))throw conflict("实际履约时间必须在原支付后、当前可信时间内并精确到秒");}
    private void publish(CourseOrder before,CourseOrder after,String source,Long operatorId,String reason){publisher.publishEvent(new CourseOrderFactsChangedEvent(before.getId(),UUID.randomUUID().toString().replace("-",""),source,operatorId,reason,encode(before),encode(after),time.now()));}
    private String encode(CourseOrder order){var value=new LinkedHashMap<String,Object>();value.put("orderId",order.getId());value.put("termId",order.getPrivateTermId());value.put("status",order.getOrderStatus());value.put("fulfilledAt",order.getFulfilledAt());value.put("fulfillmentSource",order.getFulfillmentSource());value.put("completionConfirmedAt",order.getCompletionConfirmedAt());value.put("confirmationSource",order.getCompletionConfirmationSource());value.put("refundedAt",order.getRefundedAt());try{return json.writeValueAsString(value);}catch(Exception error){throw conflict("履约事实审计保存失败");}}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
}
