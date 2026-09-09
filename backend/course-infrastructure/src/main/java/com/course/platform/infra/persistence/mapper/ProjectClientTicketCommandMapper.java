package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectclient.ProjectClientTicketCommand;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectClientTicketCommandMapper extends BaseMapper<ProjectClientTicketCommand> {
}
