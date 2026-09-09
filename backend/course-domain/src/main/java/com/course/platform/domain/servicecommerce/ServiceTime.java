package com.course.platform.domain.servicecommerce;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Supplier schedules and quote deadlines use Beijing time, independently of the host timezone. */
public final class ServiceTime {
    private ServiceTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
}
