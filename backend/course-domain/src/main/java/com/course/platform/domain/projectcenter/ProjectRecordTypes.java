package com.course.platform.domain.projectcenter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only operational projections; never serialize underlying users, credentials or payloads. */
public final class ProjectRecordTypes {
    private ProjectRecordTypes() {}

    public record Owner(Long id, String username, String status, long activeAccounts, long activeClients,
                        String accountDebited, String accountReturned, String clientDebited, String clientReturned,
                        LocalDateTime lastActivity) {}
    public record OwnerDetail(Owner owner, ProjectReportTypes.OwnerReport usage, ProjectReportTypes.Funding accountFunding) {}
    public record Account(String id, Long projectId, String title, String status, String cachedBalance,
                          LocalDateTime balanceCheckedAt, String unitPrice, String refundableUnits, String refundBudget,
                          String debited, String returned, long unresolvedOperations) {}
    public record Customer(String id, Long projectId, String title, String label, String status, String balance,
                           String unitPrice, String refundableUnits, String refundBudget,
                           String debited, String returned, LocalDateTime createdAt) {}
    public record LedgerFilter(Long ownerId, Long projectId, String clientId, String book, String direction,
                               LocalDate fromDate, LocalDate throughDate, String keyword) {}
    public record LedgerEntry(String book, String id, Long ownerId, Long projectId, String title, String subjectId,
                              String action, String direction, String amount, String units, String unitPrice,
                              String subjectBalanceAfter, String walletBalanceAfter,
                              LocalDateTime requestedAt, LocalDateTime settledAt) {}
    /** Amounts grouped by account book; project units are deliberately absent from totals. */
    public record BookTotals(String book, long operations, String debited, String returned, String netDebited) {}
    public record LedgerPage(List<LedgerEntry> records, long total, int current, int size,
                             List<BookTotals> totals, LocalDateTime checkedAt, String timezone) {}
}
