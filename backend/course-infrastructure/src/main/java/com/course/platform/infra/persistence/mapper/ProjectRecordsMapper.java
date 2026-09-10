package com.course.platform.infra.persistence.mapper;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** SQL-bounded historical owner and ledger queries; no secret-bearing entity projections. */
@Mapper
public interface ProjectRecordsMapper {
    record Search(Long ownerId, Long projectId, String clientId, String book, String direction,
                  LocalDateTime from, LocalDateTime untilExclusive, String like, Long exactOwnerId) {}
    record OwnerRow(Long id, String username, String status, long activeAccounts, long activeClients,
                    BigDecimal accountDebited, BigDecimal accountReturned, BigDecimal clientDebited,
                    BigDecimal clientReturned, LocalDateTime lastActivity) {}
    record AccountRow(String id, Long projectId, String title, String status, BigDecimal cachedBalance,
                      LocalDateTime balanceCheckedAt, BigDecimal unitPrice, BigDecimal refundableUnits,
                      BigDecimal refundBudget, BigDecimal debited, BigDecimal returned, long unresolvedOperations) {}
    record CustomerRow(String id, Long projectId, String title, String label, String status, BigDecimal balance,
                       BigDecimal unitPrice, BigDecimal refundableUnits, BigDecimal refundBudget,
                       BigDecimal debited, BigDecimal returned, LocalDateTime createdAt) {}
    record LedgerRow(String book, String id, Long ownerId, Long projectId, String title, String subjectId,
                     String action, String direction, BigDecimal amount, BigDecimal units, BigDecimal unitPrice,
                     BigDecimal subjectBalanceAfter, BigDecimal walletBalanceAfter,
                     LocalDateTime requestedAt, LocalDateTime settledAt) {}
    record TotalRow(String book, long operations, BigDecimal debited, BigDecimal returned) {}

    // Include suspended/deleted identities and historic activity; never read all owner IDs into Java.
    String HISTORY = "SELECT owner_id,MAX(activity) AS last_activity FROM ("
            + "SELECT user_id AS owner_id,update_time AS activity FROM service_project_account UNION ALL "
            + "SELECT user_id,update_time FROM service_project_operation UNION ALL "
            + "SELECT owner_id,update_time FROM project_client UNION ALL "
            + "SELECT owner_id,update_time FROM project_client_operation UNION ALL "
            + "SELECT owner_id,update_time FROM project_api_credential UNION ALL "
            + "SELECT owner_id,create_time FROM project_api_call UNION ALL "
            + "SELECT owner_id,update_time FROM project_client_ticket) history GROUP BY owner_id";
    String OWNER_WHERE = " WHERE 1=1 <if test='q.ownerId != null'> AND h.owner_id=#{q.ownerId}</if>"
            + "<if test='q.like != null'> AND (u.username LIKE #{q.like} ESCAPE '!'"
            + "<if test='q.exactOwnerId != null'> OR h.owner_id=#{q.exactOwnerId}</if>)</if>";
    String OWNER_PAGE = "SELECT h.owner_id,h.last_activity,u.username,CASE WHEN u.id IS NULL THEN 'MISSING'"
            + " WHEN u.status=1 THEN 'ACTIVE' ELSE 'DISABLED' END AS status FROM (" + HISTORY
            + ") h LEFT JOIN sys_user u ON u.id=h.owner_id" + OWNER_WHERE
            + " ORDER BY h.owner_id ASC LIMIT #{size} OFFSET #{offset}";
    String OWNER_FIELDS = "o.owner_id AS id,o.username,o.status,"
            + "(SELECT COUNT(*) FROM service_project_account a WHERE a.user_id=o.owner_id AND a.state='ACTIVE' AND a.remote_customer_id IS NOT NULL) AS activeAccounts,"
            + "(SELECT COUNT(*) FROM project_client c WHERE c.owner_id=o.owner_id AND c.status='ACTIVE') AS activeClients,"
            + "(SELECT COALESCE(SUM(amount),0) FROM service_project_operation x WHERE x.user_id=o.owner_id AND x.state='SUCCEEDED' AND x.action IN ('PROVISION','TOP_UP')) AS accountDebited,"
            + "(SELECT COALESCE(SUM(amount),0) FROM service_project_operation x WHERE x.user_id=o.owner_id AND x.state='SUCCEEDED' AND x.action='WITHDRAW') AS accountReturned,"
            + "(SELECT COALESCE(SUM(amount),0) FROM project_client_operation x WHERE x.owner_id=o.owner_id AND x.state='APPLIED' AND x.action IN ('OPEN','TOP_UP')) AS clientDebited,"
            + "(SELECT COALESCE(SUM(amount),0) FROM project_client_operation x WHERE x.owner_id=o.owner_id AND x.state='APPLIED' AND x.action='WITHDRAW') AS clientReturned,"
            + "o.last_activity AS lastActivity";

