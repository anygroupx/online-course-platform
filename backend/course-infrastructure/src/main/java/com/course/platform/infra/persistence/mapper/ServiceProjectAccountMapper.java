package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.projectcenter.ServiceProjectAccount;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceProjectAccountMapper extends BaseMapper<ServiceProjectAccount> {
    @Select("SELECT * FROM service_project_account WHERE id = #{id} FOR UPDATE")
    ServiceProjectAccount lock(@Param("id") String id);
}
