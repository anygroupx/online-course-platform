package com.course.platform.domain.projectcenter;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bound support DTOs. Raw images appear only in encrypted internal receipts/requests, never public views.
 */
public final class ProjectTicketTypes {
    private ProjectTicketTypes() {}

    public record SubmitForm(
            @NotBlank @Pattern(regexp = "suggestion|bug|compensation") String type,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 4000) String description,
            @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 6)
                    BigDecimal compensationAmount,
            boolean confirmedPolicy,
            @Size(max = 2_796_256) String imageData) {
        public SubmitForm(String type, String title, String description, BigDecimal compensationAmount, boolean confirmedPolicy) {
            this(type, title, description, compensationAmount, confirmedPolicy, null);
        }
        @Override
        public String toString() {
            return "SubmitForm[REDACTED]";
        }
    }

    public record ReplyForm(
            @Size(max = 4000) String content,
            @NotNull Long version,
            boolean confirmedPolicy,
            @Size(max = 2_796_256) String imageData) {
        public ReplyForm(String content, Long version, boolean confirmedPolicy) {
            this(content, version, confirmedPolicy, null);
        }
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

    /** Internal supplier reply; only ReplyView is returned by controllers. */
    public record Reply(
            String id, String sender, String content, String createdAt, boolean hasAttachment, String imageData) {
        public Reply(String id, String sender, String content, String createdAt, boolean hasAttachment) {
            this(id, sender, content, createdAt, hasAttachment, null);
        }
        @Override public String toString() { return "TicketReply[REDACTED]"; }
    }

    public record ReplyView(
            String id, String sender, String content, String createdAt, boolean hasAttachment, boolean attachmentAvailable) {}

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
            List<Reply> replies,
            String imageData) {
        public Receipt(String id, String projectId, String type, String title, String description,
                BigDecimal compensationAmount, String status, String reviewResult, String reviewNote,
                String createdAt, String updatedAt, boolean hasAttachment, List<Reply> replies) {
            this(id, projectId, type, title, description, compensationAmount, status, reviewResult,
                    reviewNote, createdAt, updatedAt, hasAttachment, replies, null);
        }
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
            List<ReplyView> replies,
            Long version,
            String pendingOperationId,
            LocalDateTime createdAt,
            LocalDateTime checkedAt,
            boolean attachmentAvailable) {}

    public record OperationView(
            String id,
            String ticketId,
            String action,
            String state,
            String content,
            String reviewResult,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            List<String> warnings,
            boolean hasAttachment) {}
}
