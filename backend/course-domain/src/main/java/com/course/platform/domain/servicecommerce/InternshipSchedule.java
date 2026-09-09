package com.course.platform.domain.servicecommerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * User-visible schedule only. Credentials, location and student identity never enter this snapshot.
 */
public record InternshipSchedule(
        @NotNull LocalDate endDate,
        @NotEmpty @Size(max = 7) List<@Min(1) @Max(7) Integer> weekdays,
        @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?")
                String checkInTime,
        @Pattern(regexp = "|(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?") String checkOutTime,
        @Min(1) @Max(3) int runMode,
        boolean dailyReport,
        boolean weeklyReport,
        boolean monthlyReport,
        boolean skipHolidays,
        boolean randomLocation,
        @Min(1) @Max(7) int weeklyReportDay,
        @Min(0) @Max(31) int monthlyReportDay,
        @Valid ReportLengths reportLengths) {
    public InternshipSchedule {
        if (weekdays != null) weekdays = List.copyOf(weekdays);
    }

    public record ReportLengths(
            @Valid WordRange day,
            @Valid WordRange week,
            @Valid WordRange month,
            @Valid WordRange summary) {}

    public record WordRange(@Min(0) @Max(10000) int minSize, @Min(0) @Max(10000) int maxSize) {}
}
