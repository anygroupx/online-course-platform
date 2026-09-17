package com.course.platform.domain.servicecommerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Explicit API DTOs; entities and third-party responses never leave the service boundary. */
public final class ServiceCommerceTypes {
    private ServiceCommerceTypes() {}

    public record ProductCommand(
            @NotNull Long providerId,
            @NotBlank @Size(max = 16) String project,
            @NotBlank @Size(max = 64) String remoteProductId,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.000001") @DecimalMax("9999") @Digits(integer = 4, fraction = 6)
                    BigDecimal unitPrice,
            boolean enabled,
            Long version,
            @Valid ContractPriceForm contractPrice) {
        public ProductCommand(
                Long providerId,
                String project,
                String remoteProductId,
                String title,
                String description,
                BigDecimal unitPrice,
                boolean enabled,
                Long version) {
            this(
                    providerId,
                    project,
                    remoteProductId,
                    title,
                    description,
                    unitPrice,
                    enabled,
                    version,
                    null);
        }
    }

    public record ContractPriceForm(
            @NotNull @DecimalMin("0") @DecimalMax("9999") @Digits(integer = 4, fraction = 6)
                    BigDecimal unitCost,
            @NotNull LocalDate validUntil,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean upstreamChecked) {}

    public record ContractPriceView(
            String unitCost,
            LocalDate validUntil,
            String evidence,
            Long reviewedBy,
            LocalDateTime reviewedAt) {}

