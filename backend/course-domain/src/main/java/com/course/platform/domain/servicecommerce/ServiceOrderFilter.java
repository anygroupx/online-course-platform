package com.course.platform.domain.servicecommerce;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Local order discovery only. Never accepts a credential or a supplier query. */
public record ServiceOrderFilter(
        @Size(max = 100) String keyword,
        @Pattern(regexp = "|flash|heisha|jiguang|wuxin|sxdk_tw|appui|leidian|jingyu|ssbenz_xbd") String providerType,
        @Pattern(regexp = "|ACTIVE|PAUSED|COMPLETED|REFUNDED|CANCELLED|CONFIRMING|REFUND_REVIEW|ATTENTION|SUBMITTING|SUBMITTED|SUBMISSION_REVIEW") String status,
        @Min(1) Long ownerId,
        @Pattern(regexp = "|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String orderId,
        @Pattern(regexp = "|[0-9]{4}-[0-9]{2}-[0-9]{2}") String createdFrom,
        @Pattern(regexp = "|[0-9]{4}-[0-9]{2}-[0-9]{2}") String createdTo) {
    public static ServiceOrderFilter empty() {
        return new ServiceOrderFilter(null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return blank(keyword) && blank(providerType) && blank(status) && ownerId == null
                && blank(orderId) && blank(createdFrom) && blank(createdTo);
    }

    private static boolean blank(String value) { return value == null || value.isEmpty(); }

    @Override
    public String toString() { return "ServiceOrderFilter[REDACTED]"; }
}
