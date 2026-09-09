package com.course.platform.domain.catalogrefresh;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Existing-course updates are deliberately separate from catalogue imports and student orders. */
public final class CatalogRefreshTypes {
    private CatalogRefreshTypes() {}

    public record PreviewForm(
            @NotNull @Positive Long providerId,
            @NotBlank @Pattern(regexp = "ALL_EXISTING|SELECTED") String scope,
            @NotNull @DecimalMin("0.0001") @DecimalMax("1000") @Digits(integer = 4, fraction = 4)
                    BigDecimal multiplier,
            @Size(max = 50) String categoryId,
            @Size(max = 100) List<@NotBlank @Size(max = 50) String> skipCategoryIds,
            @Size(max = 500) List<@NotBlank @Size(max = 50) String> productIds) {}

    public record ConfirmForm(boolean consent) {}

    public record Row(
            Long localId,
            String remoteId,
            String title,
            String oldPrice,
            String newPrice,
            String oldDescription,
            String newDescription,
            boolean descriptionProvided,
            boolean changed) {}

    public record Plan(
            String multiplier,
            String scope,
            String categoryId,
            List<String> skipCategoryIds,
            List<Row> rows,
            int selectedMissing,
            int notImported,
            int excluded) {}

    public record View(
            String id,
            Long providerId,
            String state,
            LocalDateTime expiresAt,
            LocalDateTime appliedAt,
            Plan plan,
            String notice) {}
}
