package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceOperation;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceOperationMapper extends BaseMapper<ServiceOperation> {
    @Select("SELECT * FROM service_order_operation WHERE id = #{id} FOR UPDATE")
    ServiceOperation lock(@Param("id") String id);
}
