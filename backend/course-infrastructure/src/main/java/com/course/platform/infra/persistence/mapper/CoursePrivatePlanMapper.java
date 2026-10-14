package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivatePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 课程运营套餐草稿及可见性，不含真实资金。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivatePlanMapper extends BaseMapper<CoursePrivatePlan> {
    @Select("SELECT * FROM course_private_plan WHERE id=#{id} FOR UPDATE")
    CoursePrivatePlan selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_plan WHERE platform_id=#{platformId} AND plan_code=#{planCode}")
    CoursePrivatePlan selectByCode(@Param("platformId") Long platformId, @Param("planCode") String planCode);

    /** 旧入口可能加入RR外层事务，锁定读取不能使用早先快照。 */
    @Select("SELECT * FROM course_private_plan WHERE platform_id=#{platformId} AND plan_code=#{planCode} FOR UPDATE")
    CoursePrivatePlan selectByCodeForUpdate(@Param("platformId") Long platformId,@Param("planCode") String planCode);
    /** 产品隐藏兼容套餐并发创建使用唯一代码，不开放销售或编辑能力。 */
    @Insert("INSERT INTO course_private_plan(platform_id,plan_code,plan_name,mode,visible_to_agents,status,created_by,updated_by,created_at,updated_at) VALUES(#{platformId},'__LEGACY_PERMANENT','旧版长期密价兼容','LEGACY_PERMANENT',0,'DISABLED',#{operator},#{operator},#{now},#{now}) ON DUPLICATE KEY UPDATE id=id")
    int ensureLegacy(@Param("platformId") Long platformId,@Param("operator") Long operator,@Param("now") java.time.LocalDateTime now);

    /** 影响范围只返回数量，不暴露代理身份、余额或凭证。 */
    @Select("SELECT COUNT(DISTINCT contract_id) FROM course_private_contract_term WHERE plan_id=#{planId}")
    long countRelatedContracts(@Param("planId") Long planId);
    @Select("SELECT COUNT(*) FROM course_private_contract_term WHERE plan_id=#{planId}")
    long countRelatedPeriods(@Param("planId") Long planId);
    @Select("SELECT COUNT(*) FROM course_private_contract c JOIN course_private_contract_term t ON t.id=c.current_term_id WHERE t.plan_id=#{planId} AND c.status='ACTIVE' AND t.status='ACTIVE' AND t.activated_at IS NOT NULL AND (t.start_at IS NULL OR t.start_at<=#{now}) AND (t.end_at IS NULL OR t.end_at>#{now})")
    long countActiveContracts(@Param("planId") Long planId,@Param("now") java.time.LocalDateTime now);
    /** 含草稿及停用套餐，历史规则关联不能通过删除商品失去追溯。 */
    @Select("SELECT COUNT(*) FROM course_private_plan WHERE platform_id=#{platformId}")
    int countByPlatform(@Param("platformId") Long platformId);

    /** 发布前草稿显式CAS，旧审批不能适用于修改后的版本。 */
    @Update("UPDATE course_private_plan SET draft_config_json=#{plan.draftConfigJson},plan_name=#{plan.planName},description=#{plan.description},mode=#{plan.mode},family_code=#{plan.familyCode},tier_code=#{plan.tierCode},tier_rank=#{plan.tierRank},visible_to_agents=#{plan.visibleToAgents},application_mode=#{plan.applicationMode},sort_order=#{plan.sortOrder},draft_version=draft_version+1,updated_by=#{plan.updatedBy},updated_at=#{now} WHERE id=#{plan.id} AND draft_version=#{expectedVersion}")
    int updateDraft(@Param("plan") CoursePrivatePlan plan, @Param("expectedVersion") Integer expectedVersion, @Param("now") LocalDateTime now);
}