    @Select("<script>SELECT COUNT(*) FROM (" + HISTORY + ") h LEFT JOIN sys_user u ON u.id=h.owner_id" + OWNER_WHERE + "</script>")
    long ownerCount(@Param("q") Search search);

    @Select("<script>SELECT " + OWNER_FIELDS + " FROM (" + OWNER_PAGE + ") o ORDER BY o.owner_id ASC</script>")
    List<OwnerRow> owners(@Param("q") Search search, @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COALESCE(SUM(CASE WHEN state='SUCCEEDED' THEN 1 ELSE 0 END),0) AS settledOperations,"
            + "COALESCE(SUM(CASE WHEN state='SUCCEEDED' AND action IN ('PROVISION','TOP_UP') THEN amount ELSE 0 END),0) AS debited,"
            + "COALESCE(SUM(CASE WHEN state='SUCCEEDED' AND action='WITHDRAW' THEN amount ELSE 0 END),0) AS returned,"
            + "COALESCE(SUM(CASE WHEN state IN ('UNKNOWN','DISPATCHING') THEN 1 ELSE 0 END),0) AS unresolvedOperations"
            + " FROM service_project_operation WHERE user_id=#{owner}")
    ProjectReportMapper.FundingRow accountFunding(@Param("owner") Long owner);

    @Select("SELECT COUNT(*) FROM service_project_account WHERE user_id=#{owner}")
    long accountCount(@Param("owner") long owner);

    @Select("SELECT a.id,a.project_id AS projectId,p.title,a.state AS status,a.remote_balance AS cachedBalance,"
            + "a.balance_checked_at AS balanceCheckedAt,a.unit_price AS unitPrice,a.refundable_units AS refundableUnits,a.refund_budget AS refundBudget,"
            + "(SELECT COALESCE(SUM(amount),0) FROM service_project_operation o WHERE o.user_id=a.user_id AND o.account_id=a.id AND o.state='SUCCEEDED' AND o.action IN ('PROVISION','TOP_UP')) AS debited,"
            + "(SELECT COALESCE(SUM(amount),0) FROM service_project_operation o WHERE o.user_id=a.user_id AND o.account_id=a.id AND o.state='SUCCEEDED' AND o.action='WITHDRAW') AS returned,"
            + "(SELECT COUNT(*) FROM service_project_operation o WHERE o.user_id=a.user_id AND o.account_id=a.id AND o.state IN ('UNKNOWN','DISPATCHING')) AS unresolvedOperations"
            + " FROM service_project_account a JOIN service_project p ON p.id=a.project_id WHERE a.user_id=#{owner} ORDER BY a.project_id,a.id LIMIT #{size} OFFSET #{offset}")
    List<AccountRow> accounts(@Param("owner") long owner, @Param("offset") long offset, @Param("size") int size);

    String CLIENT_WHERE = " WHERE c.owner_id=#{owner}<if test='project != null'> AND c.project_id=#{project}</if>"
            + "<if test='status != null'> AND c.status=#{status}</if>";
    @Select("<script>SELECT COUNT(*) FROM project_client c" + CLIENT_WHERE + "</script>")
    long customerCount(@Param("owner") long owner, @Param("project") Long project, @Param("status") String status);

    @Select("<script>SELECT c.id,c.project_id AS projectId,c.project_title AS title,c.label,c.status,c.balance,c.unit_price AS unitPrice,"
            + "c.refundable_units AS refundableUnits,c.refund_budget AS refundBudget,"
            + "(SELECT COALESCE(SUM(amount),0) FROM project_client_operation o WHERE o.owner_id=c.owner_id AND o.client_id=c.id AND o.state='APPLIED' AND o.action IN ('OPEN','TOP_UP')) AS debited,"
            + "(SELECT COALESCE(SUM(amount),0) FROM project_client_operation o WHERE o.owner_id=c.owner_id AND o.client_id=c.id AND o.state='APPLIED' AND o.action='WITHDRAW') AS returned,c.create_time AS createdAt"
            + " FROM project_client c" + CLIENT_WHERE + " ORDER BY c.project_id,c.id LIMIT #{size} OFFSET #{offset}</script>")
    List<CustomerRow> customers(@Param("owner") long owner, @Param("project") Long project, @Param("status") String status,
                               @Param("offset") long offset, @Param("size") int size);

