package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectClientTicketImage;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectClientTicketImageMapper extends BaseMapper<ProjectClientTicketImage> {
    @Select("SELECT content_encrypted FROM project_client_ticket_image WHERE id=#{id} AND ticket_id=#{ticketId}")
    String content(@Param("id") String id, @Param("ticketId") String ticketId);
}
