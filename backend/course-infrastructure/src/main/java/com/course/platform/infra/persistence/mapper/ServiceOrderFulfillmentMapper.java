package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceOrderFulfillment;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceOrderFulfillmentMapper extends BaseMapper<ServiceOrderFulfillment> {
    @Select("SELECT * FROM service_order_fulfillment WHERE order_id=#{orderId} FOR UPDATE")
    ServiceOrderFulfillment lock(@Param("orderId") String orderId);
}
