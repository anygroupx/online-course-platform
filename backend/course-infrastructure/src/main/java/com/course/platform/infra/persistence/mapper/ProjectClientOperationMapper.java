package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectClientOperation;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectClientOperationMapper extends BaseMapper<ProjectClientOperation> {
    @Select("SELECT * FROM project_client_operation WHERE id=#{id} FOR UPDATE")
    ProjectClientOperation lock(@Param("id") String id);

    record Totals(long count, java.math.BigDecimal debited, java.math.BigDecimal returned) {}

    @Select(
            "SELECT COUNT(*) AS count,COALESCE(SUM(CASE WHEN action IN ('OPEN','TOP_UP') THEN"
                + " amount ELSE 0 END),0) AS debited,COALESCE(SUM(CASE WHEN action='WITHDRAW' THEN"
                + " amount ELSE 0 END),0) AS returned FROM project_client_operation WHERE"
                + " owner_id=#{owner} AND state='APPLIED'")
    Totals totals(@Param("owner") Long owner);
}
