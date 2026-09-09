package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceAccountSession;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceAccountSessionMapper extends BaseMapper<ServiceAccountSession> {
    @Select("SELECT * FROM service_account_session WHERE id=#{id} FOR UPDATE")
    ServiceAccountSession lock(@Param("id") String id);
}
