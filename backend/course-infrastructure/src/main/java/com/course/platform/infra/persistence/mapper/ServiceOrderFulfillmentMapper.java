package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceOrderFulfillment;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceOrderFulfillmentMapper extends BaseMapper<ServiceOrderFulfillment> {}
