package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.*;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** 套餐草稿、不可变发布和真实成本核验；本服务不执行资金划转。 */
@Service
@RequiredArgsConstructor
public class CoursePrivatePlanService {
    private final CoursePrivatePlanMapper plans;
    private final CoursePrivatePlanVersionMapper versions;
    private final CoursePrivateCostVersionMapper costs;
    private final CoursePrivateRiskApprovalMapper risks;
    private final CoursePlatformMapper platforms;
    private final PlatformCategoryMapper categories;
    private final CoursePrivateOperationsAccess access;
    private final ObjectMapper json;
    private final CoursePrivateOperationsTime time;
    private UserMapper referenceUsers;
    private CoursePricingService referencePricing;
    @org.springframework.beans.factory.annotation.Autowired
    void configureReference(org.springframework.beans.factory.ObjectProvider<UserMapper> users,org.springframework.beans.factory.ObjectProvider<CoursePricingService> pricing){referenceUsers=users.getIfAvailable();referencePricing=pricing.getIfAvailable();}
    @Value("${course.private-operations.enabled:false}") private boolean enabled;

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivatePlan save(Long id,CoursePrivatePlanRequest request,Long operatorId) {
        access.require(operatorId,"course-private-plan:manage");requireEnabled();
        validate(request);lockPlatform(request.getPlatformId());
        CoursePrivatePlan plan;
        if (id==null) {
            if (plans.selectByCode(request.getPlatformId(),request.getPlanCode())!=null) throw conflict("套餐编码已存在");
            plan=new CoursePrivatePlan();plan.setPlatformId(request.getPlatformId());plan.setPlanCode(request.getPlanCode());
            apply(plan,request);plan.setStatus("DRAFT");plan.setDraftVersion(1);plan.setCreatedBy(operatorId);plan.setUpdatedBy(operatorId);plan.setCreatedAt(time.now());plan.setUpdatedAt(time.now());
            if (plans.insert(plan)!=1) throw conflict("草稿保存失败");
        } else {
            plan=locked(id);
            modern(plan);
            if (!Objects.equals(plan.getPlatformId(),request.getPlatformId()) || !Objects.equals(plan.getPlanCode(),request.getPlanCode())) throw invalid("套餐商品和编码不能修改");
            if (!Objects.equals(plan.getDraftVersion(),request.getExpectedDraftVersion())) throw conflict("套餐草稿已变化，请刷新");
            apply(plan,request);plan.setUpdatedBy(operatorId);
            if (plans.updateDraft(plan,request.getExpectedDraftVersion(),time.now())!=1) throw conflict("草稿保存冲突");
            plan.setDraftVersion(request.getExpectedDraftVersion()+1);plan.setUpdatedAt(time.now());
        }
        return plan;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivatePlanVersion publish(Long id,Integer expectedDraftVersion,Long operatorId,String reason) {
        return publish(id,expectedDraftVersion,null,null,operatorId,reason);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivatePlanVersion publish(Long id,Integer expectedDraftVersion,Long expectedCostVersionId,Long expectedRiskApprovalId,Long operatorId,String reason) {
        access.require(operatorId,"course-private-plan:publish");requireEnabled();reason(reason);
        var initial=plans.selectById(id);if(initial==null)throw missing();
        // 草稿更新和发布统一商品→套餐锁序，既阻止并发删除，也避免互相倒序死锁。
        lockPlatform(initial.getPlatformId());CoursePrivatePlan plan=locked(id);modern(plan);draft(plan,expectedDraftVersion);
        CoursePrivatePlanRequest config=decode(plan.getDraftConfigJson());validate(config);
        // 每个草稿最多发布一次；并发发布由套餐行锁及版本唯一索引共同保护。
        var existing=versions.selectByPlanAndVersion(id,plan.getDraftVersion());
        if (existing!=null) return existing;
        var cost=costs.selectLatestVerified(plan.getPlatformId(),time.now());
        if (cost==null) throw conflict("缺少已核验的成本，套餐仍待配置，不能发布");
        if(expectedCostVersionId!=null&&!Objects.equals(expectedCostVersionId,cost.getId()))throw conflict("成本版本已变化，请重新评估发布风险");
        CoursePrivateRiskApproval approval=null;
        if (config.getRules().getFixedPrice().compareTo(cost.getUnitCost())<=0) {
            approval=risks.selectApproved(id,plan.getDraftVersion(),cost.getId());
            if (approval==null || Objects.equals(approval.getReviewedBy(),operatorId) || Objects.equals(approval.getReviewedBy(),plan.getUpdatedBy()))
                throw conflict("价格不高于成本，必须由独立审批人批准当前草稿和成本版本风险");
            if(expectedRiskApprovalId!=null&&!Objects.equals(expectedRiskApprovalId,approval.getId()))throw conflict("风险审批已变化，请刷新");
        }
        var value=new CoursePrivatePlanVersion();BeanUtils.copyProperties(config.getRules(),value);
        value.setId(null);value.setPlanId(id);value.setPlatformId(plan.getPlatformId());value.setVersionNo(plan.getDraftVersion());
        value.setCostVersionId(cost.getId());value.setRiskApprovalId(approval==null?null:approval.getId());
        value.setPublishedAt(time.now());value.setCreatedAt(time.now());value.setCreatedBy(operatorId);
        // 元数据随规则完整冻结，未来改名、改档位不会覆盖已经发布的权益。
        config.setReason(reason);value.setConfigSnapshot(encode(config));
        if (versions.insert(value)!=1) throw conflict("发布版本写入失败");
        plan.setCurrentVersionId(value.getId());plan.setStatus("PUBLISHED");plan.setUpdatedAt(time.now());
        if (plans.updateById(plan)!=1) throw conflict("套餐发布状态更新失败");
        return value;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivatePlan disable(Long id,Integer expectedDraftVersion,Long operatorId,String reason) {
        access.require(operatorId,"course-private-plan:publish");reason(reason);
        var plan=locked(id);modern(plan);draft(plan,expectedDraftVersion);plan.setStatus("DISABLED");plan.setUpdatedAt(time.now());
        // 停止新申请，不修改任何发布规则、周期或已有价格。
        if (plans.updateById(plan)!=1) throw conflict("套餐停用失败");return plan;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateCostVersion verifyCost(Long platformId,BigDecimal unitCost,String evidence,LocalDateTime validFrom,LocalDateTime validUntil,Long operatorId,String reason) {
        access.require(operatorId,"course-private-cost:verify");requireEnabled();platform(platformId);reason(reason);
        if (evidence==null || evidence.isBlank() || evidence.length()>2000) throw invalid("必须填写可追溯的成本核验依据，最多2000字");
        if (validFrom!=null && validFrom.getNano()!=0 || validUntil!=null && validUntil.getNano()!=0 || validFrom!=null && validUntil!=null && !validUntil.isAfter(validFrom)) throw invalid("成本有效期无效");
        var value=new CoursePrivateCostVersion();value.setPlatformId(platformId);value.setUnitCost(CourseReservedWalletService.money(unitCost,true));
        value.setEvidence(evidence);value.setVerifiedBy(operatorId);value.setVerifiedAt(time.now());value.setCreatedAt(time.now());value.setValidFrom(validFrom);value.setValidUntil(validUntil);value.setReason(reason);
        if (costs.insert(value)!=1) throw conflict("成本核验写入失败");return value;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateRiskApproval approveRisk(Long planId,Integer draftVersion,Long operatorId,String reason) {
        return approveRisk(planId,draftVersion,null,operatorId,reason);
    }
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CoursePrivateRiskApproval approveRisk(Long planId,Integer draftVersion,Long expectedCostVersionId,Long operatorId,String reason) {
        access.require(operatorId,"course-private-risk:approve");requireEnabled();reason(reason);
        var plan=locked(planId);modern(plan);draft(plan,draftVersion);
        if (Objects.equals(plan.getUpdatedBy(),operatorId) || Objects.equals(plan.getCreatedBy(),operatorId)) throw conflict("草稿维护人不能审批自己的价格风险");
        var cost=costs.selectLatestVerified(plan.getPlatformId(),time.now());if(cost==null) throw conflict("成本尚未核验");
        if(expectedCostVersionId!=null&&!Objects.equals(expectedCostVersionId,cost.getId()))throw conflict("成本版本已变化，不能审批旧成本风险");
        var existing=risks.selectApproved(planId,draftVersion,cost.getId());if(existing!=null) return existing;
        var value=new CoursePrivateRiskApproval();value.setPlanId(planId);value.setDraftVersion(draftVersion);value.setCostVersionId(cost.getId());value.setReviewedBy(operatorId);value.setReviewedAt(time.now());
        value.setStatus("APPROVED");value.setReason(reason);value.setBizNo("CPR"+UUID.randomUUID().toString().replace("-",""));
        if(risks.insert(value)!=1) throw conflict("风险审批写入失败");return value;
    }

    public IPage<CoursePrivatePlan> adminPlans(Long operatorId,int page,int pageSize,Long platformId,String status) {
        access.require(operatorId,"course-private-plan:read","course-private-plan:manage","course-private-plan:publish");page(page,pageSize);
        var query=new QueryWrapper<CoursePrivatePlan>().eq(platformId!=null,"platform_id",platformId).eq(status!=null&&!status.isBlank(),"status",status).orderByAsc("sort_order","id");
        return plans.selectPage(new Page<>(page,pageSize),query);
    }
    public Map<String,Object> detail(Long id,Long operatorId) {
        access.require(operatorId,"course-private-plan:read","course-private-plan:manage","course-private-plan:publish");var plan=plans.selectById(id);if(plan==null) throw missing();
        var result=new LinkedHashMap<String,Object>();boolean legacy="LEGACY_PERMANENT".equals(plan.getMode());
        result.put("plan",plan);result.put("readOnly",legacy);result.put("versions",versions.selectList(new QueryWrapper<CoursePrivatePlanVersion>().eq("plan_id",id).orderByDesc("version_no")));
        // 迁移兼容套餐没有现代草稿且每个旧合同各有历史版本，只读取原记录，不推导新规则或成本。
        if(legacy){result.put("draftRules",null);result.put("assessment",Map.of("costStatus","LEGACY_PRESERVED"));return result;}
        result.put("impact",Map.of("relatedContracts",plans.countRelatedContracts(id),"relatedPeriods",plans.countRelatedPeriods(id),"activeContracts",plans.countActiveContracts(id,time.now())));
        var config=decode(plan.getDraftConfigJson());result.put("draftRules",config.getRules());result.put("assessment",assessment(plan,config));return result;
    }
    public IPage<Map<String,Object>> visiblePlans(Long userId,int page,int pageSize,Long platformId) {
        access.self(userId);page(page,pageSize);
        return plans.selectPage(new Page<>(page,pageSize),new QueryWrapper<CoursePrivatePlan>().eq("visible_to_agents",true).eq("status","PUBLISHED").eq(platformId!=null,"platform_id",platformId).orderByAsc("sort_order","id"))
                .convert(plan->{var version=versions.selectById(plan.getCurrentVersionId());var result=new LinkedHashMap<String,Object>();
                    if(version==null) throw conflict("已发布套餐版本不存在");var config=decode(version.getConfigSnapshot());
                    result.put("id",plan.getId());result.put("planId",plan.getId());result.put("planVersionId",version.getId());result.put("platformId",version.getPlatformId());
                    result.put("planName",config.getPlanName());result.put("description",config.getDescription());result.put("familyCode",config.getFamilyCode());result.put("tierCode",config.getTierCode());result.put("tierRank",config.getTierRank());result.put("applicationMode",plan.getApplicationMode());result.put("rules",config.getRules());
                    String unavailable=!enabled?"密价运营尚未开放":!activePlatform(version.getPlatformId())?"课程商品已下架":!time.inWindow(time.now(),version.getEffectiveFrom(),version.getEffectiveUntil())?"套餐不在销售有效期内":null;
                    result.put("canApply",unavailable==null);result.put("unavailableReason",unavailable);return result;});
    }
    public IPage<CoursePrivateCostVersion> costList(Long operatorId,int page,int size,Long platformId) { access.require(operatorId,"course-private-cost:verify","course-private-risk:approve","course-private-analytics:read");page(page,size);return costs.selectPage(new Page<>(page,size),new QueryWrapper<CoursePrivateCostVersion>().eq(platformId!=null,"platform_id",platformId).orderByDesc("id")); }
    public IPage<CoursePrivateRiskApproval> riskList(Long operatorId,int page,int size,Long planId) {access.require(operatorId,"course-private-risk:approve","course-private-plan:publish");page(page,size);return risks.selectPage(new Page<>(page,size),new QueryWrapper<CoursePrivateRiskApproval>().eq(planId!=null,"plan_id",planId).orderByDesc("id"));}

    /** 指定代理普通价仅调用统一定价公式，缺少费率不能伪造一个通用普通价。 */
    public Map<String,Object> ordinaryReference(Long id,Long operator,String userUid){
        access.require(operator,"course-private-plan:read","course-private-plan:manage","course-private-plan:publish");
        try{if(userUid==null||!UUID.fromString(userUid).toString().equalsIgnoreCase(userUid))throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){throw invalid("代理UUID格式无效");}
        if(referenceUsers==null||referencePricing==null)throw conflict("普通价参考服务不可用");var plan=plans.selectById(id);if(plan==null)throw missing();modern(plan);var user=referenceUsers.selectOne(new QueryWrapper<User>().eq("uid",userUid));var product=referencePricing.requirePlatform(plan.getPlatformId());var price=referencePricing.ordinaryPrice(user,product);var result=new LinkedHashMap<String,Object>(assessment(plan,decode(plan.getDraftConfigJson())));result.put("ordinaryPrice",price.toPlainString());result.put("fixedPrice",decode(plan.getDraftConfigJson()).getRules().getFixedPrice().toPlainString());result.put("userUid",userUid);return result;
    }

    private Map<String,Object> assessment(CoursePrivatePlan plan,CoursePrivatePlanRequest config) {
        var cost=costs.selectLatestVerified(plan.getPlatformId(),time.now());var result=new LinkedHashMap<String,Object>();var price=config.getRules().getFixedPrice();
        result.put("verifiedUnitCost",cost==null?null:cost.getUnitCost());result.put("costVersionId",cost==null?null:cost.getId());result.put("contributionMargin",cost==null?null:price.subtract(cost.getUnitCost()));
        result.put("marginRate",cost==null?null:price.subtract(cost.getUnitCost()).divide(price,6,RoundingMode.HALF_UP));result.put("ordinaryPrice",null);
        result.put("requiredRiskApproval",cost!=null&&price.compareTo(cost.getUnitCost())<=0);result.put("costStatus",cost==null?"PENDING_CONFIGURATION":"VERIFIED");return result;
    }
    public CoursePrivatePlanRequest decode(String value) {try{return json.readValue(value,CoursePrivatePlanRequest.class);}catch(Exception e){throw conflict("套餐规则快照损坏，需人工核查");}}
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw conflict("套餐规则序列化失败");}}
    private void apply(CoursePrivatePlan plan,CoursePrivatePlanRequest request) {plan.setPlanName(request.getPlanName());plan.setDescription(request.getDescription());plan.setMode(request.getRules().getMode());plan.setFamilyCode(request.getFamilyCode());plan.setTierCode(request.getTierCode());plan.setTierRank(request.getTierRank());plan.setVisibleToAgents(request.isVisibleToAgents());plan.setApplicationMode(request.getApplicationMode());plan.setSortOrder(request.getSortOrder());plan.setDraftConfigJson(encode(request));}
    private void validate(CoursePrivatePlanRequest request) {
        if(request==null||request.getPlatformId()==null||request.getPlatformId()<=0||request.getPlanCode()==null||!request.getPlanCode().matches("[A-Za-z0-9_-]{1,64}")||request.getPlanName()==null||request.getPlanName().isBlank()||request.getPlanName().length()>128||request.getFamilyCode()==null||!request.getFamilyCode().matches("[A-Za-z0-9_-]{1,64}")||request.getTierRank()<0||request.getSortOrder()<0||request.getSortOrder()>1000000||!Set.of("REVIEW","AUTO").contains(Objects.toString(request.getApplicationMode(),"")))throw invalid("套餐配置无效");
        if("__LEGACY_PERMANENT".equals(request.getPlanCode()))throw invalid("兼容套餐编码由系统保留，不能用于新运营套餐");
        if(request.getDescription()!=null&&request.getDescription().length()>2000||request.getTierCode()!=null&&request.getTierCode().length()>64)throw invalid("套餐描述或档位编码过长");
        reason(request.getReason());CoursePrivatePlanRulePolicy.validate(request.getRules());
        if("TIERED_PRICING".equals(request.getRules().getMode())&&(request.getTierCode()==null||request.getTierCode().isBlank()))throw invalid("阶梯套餐必须配置档位编码");
    }
    private void platform(Long id) {if(!activePlatform(id))throw new BusinessException(ResultCode.COURSE_NOT_FOUND);}
    private void lockPlatform(Long id) {if(platforms.selectByIdForUpdate(id)==null)throw new BusinessException(ResultCode.COURSE_NOT_FOUND);platform(id);}
    private boolean activePlatform(Long id) {var value=platforms.selectById(id);if(value==null||!Integer.valueOf(1).equals(value.getStatus()))return false;if(value.getCategoryId()==null)return true;var category=categories.selectById(value.getCategoryId());return category!=null&&Integer.valueOf(1).equals(category.getStatus());}
    private CoursePrivatePlan locked(Long id) {var plan=plans.selectByIdForUpdate(id);if(plan==null) throw missing();return plan;}
    private void draft(CoursePrivatePlan plan,Integer version) {if(version==null||!Objects.equals(plan.getDraftVersion(),version))throw conflict("套餐草稿已变化，请刷新");}
    private void modern(CoursePrivatePlan plan){if("LEGACY_PERMANENT".equals(plan.getMode()))throw conflict("旧版兼容套餐只读，必须通过代理明确申请及审核转换权益");}
    private void requireEnabled() {if(!enabled) throw conflict("课程密价运营功能尚未开启");}
    public static void reason(String value) {if(value==null||value.isBlank()||value.length()>255)throw invalid("必须填写不超过255字的操作原因");}
    public static void page(int page,int size) {if(page<1||size<1||size>100)throw invalid("页码无效，每页数量为1到100");}
    private static BusinessException invalid(String value) {return new BusinessException(ResultCode.PARAM_ERROR,value);}
    private BusinessException conflict(String value) {return new BusinessException(ResultCode.CONFLICT,value);}
    private BusinessException missing() {return new BusinessException(ResultCode.NOT_FOUND,"课程套餐不存在");}
}
