package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceOrder;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {
    @Select("SELECT * FROM service_order WHERE id = #{id} FOR UPDATE")
    ServiceOrder lock(@Param("id") String id);
}
