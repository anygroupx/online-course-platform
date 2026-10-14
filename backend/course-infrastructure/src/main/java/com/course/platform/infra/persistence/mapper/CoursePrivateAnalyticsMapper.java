package com.course.platform.infra.persistence.mapper;

import com.course.platform.domain.entity.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 按固定ID游标分批读取全部证据，避免分页截断导致本金或成交合计失真。 */
@Mapper
public interface CoursePrivateAnalyticsMapper {
    @Select("SELECT * FROM course_private_contract_term WHERE id>#{after} AND (#{platformId} IS NULL OR platform_id=#{platformId}) AND (#{planId} IS NULL OR plan_id=#{planId}) ORDER BY id LIMIT 200")
    List<CoursePrivateContractTerm> terms(@Param("after") long after,@Param("platformId") Long platformId,@Param("planId") Long planId);
    @Select("SELECT w.* FROM course_reserved_wallet w JOIN course_private_contract c ON c.id=w.contract_id LEFT JOIN course_private_contract_term t ON t.id=c.current_term_id WHERE w.id>#{after} AND (#{platformId} IS NULL OR w.platform_id=#{platformId}) AND (#{planId} IS NULL OR t.plan_id=#{planId}) ORDER BY w.id LIMIT 200")
    List<CourseReservedWallet> wallets(@Param("after") long after,@Param("platformId") Long platformId,@Param("planId") Long planId);
    /** 同额成功划转及普通账本对边、原支付快照一并核验，不以一条孤立流水冒充来源。 */
    @Select("""
        SELECT l.*,CASE
          WHEN l.biz_type IN ('TRANSFER_IN','TRANSFER_OUT') THEN EXISTS(
            SELECT 1 FROM course_reserved_transfer x JOIN account_ledger a ON a.user_id=x.user_id
              AND a.biz_type='COURSE_TRANSFER' AND a.biz_no=x.biz_no AND a.amount=x.amount AND a.direction=-l.direction
            WHERE x.wallet_id=l.wallet_id AND x.user_id=l.user_id AND x.biz_no=l.biz_no AND x.amount=l.amount
              AND x.type=l.biz_type AND x.status='SUCCEEDED' AND (x.type='TRANSFER_IN' OR x.approved_by IS NOT NULL))
          WHEN (l.biz_type='ORDER' AND l.direction=-1) OR (l.biz_type='REFUND' AND l.direction=1) THEN EXISTS(
            SELECT 1 FROM course_order o WHERE o.wallet_id=l.wallet_id AND o.user_id=l.user_id
              AND o.order_no=l.biz_no AND o.amount=l.amount AND o.payment_source='COURSE_WALLET' AND o.price_source='PRIVATE'
              AND EXISTS(SELECT 1 FROM course_reserved_wallet_ledger d WHERE d.wallet_id=l.wallet_id AND d.user_id=l.user_id
                AND d.biz_type='ORDER' AND d.biz_no=l.biz_no AND d.direction=-1 AND d.amount=l.amount))
          ELSE FALSE END AS verified_source
        FROM course_reserved_wallet_ledger l WHERE l.wallet_id=#{walletId} AND l.id>#{after} ORDER BY l.id LIMIT 500
        """)
    List<com.course.platform.domain.dto.CoursePrivateCashLedgerFact> ledgers(@Param("walletId") Long walletId,@Param("after") long after);
    @Select("SELECT COUNT(*) FROM course_private_plan WHERE current_version_id IS NOT NULL AND mode<>'LEGACY_PERMANENT' AND (#{platformId} IS NULL OR platform_id=#{platformId}) AND (#{planId} IS NULL OR id=#{planId})")
    long publishedPlans(@Param("platformId") Long platformId,@Param("planId") Long planId);
    @Select("SELECT COALESCE(SUM(r.amount),0) FROM course_wallet_release_request r JOIN course_reserved_wallet w ON w.id=r.wallet_id JOIN course_private_contract c ON c.id=w.contract_id LEFT JOIN course_private_contract_term t ON t.id=c.current_term_id WHERE r.status='PENDING' AND (#{platformId} IS NULL OR w.platform_id=#{platformId}) AND (#{planId} IS NULL OR t.plan_id=#{planId})")
    java.math.BigDecimal pendingRelease(@Param("platformId") Long platformId,@Param("planId") Long planId);
    @Select("SELECT COUNT(*) FROM course_private_contract_term n JOIN course_private_term_event e ON e.term_id=n.id AND e.event_type='RENEWAL_CREATED' AND e.operator_id=0 WHERE n.renewal_source_term_id=#{termId} AND n.activated_at IS NOT NULL")
    long automaticRenewal(@Param("termId") Long termId);
    @Select("SELECT * FROM course_private_application WHERE contract_id=#{contractId} AND application_type IN ('OPEN','REJOIN') AND status='APPROVED' ORDER BY id LIMIT 1")
    CoursePrivateApplication origin(@Param("contractId") Long contractId);
}
