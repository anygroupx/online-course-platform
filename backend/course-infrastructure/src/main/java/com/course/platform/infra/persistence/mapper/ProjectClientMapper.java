package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectClient;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectClientMapper extends BaseMapper<ProjectClient> {
    @Select("SELECT * FROM project_client WHERE id=#{id} FOR UPDATE")
    ProjectClient lock(@Param("id") String id);
}
