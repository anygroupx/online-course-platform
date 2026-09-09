package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicenotification.ServiceNotificationDelivery;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ServiceNotificationDeliveryMapper extends BaseMapper<ServiceNotificationDelivery> {
    @Select("SELECT * FROM service_notification_delivery WHERE id=#{id} FOR UPDATE")
    ServiceNotificationDelivery lock(@Param("id") String id);

    @Select(
            "SELECT id FROM service_notification_delivery WHERE state='READY' ORDER BY"
                    + " update_time,id LIMIT 20")
    List<String> readyBatch();
}
