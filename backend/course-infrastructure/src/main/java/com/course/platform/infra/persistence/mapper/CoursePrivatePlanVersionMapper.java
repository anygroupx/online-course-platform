package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivatePlanVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 不可变发布规则与成本审批快照。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivatePlanVersionMapper extends BaseMapper<CoursePrivatePlanVersion> {
    /** 在共享套餐锁后获取当前版本号，避免RR旧快照重复分配。 */
    @Select("SELECT version_no FROM course_private_plan_version WHERE plan_id=#{planId} ORDER BY version_no DESC LIMIT 1 FOR UPDATE")
    Integer latestVersionNoForUpdate(@Param("planId") Long planId);
    @Select("SELECT * FROM course_private_plan_version WHERE id=#{id} FOR UPDATE")
    CoursePrivatePlanVersion selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_plan_version WHERE plan_id=#{planId} AND version_no=#{versionNo}")
    CoursePrivatePlanVersion selectByPlanAndVersion(@Param("planId") Long planId, @Param("versionNo") Integer versionNo);
    @Select("SELECT * FROM course_private_plan_version WHERE plan_id=#{planId} AND published_at IS NOT NULL ORDER BY version_no DESC LIMIT 1")
    CoursePrivatePlanVersion selectLatestPublished(@Param("planId") Long planId);
    /** 连续续期按下一期起点选择当时已发布且有效的版本，迟到执行不能追溯采用未来发布价格。 */
    @Select("SELECT * FROM course_private_plan_version WHERE plan_id=#{planId} AND published_at<=#{anchor} AND (effective_from IS NULL OR effective_from<=#{anchor}) AND (effective_until IS NULL OR effective_until>#{anchor}) ORDER BY version_no DESC LIMIT 1")
    CoursePrivatePlanVersion selectLatestAt(@Param("planId") Long planId,@Param("anchor") LocalDateTime anchor);
    @Select("SELECT * FROM course_private_plan_version WHERE legacy_contract_id=#{contractId}")
    CoursePrivatePlanVersion selectByLegacyContract(@Param("contractId") Long contractId);
}