    String PROJECT_FILTER = "<if test='q.ownerId != null'> AND o.user_id=#{q.ownerId}</if>"
            + "<if test='q.projectId != null'> AND o.project_id=#{q.projectId}</if>"
            + "<if test='q.clientId != null'> AND 1=0</if>";
    String CUSTOMER_FILTER = "<if test='q.ownerId != null'> AND o.owner_id=#{q.ownerId}</if>"
            + "<if test='q.projectId != null'> AND o.project_id=#{q.projectId}</if>"
            + "<if test='q.clientId != null'> AND o.client_id=#{q.clientId}</if>";
    String TIME_FILTER = "<if test='q.from != null'> AND o.update_time &gt;= #{q.from}</if>"
            + "<if test='q.untilExclusive != null'> AND o.update_time &lt; #{q.untilExclusive}</if>";
    String PROJECT_LEDGER = "SELECT 'PROJECT_ACCOUNT' AS book,o.id,o.user_id AS ownerId,o.project_id AS projectId,o.project_title AS title,"
            + "o.account_id AS subjectId,o.action,CASE WHEN o.action='WITHDRAW' THEN 'CREDIT' ELSE 'DEBIT' END AS direction,"
            + "o.amount,o.units,o.unit_price AS unitPrice,o.balance_after AS subjectBalanceAfter,CAST(NULL AS DECIMAL(14,2)) AS walletBalanceAfter,"
            + "o.create_time AS requestedAt,o.update_time AS settledAt FROM service_project_operation o WHERE o.state='SUCCEEDED'"
            + " AND o.action IN ('PROVISION','TOP_UP','WITHDRAW')" + PROJECT_FILTER + TIME_FILTER;
    String CUSTOMER_LEDGER = "SELECT 'CUSTOMER_CREDIT' AS book,o.id,o.owner_id AS ownerId,o.project_id AS projectId,o.project_title AS title,"
            + "o.client_id AS subjectId,o.action,CASE WHEN o.action='WITHDRAW' THEN 'CREDIT' ELSE 'DEBIT' END AS direction,"
            + "o.amount,o.units,o.unit_price AS unitPrice,o.client_balance_after AS subjectBalanceAfter,o.wallet_balance_after AS walletBalanceAfter,"
            + "o.create_time AS requestedAt,o.update_time AS settledAt FROM project_client_operation o WHERE o.state='APPLIED'"
            + " AND o.action IN ('OPEN','TOP_UP','WITHDRAW')" + CUSTOMER_FILTER + TIME_FILTER;
    String LEDGER = "<choose><when test='q.book == &quot;PROJECT_ACCOUNT&quot;'>" + PROJECT_LEDGER + "</when>"
            + "<when test='q.book == &quot;CUSTOMER_CREDIT&quot;'>" + CUSTOMER_LEDGER + "</when>"
            + "<otherwise>" + PROJECT_LEDGER + " UNION ALL " + CUSTOMER_LEDGER + "</otherwise></choose>";
    String LEDGER_WHERE = " WHERE 1=1<if test='q.direction != null'> AND l.direction=#{q.direction}</if>"
            + "<if test='q.like != null'> AND (l.title LIKE #{q.like} ESCAPE '!' OR l.id LIKE #{q.like} ESCAPE '!' OR l.subjectId LIKE #{q.like} ESCAPE '!')</if>";

    @Select("<script>SELECT COUNT(*) FROM (" + LEDGER + ") l" + LEDGER_WHERE + "</script>")
    long ledgerCount(@Param("q") Search search);
    @Select("<script>SELECT l.* FROM (" + LEDGER + ") l" + LEDGER_WHERE
            + " ORDER BY l.settledAt DESC,l.book ASC,l.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<LedgerRow> ledger(@Param("q") Search search, @Param("offset") long offset, @Param("size") int size);
    @Select("<script>SELECT l.book,COUNT(*) AS operations,"
            + "COALESCE(SUM(CASE WHEN l.direction='DEBIT' THEN amount ELSE 0 END),0) AS debited,"
            + "COALESCE(SUM(CASE WHEN l.direction='CREDIT' THEN amount ELSE 0 END),0) AS returned"
            + " FROM (" + LEDGER + ") l" + LEDGER_WHERE + " GROUP BY l.book ORDER BY l.book</script>")
    List<TotalRow> ledgerTotals(@Param("q") Search search);
}
