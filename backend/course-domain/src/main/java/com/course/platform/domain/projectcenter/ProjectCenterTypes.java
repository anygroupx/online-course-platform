package com.course.platform.domain.projectcenter;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Public projections contain neither supplier/customer keys nor raw upstream response bodies. */
public final class ProjectCenterTypes {
    private ProjectCenterTypes() {}

    public record CatalogItem(String id, String name, BigDecimal basePrice) {}

    public record ProjectForm(
            @NotNull Long providerId,
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String remoteProjectId,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.000001") @DecimalMax("9999") @Digits(integer = 4, fraction = 6)
                    BigDecimal unitPrice,
            @NotNull @DecimalMin("0.000001") @DecimalMax("9999") @Digits(integer = 4, fraction = 6)
                    BigDecimal unitCost,
            @NotNull LocalDate validUntil,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean upstreamChecked,
            boolean enabled,
            Long version) {}

    public record ProjectView(
            Long id,
            Long providerId,
            String remoteProjectId,
            String title,
            String description,
            String basePrice,
            String unitPrice,
            String unitCost,
            LocalDate validUntil,
            boolean enabled,
            boolean available,
            Long version,
            AccountView account) {}

    public record AccountView(
            String id,
            Long projectId,
            String state,
            String unitPrice,
            String remoteBalance,
            String refundableUnits,
            String refundBudget,
            LocalDateTime balanceCheckedAt,
            String pendingOperationId,
            boolean ticketsAvailable) {}

    public record QuoteForm(
            @Pattern(regexp = "PROVISION|TOP_UP|WITHDRAW") @NotBlank String action,
            @DecimalMin("0") @DecimalMax("100000") @Digits(integer = 6, fraction = 6)
                    BigDecimal units,
            boolean confirmedPolicy) {}

    public record OperationView(
            String id,
            String accountId,
            Long projectId,
            String projectTitle,
            Long userId,
            String action,
            String state,
            String units,
            String unitPrice,
            String amount,
            String balanceAfter,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            List<String> warnings) {}

    public record ResolveForm(
            @NotBlank @Pattern(regexp = "ACCEPTED|NOT_ACCEPTED") String outcome,
            @Pattern(regexp = "[1-9][0-9]{0,18}") String customerId,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean upstreamChecked) {}

    public record CustomerReceipt(
            String id, String projectId, String apiKey, BigDecimal balance, boolean enabled) {
        @Override
        public String toString() {
            return "CustomerReceipt[REDACTED]";
        }
    }

    public record AdjustmentReceipt(BigDecimal balanceAfter, BigDecimal actualPrice) {}
}
