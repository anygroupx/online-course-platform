package com.course.platform.domain.projectclient;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProjectClientTicketTypes {
    private ProjectClientTicketTypes() {}

    public record CreateForm(
            @NotBlank @Pattern(regexp = "[0-9a-f-]{36}") String requestId,
            @NotBlank @Pattern(regexp = "[0-9a-f-]{36}") String clientId,
            @NotBlank @Pattern(regexp = "SUGGESTION|BUG|COMPENSATION") String kind,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
                    BigDecimal requestedAmount,
            boolean consent) {
        @Override public String toString() { return "CreateClientTicket[REDACTED]"; }
    }

    public record ReplyForm(
            @NotBlank @Pattern(regexp = "[0-9a-f-]{36}") String requestId,
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 5000) String content,
            boolean consent) {
        @Override public String toString() { return "ReplyClientTicket[REDACTED]"; }
    }

    public record DecisionForm(
            @NotBlank @Pattern(regexp = "[0-9a-f-]{36}") String requestId,
            @NotNull @Min(0) Long version,
            @NotBlank @Pattern(regexp = "RESOLVE|CLOSE|APPROVE|REJECT") String action,
            @NotBlank @Size(max = 2000) String note,
            boolean consent) {
        @Override public String toString() { return "DecideClientTicket[REDACTED]"; }
    }

    public record TicketView(
            String id, String clientId, Long projectId, String projectTitle,
            String kind, String title, String description, String requestedAmount,
            String status, long version, String reviewResult, String reviewNote,
            LocalDateTime reviewedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ReplyView(
            String id, long version, String author, String content, LocalDateTime createdAt) {}

    /** An immutable local receipt. A successful compensation review never means money was paid. */
    public record Receipt(
            String requestId, String ticketId, String action, long version,
            LocalDateTime createdAt, String notice) {}
}
