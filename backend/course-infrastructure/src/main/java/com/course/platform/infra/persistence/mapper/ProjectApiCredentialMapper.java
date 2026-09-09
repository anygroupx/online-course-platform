package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectApiCredential;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectApiCredentialMapper extends BaseMapper<ProjectApiCredential> {
    @Select("SELECT * FROM project_api_credential WHERE id=#{id} FOR UPDATE")
    ProjectApiCredential lock(@Param("id") String id);
}
