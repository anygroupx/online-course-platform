package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectcenter.ServiceProjectOperation;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceProjectOperationMapper extends BaseMapper<ServiceProjectOperation> {
    @Select("SELECT * FROM service_project_operation WHERE id = #{id} FOR UPDATE")
    ServiceProjectOperation lock(@Param("id") String id);
}
