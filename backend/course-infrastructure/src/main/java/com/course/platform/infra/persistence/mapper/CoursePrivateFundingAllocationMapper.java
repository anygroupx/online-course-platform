package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateFundingAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 转入资金资格唯一认领，不扣款或冻结本金。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateFundingAllocationMapper extends BaseMapper<CoursePrivateFundingAllocation> {
    /** 资格解释只接受同额成功划转；原始资金认领状态交由纯策略逐笔校验。 */
    @Select("""
        SELECT l.id AS ledger_id,l.wallet_id,l.user_id,t.type AS transfer_type,t.amount,t.completed_at,a.term_id AS allocated_term_id
        FROM course_reserved_wallet_ledger l
        JOIN course_reserved_transfer t ON t.wallet_id=l.wallet_id AND t.user_id=l.user_id AND t.biz_no=l.biz_no
          AND t.type=l.biz_type AND t.amount=l.amount AND t.status='SUCCEEDED'
        LEFT JOIN course_private_funding_allocation a ON a.ledger_id=l.id
        WHERE l.wallet_id=#{walletId} AND l.user_id=#{userId}
          AND ((t.type='TRANSFER_IN' AND l.direction=1) OR (t.type='TRANSFER_OUT' AND l.direction=-1 AND t.approved_by IS NOT NULL))
          AND (#{startLedgerId} IS NULL OR l.id>#{startLedgerId}) AND (#{endLedgerId} IS NULL OR l.id<=#{endLedgerId})
          AND (#{windowStart} IS NULL OR t.completed_at>=#{windowStart}) AND (#{windowEnd} IS NULL OR t.completed_at<#{windowEnd})
          AND t.completed_at<=#{asOf}
        ORDER BY l.id
        """)
    List<com.course.platform.domain.dto.CoursePrivateFundingFact> selectFundingFacts(@Param("walletId") Long walletId,@Param("userId") Long userId,@Param("startLedgerId") Long startLedgerId,@Param("endLedgerId") Long endLedgerId,@Param("windowStart") LocalDateTime windowStart,@Param("windowEnd") LocalDateTime windowEnd,@Param("asOf") LocalDateTime asOf);
    @Select("SELECT * FROM course_private_funding_allocation WHERE id=#{id} FOR UPDATE")
    CoursePrivateFundingAllocation selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_funding_allocation WHERE ledger_id=#{ledgerId}")
    CoursePrivateFundingAllocation selectByLedgerId(@Param("ledgerId") Long ledgerId);
    @Select("SELECT * FROM course_private_funding_allocation WHERE term_id=#{termId} ORDER BY ledger_id")
    List<CoursePrivateFundingAllocation> selectByTerm(@Param("termId") Long termId);
    /** 只取成功转入且未认领流水，秒内边界以账户锁下水位判定。 */
    @Select("""
        SELECT l.* FROM course_reserved_wallet_ledger l
        JOIN course_reserved_transfer t ON t.wallet_id=l.wallet_id AND t.user_id=l.user_id AND t.biz_no=l.biz_no AND t.type='TRANSFER_IN' AND t.status='SUCCEEDED'
        LEFT JOIN course_private_funding_allocation a ON a.ledger_id=l.id
        WHERE l.wallet_id=#{walletId} AND l.user_id=#{userId} AND l.biz_type='TRANSFER_IN' AND l.direction=1 AND a.id IS NULL
          AND (#{startLedgerId} IS NULL OR l.id>#{startLedgerId}) AND (#{endLedgerId} IS NULL OR l.id<=#{endLedgerId})
          AND (#{windowStart} IS NULL OR t.completed_at>=#{windowStart}) AND (#{windowEnd} IS NULL OR t.completed_at<#{windowEnd})
        ORDER BY l.id
        """)
    List<com.course.platform.domain.entity.CourseReservedWalletLedger> selectUnallocatedTransferIn(@Param("walletId") Long walletId, @Param("userId") Long userId, @Param("startLedgerId") Long startLedgerId, @Param("endLedgerId") Long endLedgerId, @Param("windowStart") LocalDateTime windowStart, @Param("windowEnd") LocalDateTime windowEnd);
    @Select("SELECT COALESCE(MAX(id),0) FROM course_reserved_wallet_ledger WHERE wallet_id=#{walletId} AND user_id=#{userId}")
    Long selectWalletLedgerWatermark(@Param("walletId") Long walletId, @Param("userId") Long userId);
    /** 迟到续期仍以真实期界封闭前期资金，期界后已经成功的转入不能被迟到任务水位吞掉。 */
    @Select("SELECT COALESCE(MAX(l.id),0) FROM course_reserved_wallet_ledger l JOIN course_reserved_transfer t ON t.wallet_id=l.wallet_id AND t.user_id=l.user_id AND t.biz_no=l.biz_no AND t.type=l.biz_type AND t.amount=l.amount AND t.status='SUCCEEDED' WHERE l.wallet_id=#{walletId} AND l.user_id=#{userId} AND ((t.type='TRANSFER_IN' AND l.direction=1) OR (t.type='TRANSFER_OUT' AND l.direction=-1 AND t.approved_by IS NOT NULL)) AND t.completed_at<#{anchor}")
    Long selectTransferWatermarkBefore(@Param("walletId") Long walletId,@Param("userId") Long userId,@Param("anchor") LocalDateTime anchor);
    /** 原净预存可为负，保留真实统计；资格服务再决定门槛，退款和消费不混入。 */
    @Select("""
        SELECT COALESCE(SUM(CASE WHEN t.type='TRANSFER_IN' THEN t.amount WHEN t.type='TRANSFER_OUT' AND t.approved_by IS NOT NULL THEN -t.amount ELSE 0 END),0)
        FROM course_reserved_transfer t JOIN course_reserved_wallet_ledger l ON l.wallet_id=t.wallet_id AND l.user_id=t.user_id AND l.biz_no=t.biz_no AND l.biz_type=t.type AND l.direction=CASE WHEN t.type='TRANSFER_IN' THEN 1 ELSE -1 END
        WHERE t.wallet_id=#{walletId} AND t.user_id=#{userId} AND t.status='SUCCEEDED'
          AND (#{startLedgerId} IS NULL OR l.id>#{startLedgerId}) AND (#{endLedgerId} IS NULL OR l.id<=#{endLedgerId})
          AND (#{windowStart} IS NULL OR t.completed_at>=#{windowStart}) AND (#{windowEnd} IS NULL OR t.completed_at<#{windowEnd})
        """)
    BigDecimal sumNetDeposit(@Param("walletId") Long walletId, @Param("userId") Long userId, @Param("startLedgerId") Long startLedgerId, @Param("endLedgerId") Long endLedgerId, @Param("windowStart") LocalDateTime windowStart, @Param("windowEnd") LocalDateTime windowEnd);
}
