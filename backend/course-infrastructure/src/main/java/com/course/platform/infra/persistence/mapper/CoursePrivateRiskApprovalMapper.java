package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateRiskApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 草稿和成本版本绑定的独立风险审批。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateRiskApprovalMapper extends BaseMapper<CoursePrivateRiskApproval> {
    @Select("SELECT * FROM course_private_risk_approval WHERE id=#{id} FOR UPDATE")
    CoursePrivateRiskApproval selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_risk_approval WHERE plan_id=#{planId} AND draft_version=#{draftVersion} AND cost_version_id=#{costVersionId} AND status='APPROVED' ORDER BY id DESC LIMIT 1")
    CoursePrivateRiskApproval selectApproved(@Param("planId") Long planId, @Param("draftVersion") Integer draftVersion, @Param("costVersionId") Long costVersionId);
}
