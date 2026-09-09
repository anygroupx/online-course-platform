package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectcenter.ServiceProjectTicket;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceProjectTicketMapper extends BaseMapper<ServiceProjectTicket> {
    @Select("SELECT * FROM service_project_ticket WHERE id = #{id} FOR UPDATE")
    ServiceProjectTicket lock(@Param("id") String id);
}
