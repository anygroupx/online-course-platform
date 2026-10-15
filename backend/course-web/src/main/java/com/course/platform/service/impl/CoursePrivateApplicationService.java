package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 申请只保存代理确认的发布版本，审核通过后才创建或改变权益，不提前移动资金。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateApplicationService {
    private final UserMapper users;
    private final CoursePlatformMapper products;
    private final CoursePrivatePlanMapper plans;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateApplicationMapper applications;
    private final CoursePrivateContractMapper contracts;
    private final CoursePrivateContractTermMapper terms;
    private final CoursePrivateTermService lifecycle;
    private final CoursePricingService pricing;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    @Value("${course.private-operations.enabled:false}") private boolean enabled;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateRenewalService renewal;
    @org.springframework.beans.factory.annotation.Autowired private CoursePrivateUpgradeService upgrade;

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> submit(Long userId,CoursePrivateApplicationRequest request){access.self(userId);return create(userId,request,false);}

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> assign(AdminCoursePrivateApplicationRequest request,Long operatorId){
        access.require(operatorId,"course-private-contract:review");
        String uid=request==null?null:request.getUserUid();validateUid(uid);
        var target=users.selectOne(new QueryWrapper<User>().eq("uid",uid));if(target==null)throw new BusinessException(ResultCode.USER_NOT_FOUND);
        return create(target.getId(),request,true);
    }

    private Map<String,Object> create(Long userId,CoursePrivateApplicationRequest request,boolean admin) {
        validate(request);var user=users.selectByIdForUpdate(userId);if(user==null||!Integer.valueOf(1).equals(user.getStatus()))throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        String identity=identity(request);var existing=applications.selectByRequestKey(userId,request.getRequestKey());
        if(existing!=null){if(!identity.equals(existing.getOperationFingerprint()))throw conflict("权益申请幂等键内容冲突");return view(existing,admin);}
        if(!enabled)throw conflict("密价运营申请功能尚未开启");
        var version=versions.selectById(request.getPlanVersionId());if(version==null||!Objects.equals(version.getPlanId(),request.getPlanId()))throw missing("套餐发布版本不存在");
        if(products.selectByIdForUpdate(version.getPlatformId())==null)throw new BusinessException(ResultCode.COURSE_NOT_FOUND);pricing.requirePlatform(version.getPlatformId());
        var plan=plans.selectByIdForUpdate(request.getPlanId());
        boolean continuing="RENEW".equals(request.getApplicationType());
        // 停止新销售不能撤销原协议续期政策；续期在下面核对原周期及明确确认的版本。
        if(plan==null||!Objects.equals(plan.getPlatformId(),version.getPlatformId())||version.getPublishedAt()==null||version.getPublishedAt().isAfter(time.now())||!continuing&&(!"PUBLISHED".equals(plan.getStatus())||!admin&&!Boolean.TRUE.equals(plan.getVisibleToAgents())||!Objects.equals(plan.getCurrentVersionId(),version.getId())||!time.inWindow(time.now(),version.getEffectiveFrom(),version.getEffectiveUntil())))throw conflict("套餐已停用、不公开或发布版本已变化");
        var contract=contracts.selectByBinding(userId,version.getPlatformId());if(contract!=null)contract=contracts.selectByIdForUpdate(contract.getId());
        CoursePrivateContractTerm source=null;
        if(request.getSourceTermId()!=null){source=terms.selectById(request.getSourceTermId());if(source==null||contract==null||!Objects.equals(source.getUserId(),userId)||!Objects.equals(source.getContractId(),contract.getId())||!Objects.equals(source.getPlatformId(),version.getPlatformId())||!Objects.equals(contract.getCurrentTermId(),source.getId()))throw missing("原权益周期不存在");}
        switch(request.getApplicationType()) {
            case "OPEN" -> {if(contract!=null||source!=null)throw conflict("已有课程资金关系，须明确申请续期、升级或重新签约");initialPolicy(request);}
            case "REJOIN" -> {if(contract==null||!"TERMINATED".equals(contract.getStatus())||!request.isAcknowledgeLegacyChange())throw conflict("重新签约必须确认已终止关系及旧权益变化");initialPolicy(request);}
            case "RENEW" -> {
                if(contract==null||source==null||source.getEndAt()==null||Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus())||!"NEXT_TERM".equals(request.getEffectivePolicy()))throw conflict("当前关系不能申请周期续期");
                var rules=config(source.getConfigSnapshot()).getRules();if("NONE".equals(rules.getRenewalPolicy()))throw conflict("当前套餐没有续期政策");
                if(!Objects.equals(source.getPlanId(),plan.getId())||"SAME_VERSION".equals(rules.getRenewalPricePolicy())&&!Objects.equals(source.getVersionId(),version.getId()))throw conflict("续期不能替换原套餐或冻结规则版本");
                var from=time.endAt(source.getEndAt(),-rules.getRenewalBeforeDays());var until=time.endAt(source.getEndAt(),rules.getRenewalAfterDays());
                if(time.now().isBefore(from)||time.now().isAfter(until))throw conflict("当前不在续期申请窗口内");
            }
            case "UPGRADE" -> {
                if(contract==null||source==null||Set.of("SUSPENDED","TERMINATED").contains(contract.getStatus()))throw conflict("当前关系不能申请升级");
                if("LEGACY_PERMANENT".equals(source.getOperationsMode())&&!request.isAcknowledgeLegacyChange())throw conflict("旧长期权益转换必须由代理明确确认");
                if(source.getEndAt()==null&&"NEXT_TERM".equals(request.getEffectivePolicy()))throw conflict("长期权益没有下一期边界，须明确申请人工即时审核");
            }
            default -> throw conflict("申请类型无效");
        }
        var value=new CoursePrivateApplication();value.setUserId(userId);value.setPlatformId(version.getPlatformId());value.setPlanId(plan.getId());value.setVersionId(version.getId());value.setContractId(contract==null?null:contract.getId());value.setSourceTermId(source==null?null:source.getId());value.setApplicationType(request.getApplicationType());value.setApplicationReason(request.getReason().trim());value.setEffectivePolicy(Set.of("OPEN","REJOIN").contains(request.getApplicationType())?"ON_ACTIVATION":request.getEffectivePolicy());value.setAcknowledgeLegacyChange(request.isAcknowledgeLegacyChange());value.setStatus("PENDING");value.setSubmittedAt(time.now());value.setRequestKey(request.getRequestKey());value.setOperationFingerprint(identity);value.setVersion(0);
        value.setSubmittedBy(SecurityUtils.getCurrentUserId());
        if(admin&&("REJOIN".equals(value.getApplicationType())||source!=null&&"UPGRADE".equals(value.getApplicationType())&&"LEGACY_PERMANENT".equals(source.getOperationsMode())))throw conflict("重新签约或旧权益转换须由代理本人明确提交，后台不能代确认");
        if("UPGRADE".equals(value.getApplicationType()))value.setFundingStartLedgerId(lifecycle.captureFundingWatermark(userId,value.getPlatformId()));
        if(applications.insert(value)!=1)throw conflict("权益申请保存失败");
        if(!admin&&"OPEN".equals(value.getApplicationType())&&"AUTO".equals(plan.getApplicationMode())){lifecycle.enrollAutomatic(value.getId(),userId);value=applications.selectById(value.getId());}
        return view(value,admin);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> review(Long id,Integer version,Long operatorId,boolean approved,String reason) {
        access.require(operatorId,"course-private-contract:review");CoursePrivatePlanService.reason(reason);
        if(approved){
            var initial=applications.selectById(id);if(initial==null)throw missing("权益申请不存在");
            if("RENEW".equals(initial.getApplicationType())){if(renewal==null)throw conflict("续期执行服务尚未就绪");renewal.review(id,version,operatorId,reason);}
            else if("UPGRADE".equals(initial.getApplicationType())){if(upgrade==null)throw conflict("升级执行服务尚未就绪");upgrade.review(id,version,operatorId,reason);}
            else lifecycle.enroll(id,version,operatorId,reason);
            return view(applications.selectById(id),true);
        }
        var initial=applications.selectById(id);if(initial==null)throw missing("权益申请不存在");users.selectByIdForUpdate(initial.getUserId());var value=applications.selectByIdForUpdate(id);
        if("REJECTED".equals(value.getStatus()))return view(value,true);
        if(!"PENDING".equals(value.getStatus())||version==null||!Objects.equals(value.getVersion(),version))throw conflict("申请状态或版本已变化");
        if(applications.review(id,version,"REJECTED",operatorId,reason,value.getContractId(),time.now())!=1)throw conflict("权益审核保存冲突");return view(applications.selectById(id),true);
    }

    /** 人工补授入口与普通达标审核分开，主体、独立权限及冻结政策由续期服务再次核验。 */
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> grantRenewal(Long id,Integer version,Long operatorId,String reason) {
        access.require(operatorId,"course-private-contract:review");
        if(renewal==null)throw conflict("续期执行服务尚未就绪");
        renewal.grantReviewed(id,version,operatorId,reason);
        return view(applications.selectById(id),true);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> cancel(Long userId,Long id) {
        access.self(userId);var initial=applications.selectById(id);if(initial==null||!Objects.equals(initial.getUserId(),userId))throw missing("权益申请不存在");users.selectByIdForUpdate(userId);var value=applications.selectByIdForUpdate(id);
        if("CANCELLED".equals(value.getStatus()))return view(value,false);if(!"PENDING".equals(value.getStatus()))throw conflict("已审核的权益申请不能撤销");
        if(applications.review(id,value.getVersion(),"CANCELLED",userId,"代理撤销权益申请",value.getContractId(),time.now())!=1)throw conflict("权益申请撤销冲突");return view(applications.selectById(id),false);
    }

    public IPage<Map<String,Object>> page(Long userId,int page,int pageSize,Long platformId,String status,String userUid) {
        boolean admin=userId==null;if(admin)access.require(SecurityUtils.getCurrentUserId(),"course-private-contract:review");else access.self(userId);CoursePrivatePlanService.page(page,pageSize);
        Long target=userId;if(admin&&userUid!=null&&!userUid.isBlank()){validateUid(userUid);var user=users.selectOne(new QueryWrapper<User>().eq("uid",userUid));target=user==null?-1L:user.getId();}
        return applications.selectPage(new Page<>(page,pageSize),new QueryWrapper<CoursePrivateApplication>().eq(target!=null,"user_id",target).eq(platformId!=null,"platform_id",platformId).eq(status!=null&&!status.isBlank(),"status",status).orderByDesc("id")).convert(value->view(value,admin));
    }
    private Map<String,Object> view(CoursePrivateApplication value,boolean admin) {
        var result=new LinkedHashMap<String,Object>();result.put("id",value.getId());result.put("platformId",value.getPlatformId());result.put("planId",value.getPlanId());result.put("planVersionId",value.getVersionId());result.put("contractId",value.getContractId());result.put("sourceTermId",value.getSourceTermId());result.put("applicationType",value.getApplicationType());result.put("applicationReason",value.getApplicationReason());result.put("effectivePolicy",value.getEffectivePolicy());result.put("acknowledgeLegacyChange",value.getAcknowledgeLegacyChange());result.put("status",value.getStatus());result.put("submittedAt",value.getSubmittedAt());result.put("reviewedAt",value.getReviewedAt());result.put("reviewReason",value.getReviewReason());result.put("version",value.getVersion());result.put("requestKey",value.getRequestKey());
        var version=versions.selectById(value.getVersionId());result.put("planName",version==null?null:config(version.getConfigSnapshot()).getPlanName());
        if(admin){var user=users.selectById(value.getUserId());result.put("userUid",user==null?null:user.getUid());result.put("username",user==null?null:user.getUsername());result.put("reviewedBy",value.getReviewedBy());}return result;
    }
    private CoursePrivatePlanRequest config(String value){try{return json.readValue(value,CoursePrivatePlanRequest.class);}catch(Exception error){throw conflict("发布规则快照损坏，需人工核查");}}
    private void initialPolicy(CoursePrivateApplicationRequest request){if(!Set.of("ON_ACTIVATION","NEXT_TERM").contains(request.getEffectivePolicy()))throw conflict("首次开通按实际资格及允许时间生效，不能自行指定即时权益");}
    private void validate(CoursePrivateApplicationRequest value){if(value==null||value.getPlanId()==null||value.getPlanId()<=0||value.getPlanVersionId()==null||value.getPlanVersionId()<=0||!Set.of("OPEN","RENEW","UPGRADE","REJOIN").contains(Objects.toString(value.getApplicationType(),""))||!Set.of("ON_ACTIVATION","NEXT_TERM","IMMEDIATE","REVIEW_IMMEDIATE").contains(Objects.toString(value.getEffectivePolicy(),""))||value.getSourceTermId()!=null&&value.getSourceTermId()<=0||value.getRequestKey()==null||!value.getRequestKey().matches("[A-Za-z0-9_-]{1,64}"))throw conflict("权益申请参数无效");CoursePrivatePlanService.reason(value.getReason());}
    private void validateUid(String uid){try{if(uid==null||!UUID.fromString(uid).toString().equalsIgnoreCase(uid))throw new IllegalArgumentException();}catch(IllegalArgumentException error){throw new BusinessException(ResultCode.PARAM_ERROR,"用户UUID格式无效");}}
    private String identity(CoursePrivateApplicationRequest request){try{var values=Arrays.asList(request.getPlanId(),request.getPlanVersionId(),request.getApplicationType(),request.getSourceTermId(),Set.of("OPEN","REJOIN").contains(request.getApplicationType())?"ON_ACTIVATION":request.getEffectivePolicy(),request.isAcknowledgeLegacyChange(),request.getReason().trim());return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(values).getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw conflict("申请摘要生成失败");}}
    private BusinessException conflict(String value){return new BusinessException(ResultCode.CONFLICT,value);}
    private BusinessException missing(String value){return new BusinessException(ResultCode.NOT_FOUND,value);}
}
