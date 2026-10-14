package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateTermOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 付款时确定归属的周期订单及可信结算投影。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateTermOrderMapper extends BaseMapper<CoursePrivateTermOrder> {
    /** 强类型连接包含逻辑归档订单和原账本，不通过名称、日期或当前套餐猜测归属。 */
    @Select("""
        SELECT x.id,x.term_id,x.contract_id,x.user_id,x.platform_id,x.order_id,x.order_no,x.ordered_amount,x.paid_at,
          o.amount AS order_amount,o.user_id AS order_user_id,o.platform_id AS order_platform_id,
          o.private_contract_id AS order_contract_id,o.wallet_id AS order_wallet_id,o.private_term_id AS order_term_id,
          o.private_plan_version_id AS order_plan_version_id,o.private_operations_mode AS order_operations_mode,
          o.price_source AS order_price_source,o.payment_source AS order_payment_source,o.order_status,
          o.is_deleted AS archived,o.fulfilled_at,o.fulfillment_source,o.completion_confirmed_at,o.completion_confirmation_source,
          o.refunded_at AS order_refunded_at,
          EXISTS(SELECT 1 FROM course_reserved_wallet_ledger d WHERE d.wallet_id=o.wallet_id AND d.user_id=o.user_id AND d.biz_type='ORDER' AND d.biz_no=o.order_no AND d.direction=-1 AND d.amount=x.ordered_amount) AS original_debit_exists,
          EXISTS(SELECT 1 FROM course_reserved_wallet_ledger r WHERE r.wallet_id=o.wallet_id AND r.user_id=o.user_id AND r.biz_type='REFUND' AND r.biz_no=o.order_no AND r.direction=1) AS refund_exists,
          COALESCE((SELECT SUM(r.amount) FROM course_reserved_wallet_ledger r WHERE r.wallet_id=o.wallet_id AND r.user_id=o.user_id AND r.biz_type='REFUND' AND r.biz_no=o.order_no AND r.direction=1),0) AS refund_amount
        FROM course_private_term_order x JOIN course_order o ON o.id=x.order_id WHERE x.term_id=#{termId} ORDER BY x.order_id FOR UPDATE
        """)
    List<com.course.platform.domain.dto.CoursePrivateOrderFact> selectConsumptionFacts(@Param("termId") Long termId);

    /** 经营分析在一致性只读快照中核验，不获取订单写锁。 */
    @Select("""
        SELECT x.id,x.term_id,x.contract_id,x.user_id,x.platform_id,x.order_id,x.order_no,x.ordered_amount,x.paid_at,
          o.amount AS order_amount,o.user_id AS order_user_id,o.platform_id AS order_platform_id,
          o.private_contract_id AS order_contract_id,o.wallet_id AS order_wallet_id,o.private_term_id AS order_term_id,
          o.private_plan_version_id AS order_plan_version_id,o.private_operations_mode AS order_operations_mode,
          o.price_source AS order_price_source,o.payment_source AS order_payment_source,o.order_status,
          o.is_deleted AS archived,o.fulfilled_at,o.fulfillment_source,o.completion_confirmed_at,o.completion_confirmation_source,
          o.refunded_at AS order_refunded_at,
          EXISTS(SELECT 1 FROM course_reserved_wallet_ledger d WHERE d.wallet_id=o.wallet_id AND d.user_id=o.user_id AND d.biz_type='ORDER' AND d.biz_no=o.order_no AND d.direction=-1 AND d.amount=x.ordered_amount) AS original_debit_exists,
          EXISTS(SELECT 1 FROM course_reserved_wallet_ledger r WHERE r.wallet_id=o.wallet_id AND r.user_id=o.user_id AND r.biz_type='REFUND' AND r.biz_no=o.order_no AND r.direction=1) AS refund_exists,
          COALESCE((SELECT SUM(r.amount) FROM course_reserved_wallet_ledger r WHERE r.wallet_id=o.wallet_id AND r.user_id=o.user_id AND r.biz_type='REFUND' AND r.biz_no=o.order_no AND r.direction=1),0) AS refund_amount
        FROM course_private_term_order x JOIN course_order o ON o.id=x.order_id WHERE x.term_id=#{termId} ORDER BY x.order_id
        """)
    List<com.course.platform.domain.dto.CoursePrivateOrderFact> selectConsumptionFactsReadOnly(@Param("termId") Long termId);

    /** 只更新事实投影，不覆盖原关联、订单金额或支付时刻。 */
    @Update("UPDATE course_private_term_order SET qualified_amount=#{row.qualifiedAmount},qualification_status=#{row.qualificationStatus},completed_at=#{row.completedAt},completion_confirmed_at=#{row.completionConfirmedAt},completion_basis=#{row.completionBasis},refunded_at=#{row.refundedAt},last_reconciled_at=#{now} WHERE id=#{row.id} AND term_id=#{row.termId}")
    int reconcile(@Param("row") CoursePrivateTermOrder row,@Param("now") LocalDateTime now);
    @Select("SELECT * FROM course_private_term_order WHERE id=#{id} FOR UPDATE")
    CoursePrivateTermOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_term_order WHERE order_id=#{orderId}")
    CoursePrivateTermOrder selectByOrderId(@Param("orderId") Long orderId);
    @Select("SELECT * FROM course_private_term_order WHERE order_no=#{orderNo}")
    CoursePrivateTermOrder selectByOrderNo(@Param("orderNo") String orderNo);
    @Select("SELECT * FROM course_private_term_order WHERE term_id=#{termId} ORDER BY order_id")
    List<CoursePrivateTermOrder> selectByTerm(@Param("termId") Long termId);
    /** 按原周期关联分页，金额及归属不会按当前套餐重算。 */
    @Select("SELECT * FROM course_private_term_order WHERE term_id=#{termId} ORDER BY order_id LIMIT #{limit} OFFSET #{offset}")
    List<CoursePrivateTermOrder> selectByTermPage(@Param("termId") Long termId,@Param("offset") long offset,@Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM course_private_term_order WHERE term_id=#{termId}")
    long countByTerm(@Param("termId") Long termId);
    /** 显式SQL保留逻辑归档行，原扣款与退款流水优先于任意状态文本。 */
    @Select("""
        SELECT x.*,o.order_status AS orderStatus,o.is_deleted AS archived,
          o.fulfilled_at AS fulfilledAt,o.fulfillment_source AS fulfillmentSource,o.refunded_at AS orderRefundedAt,
          EXISTS(SELECT 1 FROM course_reserved_wallet_ledger d WHERE d.wallet_id=o.wallet_id AND d.user_id=o.user_id AND d.biz_type='ORDER' AND d.biz_no=o.order_no AND d.direction=-1 AND d.amount=x.ordered_amount) AS originalDebitExists,
          EXISTS(SELECT 1 FROM course_reserved_wallet_ledger r WHERE r.wallet_id=o.wallet_id AND r.user_id=o.user_id AND r.biz_type='REFUND' AND r.biz_no=o.order_no AND r.direction=1) AS refundExists
        FROM course_private_term_order x JOIN course_order o ON o.id=x.order_id WHERE x.term_id=#{termId} ORDER BY x.order_id
        """)
    List<Map<String,Object>> selectFactsByTerm(@Param("termId") Long termId);
}
