package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceFulfillmentMaterialDraft;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceFulfillmentMaterialDraftMapper extends BaseMapper<ServiceFulfillmentMaterialDraft> {
    @Select("SELECT * FROM service_fulfillment_material_draft WHERE id=#{id} FOR UPDATE")
    ServiceFulfillmentMaterialDraft lock(@Param("id") String id);
}