    public record OrderForm(
            @Min(0) @Max(365) int quantity,
            @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2)
                    BigDecimal distance,
            @NotNull @Size(max = 64) Map<@Size(max = 40) String, @Size(max = 2048) String> fields,
            @Size(max = 365) List<@Size(max = 19) String> taskTimes,
            boolean authorizedAccount,
            @Valid InternshipSchedule schedule,
            @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    String accountSessionId) {
        public OrderForm(
                int quantity,
                BigDecimal distance,
                Map<String, String> fields,
                List<String> taskTimes,
                boolean authorizedAccount,
                InternshipSchedule schedule) {
            this(quantity, distance, fields, taskTimes, authorizedAccount, schedule, null);
        }

        public OrderForm(
                int quantity,
                BigDecimal distance,
                Map<String, String> fields,
                List<String> taskTimes,
                boolean authorizedAccount) {
            this(quantity, distance, fields, taskTimes, authorizedAccount, null);
        }

        @Override
        public String toString() {
            return "OrderForm[quantity=" + quantity + ", fields=REDACTED]";
        }
    }

    public record ActionForm(
            @NotBlank
                    @Pattern(
                            regexp =
                                    "CANCEL|REFUND|ADD_TIMES|PAUSE|RESUME|DELAY|DELAY_TASK|CHANGE_TIME|EDIT_PLAN|REASSIGN|EDIT_SCHEDULE|RUN_NOW|REPORT")
                    String action,
            @Min(0) @Max(365) int quantity,
            @Size(max = 64) Map<@Size(max = 40) String, @Size(max = 2048) String> fields,
            @Valid InternshipSchedule schedule) {
        public ActionForm(String action, int quantity) {
            this(action, quantity, Map.of(), null);
        }

        public ActionForm(String action, int quantity, Map<String, String> fields) {
            this(action, quantity, fields, null);
        }

        @Override
        public String toString() {
            return "ActionForm[action=" + action + ", fields=REDACTED]";
        }
    }

    public record ResolveForm(
            @Pattern(regexp = "ACCEPTED|NOT_ACCEPTED") @NotNull String outcome,
            @Size(max = 64) String externalOrderNo,
            @Min(0) @Max(9999) Integer refundedUnits,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean upstreamChecked,
            @Size(max = 64) String externalSubOrderNo) {
        public ResolveForm(String outcome, String externalOrderNo, Integer refundedUnits,
                           String evidence, boolean upstreamChecked) {
            this(outcome, externalOrderNo, refundedUnits, evidence, upstreamChecked, null);
        }
    }

    public record RefundSettlementForm(
            @NotNull @Min(0) Long orderVersion,
            @Min(0) @Max(9999) int refundedUnits,
            @NotBlank @Size(min = 10, max = 1000) String evidence,
            boolean upstreamChecked) {}

    public record Choice(String field, String value, String label) {}

    /** Optional facts suggested by account lookup; not a paid plan or an authorization. */
    public record InternshipAdvice(
            String checkInTime, String checkOutTime, LocalDate endDate, List<Integer> weekdays,
            Boolean dailyReport, Boolean weeklyReport, Boolean monthlyReport) {}

    public record Lookup(
            Map<String, String> suggested,
            List<Choice> choices,
            String notice,
            InternshipSchedule schedule,
            List<LocalDate> paidDates,
            InternshipAdvice advice,
            List<RunRule> runRules,
            List<SchoolRunRule> schoolRules) {
        public Lookup(Map<String, String> suggested, List<Choice> choices, String notice,
                      InternshipSchedule schedule, List<LocalDate> paidDates, InternshipAdvice advice,
                      List<RunRule> runRules) {
            this(suggested, choices, notice, schedule, paidDates, advice, runRules, null);
        }
        public Lookup(Map<String, String> suggested, List<Choice> choices, String notice,
                      InternshipSchedule schedule, List<LocalDate> paidDates, InternshipAdvice advice) {
            this(suggested, choices, notice, schedule, paidDates, advice, null);
        }
        public Lookup(Map<String, String> suggested, List<Choice> choices, String notice) {
            this(suggested, choices, notice, null, null, null);
        }
        public Lookup(Map<String, String> suggested, List<Choice> choices, String notice,
                      InternshipSchedule schedule) {
            this(suggested, choices, notice, schedule, null, null);
        }
        public Lookup(Map<String, String> suggested, List<Choice> choices, String notice,
                      InternshipSchedule schedule, List<LocalDate> paidDates) {
            this(suggested, choices, notice, schedule, paidDates, null);
        }
    }

    public record ProductView(
            Long id,
            Long providerId,
            String providerType,
            String project,
            String remoteProductId,
            String title,
            String description,
            String unitPrice,
            String priceUnit,
            boolean enabled,
            boolean available,
            Long version,
            List<String> capabilities,
            ContractPriceView contractPrice) {}

    public record OrderView(
            String id,
            String title,
            String accountLabel,
            String providerType,
            String project,
            String status,
            int quantity,
            Integer completed,
            String distance,
            String paidAmount,
            String refundedAmount,
            String pendingOperationId,
            LocalDateTime createTime,
            Long version,
            List<String> actions,
            InternshipSchedule schedule,
            String quantityUnit,
            TotalDistancePlan distancePlan,
            StatusCheckView statusCheck) {}

    public record StatusCheckView(
            @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
                    LocalDateTime checkedAt,
            boolean delayed) {}

    public record QuoteView(
            String id,
            String orderId,
            String action,
            String state,
            String title,
            int quantity,
            String amount,
            String amountLabel,
            LocalDateTime expiresAt,
            String errorCategory,
            String quantityUnit,
            TotalDistancePlan distancePlan,
            String unitCharge) {
        public QuoteView(
                String id,
                String orderId,
                String action,
                String state,
                String title,
                int quantity,
                String amount,
                String amountLabel,
                LocalDateTime expiresAt,
                String errorCategory) {
            this(
                    id,
                    orderId,
                    action,
                    state,
                    title,
                    quantity,
                    amount,
                    amountLabel,
                    expiresAt,
                    errorCategory,
                    "次",
                    null,
                    null);
        }
    }

    public record EventView(
            String id,
            String action,
            String state,
            String amount,
            String errorCategory,
            LocalDateTime createTime) {}

    public record AuditEventView(EventView operation, Long resolvedBy, String evidence) {}

    public record OrderAuditView(
            OrderView order,
            Long userId,
            Long providerId,
            String externalOrderNo,
            String externalSubOrderNo,
            List<AuditEventView> events) {}

    public record RemoteResult(
            String externalOrderNo,
            String status,
            Integer completed,
            Integer refundedUnits,
            String externalSubOrderNo) {
        public RemoteResult(String id, String status, Integer completed, Integer refundedUnits) {
            this(id, status, completed, refundedUnits, null);
        }
    }

    public record PreparedAction(
            Map<String, Object> fields,
            int quantity,
            BigDecimal unitCharge,
            DailyServicePlan plan) {
        @Override
        public String toString() {
            return "PreparedAction[quantity=" + quantity + ", fields=REDACTED]";
        }
    }

    /** Safe running-rule projection, never an arbitrary remote form or HTML fragment. */
    public record RunRule(String distance, String startTime, String endTime) {}

    /** A school-specific rule with its own zone and minimum distance; IDs remain strings. */
    public record SchoolRunRule(String id, String zoneId, String zoneName, String minDistance) {}

    public record OrderText(String text) {
        @Override
        public String toString() { return "OrderText[text=REDACTED]"; }
    }

    public record RunLog(String id, String time, String status, boolean editable, String endTime) {
        public RunLog(String id, String time, String status) {
            this(id, time, status, false, null);
        }
    }

    public record RunLogPage(List<RunLog> items, int page, boolean hasMore) {}

    public record PreparedOrder(
            Map<String, Object> fields,
            int quantity,
            BigDecimal distance,
            BigDecimal billablePerUnit,
            String accountLabel,
            DailyServicePlan plan,
            TotalDistancePlan distancePlan,
            ServiceAccountFingerprint accountFingerprint) {
        public PreparedOrder(
                Map<String, Object> fields, int quantity, BigDecimal distance,
                BigDecimal billablePerUnit, String accountLabel, DailyServicePlan plan,
                TotalDistancePlan distancePlan) {
            this(fields, quantity, distance, billablePerUnit, accountLabel, plan, distancePlan, null);
        }

        public PreparedOrder(
                Map<String, Object> fields, int quantity, BigDecimal distance,
                BigDecimal billablePerUnit, String accountLabel, DailyServicePlan plan) {
            this(fields, quantity, distance, billablePerUnit, accountLabel, plan, null);
        }

        public PreparedOrder(
                Map<String, Object> fields,
                int quantity,
                BigDecimal distance,
                BigDecimal billablePerUnit,
                String accountLabel) {
            this(fields, quantity, distance, billablePerUnit, accountLabel, null);
        }

        @Override
        public String toString() {
            return "PreparedOrder[quantity=" + quantity + ", fields=REDACTED]";
        }
    }
}
