package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicenotification.ServiceNotificationPreference;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ServiceNotificationPreferenceMapper
        extends BaseMapper<ServiceNotificationPreference> {
    @Select("SELECT * FROM service_notification_preference WHERE order_id=#{id} FOR UPDATE")
    ServiceNotificationPreference lock(@Param("id") String id);

    @Select("SELECT status FROM sys_user WHERE id=#{id}")
    Integer ownerStatus(@Param("id") Long id);

    @Select(
            "SELECT order_id FROM service_notification_preference WHERE enabled=TRUE ORDER BY"
                    + " scanned_at,order_id LIMIT 100")
    List<String> scanBatch();
}
