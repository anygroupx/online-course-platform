package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectcenter.ServiceProjectTicketOperation;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceProjectTicketOperationMapper
        extends BaseMapper<ServiceProjectTicketOperation> {
    @Select("SELECT * FROM service_project_ticket_operation WHERE id = #{id} FOR UPDATE")
    ServiceProjectTicketOperation lock(@Param("id") String id);
}
