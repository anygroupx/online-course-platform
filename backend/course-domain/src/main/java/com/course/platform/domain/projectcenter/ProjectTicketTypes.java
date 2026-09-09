package com.course.platform.domain.projectcenter;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Text-only support. Attachment URLs, credentials and supplier response bodies are never exposed.
 */
public final class ProjectTicketTypes {
    private ProjectTicketTypes() {}

    public record SubmitForm(
            @NotBlank @Pattern(regexp = "suggestion|bug|compensation") String type,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 4000) String description,
            @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 6)
                    BigDecimal compensationAmount,
            boolean confirmedPolicy) {
        @Override
        public String toString() {
            return "SubmitForm[REDACTED]";
        }
    }

    public record ReplyForm(
            @NotBlank @Size(max = 4000) String content,
            @NotNull Long version,
            boolean confirmedPolicy) {
        @Override
        public String toString() {
            return "ReplyForm[REDACTED]";
        }
    }

    public record ReviewForm(
            @NotBlank @Pattern(regexp = "approved|rejected") String result,
            @NotBlank @Size(min = 10, max = 1000) String note,
            @NotNull Long version,
            boolean upstreamChecked) {
        @Override
        public String toString() {
            return "ReviewForm[REDACTED]";
        }
    }

    public record ResolveForm(
            @NotBlank @Pattern(regexp = "ACCEPTED|NOT_ACCEPTED") String outcome,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean upstreamChecked) {
        @Override
        public String toString() {
            return "TicketResolveForm[REDACTED]";
        }
    }

    public record Reply(
            String id, String sender, String content, String createdAt, boolean hasAttachment) {}

    public record Receipt(
            String id,
            String projectId,
            String type,
            String title,
            String description,
            BigDecimal compensationAmount,
            String status,
            String reviewResult,
            String reviewNote,
            String createdAt,
            String updatedAt,
            boolean hasAttachment,
            List<Reply> replies) {
        @Override
        public String toString() {
            return "TicketReceipt[REDACTED]";
        }
    }

    public record TicketView(
            String id,
            String accountId,
            Long projectId,
            String projectTitle,
            Long userId,
            String state,
            String type,
            String title,
            String description,
            String compensationAmount,
            String status,
            String reviewResult,
            String reviewNote,
            boolean hasAttachment,
            List<Reply> replies,
            Long version,
            String pendingOperationId,
            LocalDateTime createdAt,
            LocalDateTime checkedAt) {}

    public record OperationView(
            String id,
            String ticketId,
            String action,
            String state,
            String content,
            String reviewResult,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            List<String> warnings) {}
}
