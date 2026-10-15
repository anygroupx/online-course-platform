package com.course.platform.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** 权益采用业务时区和连续时长；观察期与购买窗口严格分离。 */
@Component
@RequiredArgsConstructor
public class CoursePrivateOperationsTime {
    private final Clock courseWalletClock;
    public ZoneId zone(){return courseWalletClock.getZone();}
    public LocalDateTime now(){return LocalDateTime.ofInstant(courseWalletClock.instant(),zone()).truncatedTo(ChronoUnit.SECONDS);}
    public LocalDateTime startAt(LocalDateTime qualifiedAt,LocalDateTime allowedAt){return allowedAt!=null && allowedAt.isAfter(qualifiedAt)?allowedAt:qualifiedAt;}
    public LocalDateTime endAt(LocalDateTime start,Integer days){return days==null?null:addDays(start,days);}
    public LocalDateTime settlementDeadline(LocalDateTime end,int graceDays){return end==null?null:addDays(end,graceDays);}
    public boolean inWindow(LocalDateTime current,LocalDateTime start,LocalDateTime end){return (start==null || !current.isBefore(start)) && (end==null || current.isBefore(end));}
    private LocalDateTime addDays(LocalDateTime start,int days){return LocalDateTime.ofInstant(start.atZone(zone()).toInstant().plus(Duration.ofDays(days)),zone());}
}
