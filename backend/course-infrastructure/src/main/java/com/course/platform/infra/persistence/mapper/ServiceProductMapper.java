package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceProduct;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceProductMapper extends BaseMapper<ServiceProduct> {
    @Select("SELECT * FROM service_product WHERE id = #{id} FOR UPDATE")
    ServiceProduct lock(@Param("id") Long id);
}
