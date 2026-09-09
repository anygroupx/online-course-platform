package com.course.platform.application.service.servicenotification;

import com.course.platform.domain.servicenotification.ServiceNotificationTypes.*;

public interface ServiceNotificationSender {
    Receipt send(String token, Message message);
}
