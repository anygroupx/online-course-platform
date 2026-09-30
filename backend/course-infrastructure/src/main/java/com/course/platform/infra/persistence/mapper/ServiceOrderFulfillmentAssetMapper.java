package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceOrderFulfillmentAsset;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceOrderFulfillmentAssetMapper extends BaseMapper<ServiceOrderFulfillmentAsset> {
    @Select("SELECT * FROM service_order_fulfillment_asset WHERE id=#{id} FOR UPDATE")
    ServiceOrderFulfillmentAsset lock(@Param("id") String id);
}
