package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CourseReservedWalletLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 以钱包和业务身份查找流水，配合唯一约束避免重复扣款或退款。 */
@Mapper
public interface CourseReservedWalletLedgerMapper extends BaseMapper<CourseReservedWalletLedger> {
    /** 生命周期累计值来自完整不可变流水，不是当前分页的求和。 */
    @Select("""
            SELECT COALESCE(SUM(CASE WHEN biz_type='TRANSFER_IN' AND direction=1 THEN amount ELSE 0 END),0) AS totalTransferIn,
              COALESCE(SUM(CASE WHEN biz_type='TRANSFER_OUT' AND direction=-1 THEN amount ELSE 0 END),0) AS totalTransferredOut,
              COALESCE(SUM(CASE WHEN biz_type='ORDER' AND direction=-1 THEN amount ELSE 0 END),0) AS totalConsumed,
              COALESCE(SUM(CASE WHEN biz_type='REFUND' AND direction=1 THEN amount ELSE 0 END),0) AS totalRefunded
            FROM course_reserved_wallet_ledger WHERE wallet_id=#{walletId}
            """)
    java.util.Map<String,Object> sumByWallet(@Param("walletId") Long walletId);

    @Select("""
            SELECT * FROM course_reserved_wallet_ledger
            WHERE wallet_id=#{walletId} AND biz_type=#{bizType} AND biz_no=#{bizNo} AND direction=#{direction}
            LIMIT 1 FOR UPDATE
            """)
    CourseReservedWalletLedger selectByBizKey(@Param("walletId") Long walletId,
                                              @Param("bizType") String bizType,
                                              @Param("bizNo") String bizNo,
                                              @Param("direction") int direction);
}
