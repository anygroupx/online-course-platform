package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectClientTicket;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectClientTicketMapper extends BaseMapper<ProjectClientTicket> {
    @Select("SELECT * FROM project_client_ticket WHERE id=#{id} FOR UPDATE")
    ProjectClientTicket lock(@Param("id") String id);
}
