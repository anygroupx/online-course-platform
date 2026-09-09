package com.course.platform.application.service.servicenotification;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;

public interface ServiceNotificationService {
    SettingsView settings(String orderId);

    SettingsView configure(String orderId, ConfigureForm form);

    SettingsView challenge(String orderId, VersionForm form);

    SettingsView verify(String orderId, VerifyForm form);

    SettingsView disconnect(String orderId, VersionForm form);

    IPage<DeliveryView> deliveries(String orderId, int page, int size);

    DeliveryView delivery(String id);
}
