package com.course.platform.domain.servicecommerce;

import com.course.platform.common.exception.BusinessException;

import java.time.LocalDate;
import java.util.*;

/** Frozen paid date entitlements: editing/removing days never silently refunds or charges twice. */
public record DailyServicePlan(
        LocalDate startDate, InternshipSchedule schedule, List<LocalDate> paidDates) {
    public static final int MAX_CALENDAR_DAYS = 365;

    public DailyServicePlan {
        if (startDate == null
                || schedule == null
                || schedule.endDate() == null
                || schedule.endDate().isBefore(startDate)
                || paidDates == null
                || paidDates.isEmpty()
                || paidDates.size() > 9999
                || paidDates.stream().anyMatch(d -> d == null || d.isBefore(startDate))
                || new HashSet<>(paidDates).size() != paidDates.size())
            throw new BusinessException("实习服务日快照不完整，请联系管理员核对");
        validate(schedule, schedule.endDate());
        paidDates = List.copyOf(paidDates);
    }

    public static DailyServicePlan create(InternshipSchedule schedule, LocalDate today) {
        List<LocalDate> dates = dates(today, schedule);
        if (dates.isEmpty()) throw new BusinessException("当前周期内没有服务日");
        return new DailyServicePlan(today, schedule, dates);
    }

    public DailyServicePlan revise(InternshipSchedule updated, LocalDate today) {
        if (updated.runMode() != schedule.runMode())
            throw new BusinessException("运行方式影响计价，已有订单不能通过编辑切换运行方式");
        Set<LocalDate> paid = new TreeSet<>(paidDates);
        paid.addAll(dates(today, updated));
        if (paid.size() > 9999) throw new BusinessException("累计服务天数超出范围");
        return new DailyServicePlan(startDate, updated, List.copyOf(paid));
    }

    public int additionalDays(DailyServicePlan previous) {
        return paidDates.size() - previous.paidDates.size();
    }

    public int refundableDays(LocalDate today) {
        if (schedule.endDate().isBefore(today)) return 0;
        LocalDate first = today.isBefore(startDate) ? startDate : today;
        return (int)
                paidDates.stream()
                        .filter(
                                d ->
                                        !d.isBefore(first)
                                                && !d.isAfter(schedule.endDate())
                                                && schedule.weekdays()
                                                        .contains(d.getDayOfWeek().getValue()))
                        .count();
    }

    public static List<LocalDate> dates(LocalDate start, InternshipSchedule schedule) {
        validate(schedule, start);
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(schedule.endDate()); d = d.plusDays(1))
            if (schedule.weekdays().contains(d.getDayOfWeek().getValue())) dates.add(d);
        return List.copyOf(dates);
    }

    public static void validate(InternshipSchedule s, LocalDate today) {
        if (s == null
                || s.endDate() == null
                || s.endDate().isBefore(today)
                || s.endDate().isAfter(today.plusDays(MAX_CALENDAR_DAYS - 1))
                || s.weekdays() == null
                || s.weekdays().isEmpty()
                || s.weekdays().size() > 7
                || s.weekdays().stream().anyMatch(d -> d == null || d < 1 || d > 7)
                || new HashSet<>(s.weekdays()).size() != s.weekdays().size()
                || !clock(s.checkInTime())
                || (s.checkOutTime() != null
                        && !s.checkOutTime().isEmpty()
                        && !clock(s.checkOutTime()))
                || s.runMode() < 1
                || s.runMode() > 3
                || s.weeklyReportDay() < 1
                || s.weeklyReportDay() > 7
                || s.monthlyReportDay() < 0
                || s.monthlyReportDay() > 31) throw new BusinessException("实习周期、星期或执行时间不合法");
        if (s.reportLengths() != null) {
            for (var range :
                    Arrays.asList(
                            s.reportLengths().day(),
                            s.reportLengths().week(),
                            s.reportLengths().month(),
                            s.reportLengths().summary()))
                if (range != null
                        && (range.minSize() < 0
                                || range.maxSize() > 10000
                                || range.minSize() > range.maxSize()))
                    throw new BusinessException("报告字数范围不合法");
        }
    }

    private static boolean clock(String v) {
        return v != null && v.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?");
    }
}
