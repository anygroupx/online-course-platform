package com.course.platform.infra.servicecommerce;

import static org.junit.jupiter.api.Assertions.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class DailyServicePlanTest {
    private InternshipSchedule schedule(LocalDate end, List<Integer> days, int mode) {
        return new InternshipSchedule(
                end, days, "08:00", "18:00", mode, false, true, false, true, false, 7, 0, null);
    }

    @Test
    void beijingCalendarCountsInclusiveLeapDaysAndDoesNotInventHolidayExemptions() {
        LocalDate start = LocalDate.of(2028, 2, 28);
        var p =
                DailyServicePlan.create(
                        schedule(start.plusDays(3), List.of(1, 2, 3, 4, 5, 6, 7), 1), start);
        assertEquals(4, p.paidDates().size());
        assertTrue(p.paidDates().contains(LocalDate.of(2028, 2, 29)));
        assertEquals(3, p.refundableDays(start.plusDays(1)));
    }

    @Test
    void shrinkingAndRestoringPaidDatesNeverChargesThemAgain() {
        var start = LocalDate.of(2026, 9, 7);
        var all = List.of(1, 2, 3, 4, 5, 6, 7);
        var p = DailyServicePlan.create(schedule(start.plusDays(6), all, 1), start);
        var reduced = p.revise(schedule(start.plusDays(2), all, 1), start);
        assertEquals(0, reduced.additionalDays(p));
        assertEquals(3, reduced.refundableDays(start));
        var restored = reduced.revise(schedule(start.plusDays(6), all, 1), start);
        assertEquals(0, restored.additionalDays(reduced));
        var extended = restored.revise(schedule(start.plusDays(9), all, 1), start);
        assertEquals(3, extended.additionalDays(restored));
    }

    @Test
    void swappingWeekdaysChargesOnlyPreviouslyUnboughtDatesNotJustTheDifferenceInCounts() {
        var start = LocalDate.of(2026, 9, 7);
        var p = DailyServicePlan.create(schedule(start.plusDays(6), List.of(1, 3, 5), 1), start);
        var q = p.revise(schedule(start.plusDays(6), List.of(2, 4, 6), 1), start);
        assertEquals(3, q.additionalDays(p));
        assertEquals(3, q.refundableDays(start));
    }

    @Test
    void outOfRangeDuplicateWeekdaysAndPriceMultiplierChangesAreRejected() {
        var start = LocalDate.of(2026, 9, 7);
        var p = DailyServicePlan.create(schedule(start.plusDays(6), List.of(1, 3, 5), 1), start);
        assertThrows(
                BusinessException.class,
                () -> DailyServicePlan.create(schedule(start.minusDays(1), List.of(1), 1), start));
        assertThrows(
                BusinessException.class,
                () -> DailyServicePlan.create(schedule(start.plusDays(366), List.of(1), 1), start));
        assertThrows(
                BusinessException.class,
                () ->
                        DailyServicePlan.create(
                                schedule(start.plusDays(6), List.of(1, 1), 1), start));
        assertThrows(
                BusinessException.class,
                () -> p.revise(schedule(start.plusDays(6), List.of(1, 3, 5), 3), start));
        assertEquals(0, p.refundableDays(start.plusDays(7)));
    }

    @Test
    void maximumPeriodIs365InclusiveDaysNot366() {
        var start = LocalDate.of(2028, 2, 28);
        var days = List.of(1, 2, 3, 4, 5, 6, 7);
        assertEquals(
                365,
                DailyServicePlan.create(schedule(start.plusDays(364), days, 1), start)
                        .paidDates()
                        .size());
        assertThrows(
                BusinessException.class,
                () -> DailyServicePlan.create(schedule(start.plusDays(365), days, 1), start));
    }

    @Test
    void corruptedOrMutablePaidDateSnapshotsCannotChangeEntitlements() {
        var start = LocalDate.of(2026, 9, 7);
        var s = schedule(start.plusDays(2), List.of(1, 2, 3, 4, 5, 6, 7), 1);
        var dates = new java.util.ArrayList<>(List.of(start));
        var plan = new DailyServicePlan(start, s, dates);
        dates.add(start.plusDays(1));
        assertEquals(List.of(start), plan.paidDates());
        assertThrows(BusinessException.class, () -> new DailyServicePlan(null, s, List.of(start)));
        assertThrows(
                BusinessException.class,
                () -> new DailyServicePlan(start, s, List.of(start, start)));
        assertThrows(
                BusinessException.class,
                () -> new DailyServicePlan(start, s, List.of(start.minusDays(1))));
        assertThrows(BusinessException.class, () -> new DailyServicePlan(start, s, List.of()));
    }
}
