package com.course.platform.domain.projectclient;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProjectClientTypes {
    private ProjectClientTypes() {}

    /** Created only by the authenticated web or API credential resolver, never request-bound. */
    public record Caller(
            Long ownerId,
            String credentialId,
            Long credentialVersion,
            String clientId,
            boolean manage) {}

    public record QuoteForm(
            @NotBlank @Pattern(regexp = "[0-9a-f-]{36}") String requestId,
            @NotBlank @Pattern(regexp = "OPEN|TOP_UP|WITHDRAW") String action,
            @NotNull @Positive Long projectId,
            @Size(max = 36) String clientId,
            @Size(max = 100) String label,
            @NotNull @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 6)
                    BigDecimal units,
            boolean consent) {}

    public record ConfirmForm(boolean consent) {}

    public record StatusForm(
            @NotNull @Min(0) Long version,
            @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|CLOSED") String status,
            boolean consent) {}

    public record ClientView(
            String id,
            Long projectId,
            String projectTitle,
            String label,
            String status,
            long version,
            String balance,
            String unitPrice,
            String refundableUnits,
            String refundBudget,
            LocalDateTime createdAt) {}

    public record ProjectView(
            Long id, String title, String unitPrice, boolean available, long version) {}

    public record OperationView(
            String id,
            String requestId,
            String clientId,
            Long projectId,
            String projectTitle,
            String label,
            String action,
            String state,
            String units,
            String unitPrice,
            String amount,
            String clientBalanceAfter,
            String walletBalanceAfter,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            String notice) {}

    public record BalanceView(
            String id, Long projectId, String projectTitle, String status, String balance) {}

    public record Stats(
            long customers,
            long active,
            long appliedOperations,
            String totalDebited,
            String totalReturned) {}

    public record KeyForm(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Pattern(regexp = "READ_ONLY|MANAGE|SUPPORT") String access,
            @Min(1) @Max(365) int days,
            boolean consent) {
        @Override
        public String toString() {
            return "ProjectKeyForm[REDACTED]";
        }
    }

    public record KeyRevoke(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 200) String password,
            boolean consent) {
        @Override
        public String toString() {
            return "ProjectKeyRevoke[REDACTED]";
        }
    }

    public record KeyView(
            boolean configured,
            String prefix,
            String access,
            long version,
            LocalDateTime expiresAt,
            String subject) {}

    /** Dedicated one-time issuance receipt. Never returned by GET/list/history. */
    public record IssuedKey(String secret, KeyView settings) {
        @Override
        public String toString() {
            return "IssuedProjectKey[REDACTED]";
        }
    }

    public record ApiCallView(String action, String outcome, LocalDateTime createdAt) {}
}
