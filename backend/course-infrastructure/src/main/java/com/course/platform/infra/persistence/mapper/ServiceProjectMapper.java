package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectcenter.ServiceProject;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceProjectMapper extends BaseMapper<ServiceProject> {
    @Select("SELECT * FROM service_project WHERE id = #{id} FOR UPDATE")
    ServiceProject lock(@Param("id") Long id);
}
