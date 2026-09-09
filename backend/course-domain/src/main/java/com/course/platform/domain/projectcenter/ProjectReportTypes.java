package com.course.platform.domain.projectcenter;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only local observations; no supplier credentials, request bodies or mixed-project units. */
public final class ProjectReportTypes {
    private ProjectReportTypes() {}

    public record Window(LocalDateTime from, LocalDateTime through, String timezone) {}
    public record Calls(long total, long failed, long last24Hours, long failedLast24Hours, long actionKinds) {}
    public record ActionUsage(String action, long total, long failed, long last24Hours, long failedLast24Hours) {}
    public record Clients(long total, long active, long suspended, long closed, long projects) {}
    public record Tickets(long total, long open, long inProgress, long resolved, long closed, long pendingCompensation) {}
    /** All amounts are CNY platform-wallet movements, not revenue or project units. */
    public record Funding(long settledOperations, String debited, String returned, String netDebited,
                          long unresolvedOperations) {}
    public record OwnerReport(Window window, Calls calls, List<ActionUsage> actions, boolean moreActions,
                              Clients clients, Tickets tickets, Funding localFunding) {}
    public record ProjectBalance(Long projectId, String title, long customers, long active,
                                 String activeUnits, String suspendedUnits, String refundBudget) {}
    public record SystemReport(Window window, long publishedProjects, long activeUpstreamBindings,
                               long activeUpstreamOwners, long activeLocalOwners, Calls calls,
                               Clients clients, Tickets tickets, Funding upstreamFunding, Funding localFunding) {}
}
