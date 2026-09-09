package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectApiCall;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectApiCallMapper extends BaseMapper<ProjectApiCall> {
    @Select("SELECT * FROM project_api_call WHERE id=#{id} FOR UPDATE")
    ProjectApiCall lock(@Param("id") String id);
}
