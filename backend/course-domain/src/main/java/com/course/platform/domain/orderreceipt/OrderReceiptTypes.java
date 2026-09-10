package com.course.platform.domain.orderreceipt;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** Receipt recovery is a local association, not an order submission or payment. */
public final class OrderReceiptTypes {
    private OrderReceiptTypes() {}

    public record PreviewForm(
            @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,50}") String receiptId,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean ownershipConfirmed) {}
    public record ConfirmForm(boolean consent) {}
    public record View(String id, Long orderId, String orderNo, String courseName, String receiptId,
                       String state, LocalDateTime expiresAt, LocalDateTime appliedAt, String notice) {}
    /** No remote credentials or private response fields survive verification. */
    public record Verified(String receiptId) {}
}
