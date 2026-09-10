package com.course.platform.domain.servicecommerce;

import com.course.platform.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Non-secret, immutable inputs for a single total-distance order; never a completion counter. */
public record TotalDistancePlan(
        String typeCode, String totalDistance, String schoolName,
        String startTime, String endTime, List<Integer> weekdays) {
    public static final BigDecimal MAX_DISTANCE = new BigDecimal("999999.99");

    public TotalDistancePlan {
        if (!Set.of("0", "1").contains(typeCode == null ? "" : typeCode)
                || totalDistance == null
                || !totalDistance.matches("[0-9]{1,6}(?:\\.[0-9]{1,2})?")
                || schoolName == null || schoolName.isBlank() || schoolName.length() > 120
                || schoolName.codePoints().anyMatch(Character::isISOControl)
                || !clock(startTime) || !clock(endTime) || startTime.compareTo(endTime) >= 0
                || weekdays == null || weekdays.isEmpty() || weekdays.size() > 7
                || weekdays.stream().anyMatch(d -> d == null || d < 1 || d > 7)
                || new HashSet<>(weekdays).size() != weekdays.size())
            throw new BusinessException("总公里计划不完整：请选择有效时段和不重复的星期");
        totalDistance = distance(new BigDecimal(totalDistance)).toPlainString();
        weekdays = weekdays.stream().sorted().toList();
    }

    public static BigDecimal distance(BigDecimal value) {
        if (value == null || value.compareTo(new BigDecimal("0.01")) < 0
                || value.compareTo(MAX_DISTANCE) > 0 || value.stripTrailingZeros().scale() > 2)
            throw new BusinessException("总公里数须为 0.01–999999.99，最多两位小数");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean clock(String value) {
        return value != null && value.matches("(?:0[6-9]|1[0-9]|2[0-2]):[0-5][0-9]");
    }
}
