package com.course.platform.infra.persistence.mapper;

import com.course.platform.domain.projectcenter.ProjectReportTypes.*;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Aggregate in SQL, never load unbounded entity histories or encrypted payload columns. */
@Mapper
public interface ProjectReportMapper {
    String OWNER = "<if test='owner != null'> AND owner_id=#{owner}</if>";
    String CALL_FIELDS = "COUNT(*) AS total, COALESCE(SUM(CASE WHEN outcome='FAILED' THEN 1 ELSE 0 END),0) AS failed,"
            + " COALESCE(SUM(CASE WHEN create_time &gt;= #{since} THEN 1 ELSE 0 END),0) AS last24Hours,"
            + " COALESCE(SUM(CASE WHEN create_time &gt;= #{since} AND outcome='FAILED' THEN 1 ELSE 0 END),0) AS failedLast24Hours";

    @Select("<script>SELECT " + CALL_FIELDS + ", COUNT(DISTINCT action) AS actionKinds FROM project_api_call"
            + " WHERE create_time &lt;= #{until}" + OWNER + "</script>")
    Calls calls(@Param("owner") Long owner, @Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("<script>SELECT action," + CALL_FIELDS + " FROM project_api_call"
            + " WHERE owner_id=#{owner} AND create_time &lt;= #{until} GROUP BY action"
            + " ORDER BY total DESC, action ASC LIMIT 50</script>")
    List<ActionUsage> actions(@Param("owner") Long owner, @Param("since") LocalDateTime since, @Param("until") LocalDateTime until);

    @Select("<script>SELECT COUNT(*) AS total,"
            + " COALESCE(SUM(CASE WHEN status='ACTIVE' THEN 1 ELSE 0 END),0) AS active,"
            + " COALESCE(SUM(CASE WHEN status='SUSPENDED' THEN 1 ELSE 0 END),0) AS suspended,"
            + " COALESCE(SUM(CASE WHEN status='CLOSED' THEN 1 ELSE 0 END),0) AS closed,"
            + " COUNT(DISTINCT project_id) AS projects FROM project_client WHERE 1=1" + OWNER + "</script>")
    Clients clients(@Param("owner") Long owner);

    @Select("<script>SELECT COUNT(*) AS total,"
            + " COALESCE(SUM(CASE WHEN status='OPEN' THEN 1 ELSE 0 END),0) AS open,"
            + " COALESCE(SUM(CASE WHEN status='IN_PROGRESS' THEN 1 ELSE 0 END),0) AS inProgress,"
            + " COALESCE(SUM(CASE WHEN status='RESOLVED' THEN 1 ELSE 0 END),0) AS resolved,"
            + " COALESCE(SUM(CASE WHEN status='CLOSED' THEN 1 ELSE 0 END),0) AS closed,"
            + " COALESCE(SUM(CASE WHEN kind='COMPENSATION' AND review_result='PENDING' THEN 1 ELSE 0 END),0) AS pendingCompensation"
            + " FROM project_client_ticket WHERE 1=1" + OWNER + "</script>")
    Tickets tickets(@Param("owner") Long owner);

    record FundingRow(long settledOperations, BigDecimal debited, BigDecimal returned, long unresolvedOperations) {}

    @Select("<script>SELECT COALESCE(SUM(CASE WHEN state='APPLIED' THEN 1 ELSE 0 END),0) AS settledOperations,"
            + " COALESCE(SUM(CASE WHEN state='APPLIED' AND action IN ('OPEN','TOP_UP') THEN amount ELSE 0 END),0) AS debited,"
            + " COALESCE(SUM(CASE WHEN state='APPLIED' AND action='WITHDRAW' THEN amount ELSE 0 END),0) AS returned,"
            + " 0 AS unresolvedOperations FROM project_client_operation WHERE 1=1" + OWNER + "</script>")
    FundingRow localFunding(@Param("owner") Long owner);

    @Select("SELECT COALESCE(SUM(CASE WHEN state='SUCCEEDED' THEN 1 ELSE 0 END),0) AS settledOperations,"
            + " COALESCE(SUM(CASE WHEN state='SUCCEEDED' AND action IN ('PROVISION','TOP_UP') THEN amount ELSE 0 END),0) AS debited,"
            + " COALESCE(SUM(CASE WHEN state='SUCCEEDED' AND action='WITHDRAW' THEN amount ELSE 0 END),0) AS returned,"
            + " COALESCE(SUM(CASE WHEN state IN ('DISPATCHING','UNKNOWN') THEN 1 ELSE 0 END),0) AS unresolvedOperations"
            + " FROM service_project_operation")
    FundingRow upstreamFunding();

    record ProjectRow(Long projectId, String title, long customers, long active,
                      BigDecimal activeUnits, BigDecimal suspendedUnits, BigDecimal refundBudget) {}

    @Select("SELECT c.project_id AS projectId,p.title,COUNT(*) AS customers,"
            + " COALESCE(SUM(CASE WHEN c.status='ACTIVE' THEN 1 ELSE 0 END),0) AS active,"
            + " COALESCE(SUM(CASE WHEN c.status='ACTIVE' THEN c.balance ELSE 0 END),0) AS activeUnits,"
            + " COALESCE(SUM(CASE WHEN c.status='SUSPENDED' THEN c.balance ELSE 0 END),0) AS suspendedUnits,"
            + " COALESCE(SUM(c.refund_budget),0) AS refundBudget FROM project_client c"
            + " JOIN service_project p ON p.id=c.project_id WHERE c.owner_id=#{owner}"
            + " GROUP BY c.project_id,p.title ORDER BY c.project_id ASC LIMIT #{size} OFFSET #{offset}")
    List<ProjectRow> projects(@Param("owner") Long owner, @Param("offset") long offset, @Param("size") int size);

    record SystemCounts(long publishedProjects, long activeUpstreamBindings, long activeUpstreamOwners, long activeLocalOwners) {}

    @Select("SELECT (SELECT COUNT(*) FROM service_project WHERE enabled=TRUE) AS publishedProjects,"
            + " (SELECT COUNT(*) FROM service_project_account WHERE state='ACTIVE' AND remote_customer_id IS NOT NULL) AS activeUpstreamBindings,"
            + " (SELECT COUNT(DISTINCT user_id) FROM service_project_account WHERE state='ACTIVE' AND remote_customer_id IS NOT NULL) AS activeUpstreamOwners,"
            + " (SELECT COUNT(DISTINCT owner_id) FROM project_client WHERE status='ACTIVE') AS activeLocalOwners")
    SystemCounts systemCounts();
}
